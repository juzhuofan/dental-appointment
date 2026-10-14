import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

let moduleSequence = 0;
const defaultUser = { id: 18, displayName: '微信用户', wechatBound: true, profileCompleted: true,
  avatarUrl: null, avatarDisplayUrl: null };

/** 加载实际编辑器脚本与会话模块；仅替换 Vue 宏、微信能力及 HTTP/上传边界。 */
async function harness({ user = defaultUser, handleSave, handleUpload, privacyRequired = false,
  privacyError = '', wechatSupported = true } = {}) {
  const storage = new Map();
  const calls = { updates: [], uploads: [], order: [], privacy: [], toasts: [], events: [], tabBar: [] };
  globalThis.uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key),
    showToast: options => calls.toasts.push(options.title),
    hideTabBar: () => calls.tabBar.push('hide'),
    showTabBar: () => calls.tabBar.push('show'),
  };
  const suffix = `\n// account-profile-test-${++moduleSequence}`;
  const asUrl = source => `data:text/javascript;base64,${Buffer.from(source + suffix).toString('base64')}`;
  const sessionSource = await readFile(new URL('../../miniapp/src/utils/session.js', import.meta.url), 'utf8');
  const sessionUrl = asUrl(sessionSource.replace("import { reactive } from 'vue';", 'const reactive = value => value;'));
  const session = await import(sessionUrl);
  session.saveSession({ token: 'account-profile-fixture-only', user: { ...user } });
  const runtime = {
    props: { visible: false }, watchers: [], pending: [], unmountCallbacks: [], session, calls,
    privacyRequired, privacyError, wechatSupported,
    async saveProfile(input) {
      calls.updates.push(input);
      calls.order.push('nickname');
      return await handleSave?.(input, session.getUser()) || { ...session.getUser(), ...input };
    },
    async uploadAvatar(path) {
      calls.uploads.push(path);
      calls.order.push('avatar');
      const result = await handleUpload?.(path, session.getUser()) || { ...session.getUser(),
        avatarUrl: 'https://avatar.example.test/selected.png',
        avatarDisplayUrl: 'https://avatar.example.test/selected.png?signature=view' };
      session.saveUser(result);
      return result;
    },
    async refreshPrivacy() {
      calls.privacy.push('check');
      if (runtime.privacyError) throw new Error(runtime.privacyError);
      session.authState.privacyRequired = runtime.privacyRequired;
      return { needAuthorization: runtime.privacyRequired };
    },
  };
  const runtimeKey = `__accountProfileTest${moduleSequence}`;
  globalThis[runtimeKey] = runtime;
  const boundaryUrl = asUrl(`
    const runtime = globalThis[${JSON.stringify(runtimeKey)}];
    export const api = { saveAccountProfile: input => runtime.saveProfile(input) };
    export const uploadAvatar = path => runtime.uploadAvatar(path);
    export const refreshPrivacyRequirement = () => runtime.refreshPrivacy();
    export const isWechatMiniProgram = () => runtime.wechatSupported;
    export const completePrivacyAuthorization = id => {
      runtime.calls.privacy.push(id);
      runtime.privacyRequired = false;
    };
    export const openPrivacyContract = () => {};
  `);
  const component = await readFile(new URL('../../miniapp/src/components/AccountProfileEditor.vue', import.meta.url), 'utf8');
  const script = component.match(/<script setup>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script, '资料编辑器应包含可验证的事件处理脚本');
  const editor = await import(asUrl(`
    const runtime = globalThis[${JSON.stringify(runtimeKey)}];
    const ref = value => ({ value });
    const computed = getter => ({ get value() { return getter(); } });
    const defineProps = () => runtime.props;
    const defineEmits = () => (...event) => runtime.calls.events.push(event);
    const onUnmounted = callback => runtime.unmountCallbacks.push(callback);
    const watch = (source, callback, options) => {
      runtime.watchers.push({ source, callback });
      if (options?.immediate) runtime.pending.push(Promise.resolve(callback(source())));
    };
    ${script.replace(/import \{[^}]+\} from 'vue';/, '')
      .replace(/import \w+ from '[^']+\.vue';/g, '')
      .replace("'../utils/request'", `'${boundaryUrl}'`)
      .replace("'../utils/session'", `'${sessionUrl}'`)
      .replace("'../utils/auth'", `'${boundaryUrl}'`)
      .replace("'../utils/wechat'", `'${boundaryUrl}'`)}
    export { displayName, avatarPath, saving, error, baselineName, saved,
      handleChooseAvatar, syncDisplayName, blurNickname, confirmNickname, keyboardChanged, keyboardHeight, save, close };
  `));
  delete globalThis[runtimeKey];
  await Promise.all(runtime.pending);
  async function setVisible(visible) {
    runtime.props.visible = visible;
    await Promise.all(runtime.watchers.map(watch => watch.callback(watch.source())));
  }
  await setVisible(true);
  return { editor, session, calls, runtime, setVisible,
    unmount() { runtime.unmountCallbacks.forEach(callback => callback()); } };
}

test('头像选择只更新预览，修改昵称与头像后一次保存并关闭', async () => {
  const h = await harness();
  h.editor.syncDisplayName({ detail: { value: '新的展示昵称' } });
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://selected.png' } });
  assert.equal(h.editor.avatarPath.value, 'wxfile://selected.png');
  assert.deepEqual(h.calls.updates, []);
  assert.deepEqual(h.calls.uploads, []);
  assert.equal(h.session.getUser().displayName, '微信用户');
  assert.equal(h.session.getUser().avatarUrl, null);
  await h.editor.save();
  assert.deepEqual(h.calls.updates, [{ displayName: '新的展示昵称' }]);
  assert.deepEqual(h.calls.uploads, ['wxfile://selected.png']);
  assert.deepEqual(h.calls.order, ['nickname', 'avatar']);
  assert.equal(h.session.getUser().displayName, '新的展示昵称');
  assert.equal(h.session.getUser().avatarUrl, 'https://avatar.example.test/selected.png');
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('取消不保存昵称或头像，重新打开恢复已保存资料', async () => {
  const user = { ...defaultUser, displayName: '原来的昵称' };
  const h = await harness({ user });
  h.editor.displayName.value = '未保存昵称';
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://discarded.png' } });
  h.editor.close();
  assert.deepEqual(h.calls.updates, []);
  assert.deepEqual(h.calls.uploads, []);
  assert.equal(h.session.getUser().displayName, '原来的昵称');
  assert.equal(h.session.getUser().avatarUrl, null);
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
  await h.setVisible(false);
  await h.setVisible(true);
  assert.equal(h.editor.displayName.value, '原来的昵称');
  assert.equal(h.editor.avatarPath.value, '');
  assert.equal(h.editor.error.value, '');
});

test('仅选择头像且昵称留空时保留原昵称，无需重复保存昵称', async () => {
  const h = await harness({ user: { ...defaultUser, displayName: '原来的昵称' } });
  h.editor.displayName.value = '';
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://avatar-only.png' } });
  await h.editor.save();
  assert.deepEqual(h.calls.updates, []);
  assert.deepEqual(h.calls.uploads, ['wxfile://avatar-only.png']);
  assert.equal(h.session.getUser().displayName, '原来的昵称');
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('新账号未修改资料时不提交空昵称，用户仍可直接关闭', async () => {
  const h = await harness();
  assert.equal(h.editor.displayName.value, '');
  await h.editor.save();
  assert.deepEqual(h.calls.updates, []);
  assert.deepEqual(h.calls.uploads, []);
  assert.equal(h.session.getUser().displayName, '微信用户');
  assert.equal(h.session.getUser().avatarUrl, null);
  assert.deepEqual(h.calls.events, []);
  h.editor.close();
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('保存昵称失败时保留完整草稿，重试成功后继续保存头像', async () => {
  let attempts = 0;
  const h = await harness({ handleSave(input, user) {
    attempts += 1;
    if (attempts === 1) throw new Error('昵称保存失败');
    return { ...user, ...input };
  } });
  h.editor.displayName.value = '未丢失的昵称';
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://retry.png' } });
  await h.editor.save();
  assert.match(h.editor.error.value, /昵称保存失败/);
  assert.equal(h.editor.displayName.value, '未丢失的昵称');
  assert.equal(h.editor.avatarPath.value, 'wxfile://retry.png');
  assert.equal(h.session.getUser().displayName, '微信用户');
  assert.deepEqual(h.calls.uploads, []);
  assert.deepEqual(h.calls.events, []);
  await h.editor.save();
  assert.equal(h.session.getUser().displayName, '未丢失的昵称');
  assert.deepEqual(h.calls.uploads, ['wxfile://retry.png']);
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('昵称已保存而头像失败时保留头像草稿，重试不重复更新昵称', async () => {
  let attempts = 0;
  const h = await harness({ handleUpload() {
    attempts += 1;
    if (attempts === 1) throw new Error('头像上传失败');
  } });
  h.editor.displayName.value = '已保存昵称';
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://avatar-retry.png' } });
  await h.editor.save();
  assert.match(h.editor.error.value, /头像上传失败/);
  assert.equal(h.session.getUser().displayName, '已保存昵称');
  assert.equal(h.session.getUser().avatarUrl, null);
  assert.equal(h.editor.avatarPath.value, 'wxfile://avatar-retry.png');
  assert.deepEqual(h.calls.events, []);
  await h.editor.save();
  assert.deepEqual(h.calls.updates, [{ displayName: '已保存昵称' }]);
  assert.deepEqual(h.calls.uploads, ['wxfile://avatar-retry.png', 'wxfile://avatar-retry.png']);
  assert.equal(h.session.getUser().displayName, '已保存昵称');
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('保存过程中重复点击或取消不会并发提交和提前关闭', async () => {
  let resolveSave;
  const pending = new Promise(resolve => { resolveSave = resolve; });
  const h = await harness({ handleSave: () => pending });
  h.editor.displayName.value = '正在保存的昵称';
  const first = h.editor.save();
  assert.equal(h.editor.saving.value, true);
  await h.editor.save();
  h.editor.close();
  assert.deepEqual(h.calls.updates, [{ displayName: '正在保存的昵称' }]);
  assert.deepEqual(h.calls.events, []);
  resolveSave({ ...defaultUser, displayName: '正在保存的昵称' });
  await first;
  assert.equal(h.editor.saving.value, false);
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('头像隐私能力检查失败不阻止用户单独保存昵称', async () => {
  const h = await harness({ privacyError: '无法读取微信隐私授权状态' });
  h.editor.displayName.value = '昵称仍可修改';
  await h.editor.handleChooseAvatar({ detail: { avatarUrl: 'wxfile://blocked.png' } });
  assert.equal(h.editor.avatarPath.value, '');
  await h.editor.save();
  assert.deepEqual(h.calls.updates, [{ displayName: '昵称仍可修改' }]);
  assert.deepEqual(h.calls.uploads, []);
  assert.equal(h.session.getUser().displayName, '昵称仍可修改');
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('键盘确认使用事件中的最新昵称保存，失焦不提前移动面板', async () => {
  let resolveUpdate;
  const response = new Promise(resolve => { resolveUpdate = resolve; });
  const h = await harness({ handleSave: () => response });
  h.editor.keyboardChanged({ detail: { height: 280 } });
  h.editor.blurNickname({ detail: { value: '失焦同步昵称' } });
  assert.equal(h.editor.displayName.value, '失焦同步昵称');
  assert.equal(h.editor.keyboardHeight.value, 280);
  h.editor.confirmNickname({ detail: { value: '确认键最新昵称' } });
  assert.deepEqual(h.calls.updates, [{ displayName: '确认键最新昵称' }]);
  resolveUpdate({ ...defaultUser, displayName: '确认键最新昵称' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.session.getUser().displayName, '确认键最新昵称');
  assert.equal(h.editor.keyboardHeight.value, 0);
  assert.equal(h.calls.events.filter(event => event[0] === 'close').length, 1);
});

test('面板打开隐藏原生 TabBar，关闭或卸载时恢复且不重复恢复', async () => {
  const h = await harness();
  assert.deepEqual(h.calls.tabBar, ['hide']);
  h.editor.close();
  await h.setVisible(false);
  assert.deepEqual(h.calls.tabBar, ['hide', 'show']);
  await h.setVisible(true);
  assert.deepEqual(h.calls.tabBar, ['hide', 'show', 'hide']);
  h.unmount();
  assert.deepEqual(h.calls.tabBar, ['hide', 'show', 'hide', 'show']);
  h.unmount();
  assert.deepEqual(h.calls.tabBar, ['hide', 'show', 'hide', 'show']);
});
