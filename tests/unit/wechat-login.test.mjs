import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

let moduleSequence = 0;
const completeUser = { id: 18, displayName: '微信就诊人', wechatBound: true, profileCompleted: true,
  phone: null, avatarUrl: null, avatarDisplayUrl: null };
const avatarUser = { ...completeUser, avatarUrl: 'https://bucket.example/avatar.jpg',
  avatarDisplayUrl: 'https://bucket.example/avatar.jpg?signature=view' };
const defaultUser = { ...completeUser, displayName: '微信用户' };

/** 加载实际客户端逻辑，替换构建器专属 env 和 Vue reactive；所有网络/平台操作均为内存桩。 */
async function harness({ token, user, h5 = false, handleRequest, handleUpload, handleLogin } = {}) {
  const storage = new Map();
  if (token) storage.set('dental.token', token);
  if (user) storage.set('dental.user', user);
  const calls = { requests: [], uploads: [], wxLogin: [], navigation: [], privacyResolution: [], privacyChecks: [], modals: [], toasts: [] };
  let privacyRequired = false;
  let privacyListener;
  globalThis.getCurrentPages = () => [{ route: 'pages/home/index', options: {} }];
  globalThis.uni = {
    getStorageSync: key => storage.get(key),
    setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => storage.delete(key),
    login(options) {
      calls.wxLogin.push(options.provider);
      const response = handleLogin?.();
      if (response?.fail) options.fail(response.fail);
      else options.success({ code: `platform-code-${calls.wxLogin.length}` });
    },
    request(options) {
      const path = new URL(options.url).pathname.replace('/api/v1', '');
      const call = { path, data: options.data?.data, header: options.header };
      calls.requests.push(call);
      let response = handleRequest?.(call);
      if (!response) {
        if (path === '/auth/wechat-config') response = { status: 200, data: { enabled: true } };
        else if (path === '/me') response = { status: 200, data: user || completeUser };
        else if (path === '/auth/wechat-login') response = { status: 200, data: { token: 'issued-project-token', user: completeUser } };
        else throw new Error(`Unexpected request: ${path}`);
      }
      Promise.resolve(response).then(result => {
        if (result.fail) options.fail(result.fail);
        else options.success({ statusCode: result.status, data: {
          code: result.code || (result.status === 200 ? 'OK' : 'FAILED'),
          message: result.message || '测试响应', data: result.data
        } });
      });
    },
    uploadFile(options) {
      calls.uploads.push(options);
      const response = handleUpload?.(options) || { status: 200, data: avatarUser };
      Promise.resolve(response).then(result => options.success({ statusCode: result.status, data: JSON.stringify({
        code: result.code || (result.status === 200 ? 'OK' : 'FILE_UPLOAD_FAILED'),
        message: result.message || '头像保存失败', data: result.data
      }) }));
    },
    navigateTo(options) { calls.navigation.push(options.url); options.complete?.(); },
    switchTab(options) { calls.navigation.push(options.url); },
    redirectTo(options) { calls.navigation.push(options.url); },
    showModal(options) { calls.modals.push(options.title); queueMicrotask(() => options.success({ confirm: true })); },
    showToast(options) { calls.toasts.push(options.title); }
  };
  if (h5) delete globalThis.wx;
  else globalThis.wx = {
    getPrivacySetting(options) { calls.privacyChecks.push(true); options.success({ needAuthorization: privacyRequired, privacyContractName: '隐私保护指引' }); },
    onNeedPrivacyAuthorization(callback) { privacyListener = callback; }
  };
  const suffix = `\n// fresh-test-module-${++moduleSequence}`;
  const asUrl = source => `data:text/javascript;base64,${Buffer.from(source + suffix).toString('base64')}`;
  const source = name => readFile(new URL(`../../miniapp/src/utils/${name}.js`, import.meta.url), 'utf8');
  const sessionUrl = asUrl((await source('session')).replace("import { reactive } from 'vue';", 'const reactive = value => value;'));
  const session = await import(sessionUrl);
  const requestUrl = asUrl((await source('request')).replace("'./session'", `'${sessionUrl}'`)
    .replace('import.meta.env.VITE_API_BASE_URL', 'undefined'));
  const request = await import(requestUrl);
  const wechatUrl = asUrl(await source('wechat'));
  const wechat = await import(wechatUrl);
  const authUrl = asUrl((await source('auth')).replace("'./session'", `'${sessionUrl}'`)
    .replace("'./request'", `'${requestUrl}'`).replace("'./wechat'", `'${wechatUrl}'`));
  const auth = await import(authUrl);
  return { auth, session, request, wechat, calls, storage,
    /** 执行实际弹窗事件处理；Vue 桩只提供 ref/computed，挂载与页面导航交由独立端到端验证。 */
    async loadLoginDialog() {
      const component = await readFile(new URL('../../miniapp/src/components/LoginDialog.vue', import.meta.url), 'utf8');
      const script = component.match(/<script setup>([\s\S]*?)<\/script>/)?.[1];
      assert.ok(script, '登录弹窗应包含可验证的事件处理脚本');
      const componentUrl = asUrl(`
        const ref = value => ({ value });
        const computed = getter => ({ get value() { return getter(); } });
        const watch = () => {};
        const defineProps = () => ({ visible: true, modal: true });
        export const events = [];
        const defineEmits = () => (...event) => events.push(event);
        ${script.replace(/import \{[^}]+\} from 'vue';/, '')
          .replace("'../utils/request'", `'${requestUrl}'`)
          .replace("'../utils/session'", `'${sessionUrl}'`)
          .replace("'../utils/auth'", `'${authUrl}'`)
          .replace("'../utils/wechat'", `'${wechatUrl}'`)}
        export { login, retry, close };
      `);
      return import(componentUrl);
    },
    async loadLoginPage() {
      const component = await readFile(new URL('../../miniapp/src/pages/login/index.vue', import.meta.url), 'utf8');
      const script = component.match(/<script setup>([\s\S]*?)<\/script>/)?.[1];
      assert.ok(script, '登录页应包含可验证的返回目标处理');
      return import(asUrl(`
        export const lifecycle = {};
        const onLoad = callback => { lifecycle.onLoad = callback; };
        ${script.replace("import { onLoad } from '@dcloudio/uni-app';", '')
          .replace(/import LoginDialog from '[^']+';/, '')
          .replace("'../../utils/auth'", `'${authUrl}'`)
          .replace("'../../utils/session'", `'${sessionUrl}'`)}
        export { success };
      `));
    },
    requirePrivacy() { privacyRequired = true; },
    authorizePrivacy() { privacyRequired = false; },
    triggerPrivacy() { privacyListener(value => calls.privacyResolution.push(value)); }
  };
}

test('有效真实微信 token 等待一次 /me 验证，个人主体无需手机号', async () => {
  const h = await harness({ token: 'existing-token', user: completeUser });
  const first = h.auth.bootstrapAuth();
  assert.equal(first, h.auth.bootstrapAuth());
  assert.equal(await h.auth.ensureLogin('/pages/profile/index'), true);
  assert.equal(h.calls.requests.filter(call => call.path === '/me').length, 1);
  assert.equal(h.calls.wxLogin.length, 0);
  assert.equal(h.calls.navigation.length, 0);
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
});

test('首次打开只读取能力配置并弹窗，不自动取得微信 code 或创建账号', async () => {
  const h = await harness();
  await h.auth.bootstrapAuth();
  assert.deepEqual(h.calls.requests.map(call => call.path), ['/auth/wechat-config']);
  assert.equal(h.calls.wxLogin.length, 0);
  assert.equal(h.calls.privacyChecks.length, 0);
  assert.equal(h.session.authState.dialogVisible, true);
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.calls.uploads.length, 0);
  h.auth.dismissLoginDialog();
  await h.auth.bootstrapAuth();
  assert.equal(h.session.authState.dialogVisible, false);
  assert.equal(h.calls.wxLogin.length, 0);
});

test('用户关闭登录弹窗继续浏览，不验证微信身份、不保存资料或触发成功事件', async () => {
  const h = await harness();
  await h.auth.bootstrapAuth();
  const dialog = await h.loadLoginDialog();
  dialog.close();
  assert.equal(h.session.authState.dialogVisible, false);
  assert.equal(h.session.hasSession(), false);
  assert.deepEqual(dialog.events, [['close']]);
  assert.deepEqual(h.calls.requests.map(call => call.path), ['/auth/wechat-config']);
  assert.equal(h.calls.wxLogin.length, 0);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, []);
});

test('运行中 token 失效使缓存失效，重新显示登录选项且不自动恢复身份', async () => {
  let meCalls = 0;
  const h = await harness({ token: 'existing-token', user: completeUser, handleRequest(call) {
    if (call.path === '/me') {
      meCalls += 1;
      return meCalls === 1 ? { status: 200, data: completeUser } : { status: 401, code: 'UNAUTHORIZED' };
    }
  } });
  await h.auth.bootstrapAuth();
  await assert.rejects(h.request.api.me({ redirectOn401: false }), error => error.status === 401);
  assert.equal(h.session.authState.needsBootstrap, true);
  assert.equal(await h.auth.ensureLogin('/pages/profile/index'), false);
  assert.equal(h.session.getToken(), '');
  assert.equal(h.calls.wxLogin.length, 0);
  assert.equal(h.calls.requests.some(call => call.path === '/auth/wechat-login'), false);
  assert.equal(h.session.authState.dialogVisible, true);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, ['/pages/login/index?next=%2Fpages%2Fprofile%2Findex']);
});

test('手动退出后普通页面与前台恢复不立即自动登录', async () => {
  const h = await harness({ token: 'existing-token', user: completeUser });
  await h.auth.bootstrapAuth();
  h.session.clearSession();
  await h.auth.resumeAuth();
  await h.auth.bootstrapAuth();
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.calls.wxLogin.length, 0);
});

test('启动时 token 过期显示弹窗，点击按钮后才取得新微信 code 登录', async () => {
  const h = await harness({ token: 'expired-token', user: completeUser, handleRequest(call) {
    if (call.path === '/me') return { status: 401, code: 'UNAUTHORIZED' };
    if (call.path === '/auth/wechat-login') return { status: 200, data: { token: 'renewed-token', user: completeUser } };
  } });
  await h.auth.bootstrapAuth();
  assert.equal(h.session.getToken(), '');
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.session.authState.dialogVisible, true);
  assert.equal(h.calls.navigation.length, 0);
  assert.equal(h.calls.wxLogin.length, 0);
  await h.auth.loginWithWechat();
  assert.equal(h.session.getToken(), 'renewed-token');
  assert.equal(h.session.hasSession(), true);
  assert.deepEqual(h.calls.requests.find(call => call.path === '/auth/wechat-login').data,
    { loginCode: 'platform-code-1', register: true });
});

test('个人主体点击微信登录无需头像或手机号，真实 code 登录成功关闭弹窗', async () => {
  const h = await harness();
  await h.auth.bootstrapAuth();
  const user = await h.auth.loginWithWechat();
  const loginCalls = h.calls.requests.filter(call => call.path === '/auth/wechat-login');
  assert.deepEqual(loginCalls[0].data, { loginCode: 'platform-code-1', register: true });
  assert.equal(h.calls.uploads.length, 0);
  assert.equal(user.avatarUrl, null);
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
});

test('个人中心可选头像上传失败保留完整身份，重试上传不重新登录', async () => {
  let attempts = 0;
  const h = await harness({ handleUpload() {
    attempts += 1;
    return attempts === 1 ? { status: 502, code: 'FILE_UPLOAD_FAILED' } : { status: 200, data: avatarUser };
  } });
  await h.auth.loginWithWechat();
  await assert.rejects(h.auth.uploadAvatar('wxfile://avatar.jpg'), /头像保存失败/);
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.status, 'authenticated');
  assert.equal(h.session.authState.dialogVisible, false);
  assert.equal(h.session.getToken(), 'issued-project-token');
  const user = await h.auth.uploadAvatar('wxfile://avatar.jpg');
  assert.equal(h.session.hasSession(), true);
  assert.equal(user.avatarUrl, avatarUser.avatarUrl);
  assert.equal(h.calls.uploads[0].url.endsWith('/me/avatar'), true);
  assert.equal(h.calls.uploads[0].header.Authorization, 'Bearer issued-project-token');
  assert.equal(h.calls.requests.filter(call => call.path === '/auth/wechat-login' && call.data.register).length, 1);
});

test('公开请求 401 不清会话；静默认证 401 清会话但不导航', async () => {
  const h = await harness({ handleRequest() { return { status: 401, code: 'UNAUTHORIZED' }; } });
  h.session.saveSession({ token: 'valid-token', user: completeUser });
  await assert.rejects(h.request.request('/auth/wechat-login', { auth: false }), error => error.status === 401);
  assert.equal(h.session.getToken(), 'valid-token');
  assert.equal(h.calls.navigation.length, 0);
  await assert.rejects(h.request.request('/me', { redirectOn401: false }), error => error.status === 401);
  assert.equal(h.session.getToken(), '');
  assert.equal(h.calls.navigation.length, 0);
});

test('微信要求隐私同意时，等待原生事件且授权前不调用登录和头像上传', async () => {
  const h = await harness();
  await h.auth.bootstrapAuth();
  h.requirePrivacy();
  await h.auth.refreshPrivacyRequirement();
  h.triggerPrivacy();
  assert.equal(h.calls.privacyResolution.length, 0);
  await assert.rejects(h.auth.loginWithWechat(), /同意微信隐私/);
  assert.equal(h.calls.uploads.length, 0);
  h.wechat.completePrivacyAuthorization('wechat-privacy-agree');
  assert.deepEqual(h.calls.privacyResolution, [{ event: 'agree', buttonId: 'wechat-privacy-agree' }]);
  h.authorizePrivacy();
  await h.auth.refreshPrivacyRequirement();
  assert.equal(h.session.authState.privacyRequired, false);
  await h.auth.loginWithWechat();
  assert.equal(h.session.hasSession(), true);
});

test('H5 不取微信 code，也不调用任何假登录接口', async () => {
  const h = await harness({ h5: true });
  await h.auth.bootstrapAuth();
  assert.equal(h.session.hasSession(), false);
  assert.match(h.session.authState.error, /微信小程序/);
  assert.equal(h.calls.wxLogin.length, 0);
  assert.deepEqual(h.calls.requests.map(call => call.path), ['/auth/wechat-config']);
  await assert.rejects(h.auth.loginWithWechat(), /微信小程序/);
});

test('并发点击微信登录只调用一次平台登录与一次后端登录', async () => {
  const h = await harness();
  await h.auth.bootstrapAuth();
  const [first, duplicate] = await Promise.all([h.auth.loginWithWechat(), h.auth.loginWithWechat()]);
  assert.equal(first.id, completeUser.id);
  assert.equal(duplicate, null);
  assert.equal(h.calls.wxLogin.length, 1);
  assert.equal(h.calls.requests.filter(call => call.path === '/auth/wechat-login').length, 1);
});

test('主动重试使用新的微信 code，不重复使用一次性 code', async () => {
  let attempts = 0;
  const h = await harness({ handleRequest(call) {
    if (call.path === '/auth/wechat-login') {
      attempts += 1;
      if (attempts === 1) return { status: 502, code: 'WECHAT_UNAVAILABLE', message: '微信暂时不可用' };
    }
  } });
  await assert.rejects(h.auth.loginWithWechat(), /微信暂时不可用/);
  assert.equal(h.session.authState.busy, false);
  await h.auth.loginWithWechat();
  assert.deepEqual(h.calls.requests.filter(call => call.path === '/auth/wechat-login').map(call => call.data.loginCode),
    ['platform-code-1', 'platform-code-2']);
});

test('微信平台登录失败保留原始原因，后端不创建账号', async () => {
  const h = await harness({ handleLogin: () => ({ fail: { errMsg: 'login:fail network error', errCode: 1001 } }) });
  await assert.rejects(h.auth.loginWithWechat(), error => error.errMsg === 'login:fail network error' && error.code === 1001);
  assert.equal(h.calls.requests.some(call => call.path === '/auth/wechat-login'), false);
  assert.equal(h.session.authState.busy, false);
});

test('网络失败保留微信 errMsg、code 与请求地址，并说明真机不能使用 localhost', async () => {
  const h = await harness({ handleRequest: () => ({ fail: { errMsg: 'request:fail connection refused', errCode: 7 } }) });
  await assert.rejects(h.request.api.clinic(), error => {
    assert.equal(error.errMsg, 'request:fail connection refused');
    assert.equal(error.code, 7);
    assert.equal(error.url, 'http://127.0.0.1:8080/api/v1/clinic');
    assert.match(error.message, /真机需改为电脑的局域网地址/);
    return true;
  });
});

test('进入后台再返回时仅刷新过期验证间隔的 /me，不自动调用 wx.login', async () => {
  const h = await harness({ token: 'valid-token', user: completeUser });
  const originalNow = Date.now;
  let time = originalNow();
  Date.now = () => time;
  try {
    await h.auth.bootstrapAuth();
    time += 6 * 60 * 1000;
    await h.auth.resumeAuth();
    assert.equal(h.calls.requests.filter(call => call.path === '/me').length, 2);
    assert.equal(h.calls.wxLogin.length, 0);
    assert.equal(h.session.hasSession(), true);
  } finally { Date.now = originalNow; }
});

test('新微信账号默认昵称无头像也立即关闭登录弹窗，并返回原预约确认页', async () => {
  const h = await harness({ handleRequest(call) {
    if (call.path === '/auth/wechat-login') return { status: 200, data: { token: 'new-account-token', user: defaultUser } };
  } });
  await h.auth.bootstrapAuth();
  assert.equal(h.session.authState.dialogVisible, true);
  const target = '/pages/confirm/index?scheduleId=23&doctorId=7';
  const page = await h.loadLoginPage();
  await page.lifecycle.onLoad({ next: encodeURIComponent(target) });
  const dialog = await h.loadLoginDialog();
  await dialog.login();
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.getToken(), 'new-account-token');
  assert.equal(h.session.getUser().displayName, '微信用户');
  assert.equal(h.session.getUser().avatarUrl, null);
  assert.equal(h.session.authState.dialogVisible, false);
  assert.deepEqual(dialog.events, [['success', defaultUser]]);
  assert.equal(h.calls.requests.some(call => call.path === '/me/account-profile'), false);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, []);
  assert.deepEqual(h.calls.modals, []);
  await page.success(dialog.events[0][1]);
  assert.deepEqual(h.calls.navigation, [target]);
});

test('默认昵称无头像的已验证微信账号恢复会话，无需重新登录或设置资料', async () => {
  const h = await harness({ token: 'default-profile-token', user: defaultUser });
  await h.auth.bootstrapAuth();
  assert.equal(await h.auth.ensureLogin('/pages/confirm/index?scheduleId=23'), true);
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
  assert.equal(h.calls.requests.filter(call => call.path === '/me').length, 1);
  assert.equal(h.calls.wxLogin.length, 0);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, []);
});

test('缓存账号未经微信绑定验证时仍清除会话，不因取消头像要求而放行', async () => {
  const unboundUser = { ...defaultUser, wechatBound: false };
  const h = await harness({ token: 'unbound-token', user: unboundUser });
  await h.auth.bootstrapAuth();
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.session.getToken(), '');
  assert.equal(h.session.authState.dialogVisible, true);
  assert.equal(h.calls.wxLogin.length, 0);
});

test('登录响应未通过微信身份校验时弹窗保持打开，不通知页面继续预约', async () => {
  const h = await harness({ handleRequest(call) {
    if (call.path === '/auth/wechat-login') return { status: 200, data: {
      token: 'unverified-token', user: { ...defaultUser, wechatBound: false }
    } };
  } });
  await h.auth.bootstrapAuth();
  const dialog = await h.loadLoginDialog();
  await dialog.login();
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.session.authState.dialogVisible, true);
  assert.match(h.session.authState.error, /微信身份验证/);
  assert.deepEqual(dialog.events, []);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, []);
});

test('后端登录失败保留弹窗且不发成功事件，重试成功后只通知一次', async () => {
  let attempts = 0;
  const h = await harness({ handleRequest(call) {
    if (call.path === '/auth/wechat-login') {
      attempts += 1;
      return attempts === 1
        ? { status: 502, code: 'WECHAT_UNAVAILABLE', message: '微信暂时不可用' }
        : { status: 200, data: { token: 'retry-token', user: defaultUser } };
    }
  } });
  await h.auth.bootstrapAuth();
  const dialog = await h.loadLoginDialog();
  await dialog.login();
  assert.equal(h.session.hasSession(), false);
  assert.equal(h.session.authState.dialogVisible, true);
  assert.equal(h.session.authState.busy, false);
  assert.match(h.session.authState.error, /微信暂时不可用/);
  assert.deepEqual(dialog.events, []);
  await dialog.login();
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
  assert.deepEqual(dialog.events, [['success', defaultUser]]);
  assert.deepEqual(h.calls.requests.filter(call => call.path === '/auth/wechat-login').map(call => call.data.loginCode),
    ['platform-code-1', 'platform-code-2']);
  assert.equal(h.calls.uploads.length, 0);
  assert.deepEqual(h.calls.navigation, []);
});

test('旧请求迟到的401不清除新令牌，也不提示或导航登录', async () => {
  let release;
  const deferred = new Promise(resolve => { release = resolve; });
  const h = await harness({ token: 'old-token', user: completeUser, handleRequest(call) {
    if (call.path === '/appointments/me') return deferred;
  } });
  await h.auth.bootstrapAuth();
  const oldRequest = h.request.api.appointments();
  h.session.saveSession({ token: 'new-token', user: { ...defaultUser, id: 19 } });
  release({ status: 401, code: 'UNAUTHORIZED' });
  await assert.rejects(oldRequest, error => error.status === 401 && error.staleSession === true);
  assert.equal(h.session.getToken(), 'new-token');
  assert.equal(h.session.getUser().id, 19);
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
  assert.deepEqual(h.calls.navigation, []);
  assert.deepEqual(h.calls.toasts, []);
});

test('当前令牌的并发401仍清除会话，但只提示和导航一次', async () => {
  const h = await harness({ token: 'expired-token', user: completeUser, handleRequest(call) {
    if (call.path === '/appointments/me' || call.path === '/me/patient-profile') return { status: 401, code: 'UNAUTHORIZED' };
  } });
  await h.auth.bootstrapAuth();
  const outcomes = await Promise.allSettled([h.request.api.appointments(), h.request.api.profile()]);
  assert.equal(outcomes.every(outcome => outcome.status === 'rejected' && outcome.reason.status === 401), true);
  assert.equal(h.session.getToken(), '');
  assert.equal(h.session.hasSession(), false);
  assert.deepEqual(h.calls.toasts, ['登录已失效，请重新登录']);
  assert.deepEqual(h.calls.navigation, ['/pages/login/index?next=%2Fpages%2Fhome%2Findex']);
});

test('bootstrap的旧me响应无论401或200都不覆盖后建立的有效会话', async () => {
  for (const status of [401, 200]) {
    let release;
    const deferred = new Promise(resolve => { release = resolve; });
    const h = await harness({ token: 'old-token', user: completeUser, handleRequest(call) {
      if (call.path === '/me') return deferred;
    } });
    const bootstrap = h.auth.bootstrapAuth();
    await new Promise(resolve => setImmediate(resolve));
    h.session.saveSession({ token: 'new-token', user: { ...defaultUser, id: 19 } });
    h.auth.dismissLoginDialog();
    release({ status, code: status === 401 ? 'UNAUTHORIZED' : 'OK', data: completeUser });
    await bootstrap;
    assert.equal(h.session.getToken(), 'new-token');
    assert.equal(h.session.getUser().id, 19);
    assert.equal(h.session.hasSession(), true);
    assert.equal(h.session.authState.dialogVisible, false);
    assert.deepEqual(h.calls.navigation, []);
    assert.deepEqual(h.calls.toasts, []);
  }
});

test('旧头像请求迟到的401或成功响应都不改写新会话', async () => {
  for (const status of [401, 200]) {
    let release;
    const deferred = new Promise(resolve => { release = resolve; });
    const h = await harness({ token: 'old-token', user: completeUser, handleUpload: () => deferred });
    await h.auth.bootstrapAuth();
    const upload = h.auth.uploadAvatar('wxfile://old-user.png');
    h.session.saveSession({ token: 'new-token', user: { ...defaultUser, id: 19 } });
    release({ status, code: status === 401 ? 'UNAUTHORIZED' : 'OK', data: avatarUser });
    await assert.rejects(upload, error => error.staleSession === true);
    assert.equal(h.session.getToken(), 'new-token');
    assert.equal(h.session.getUser().id, 19);
    assert.equal(h.session.getUser().avatarUrl, null);
    assert.equal(h.session.hasSession(), true);
    assert.deepEqual(h.calls.navigation, []);
  }
});

test('旧bootstrap配置请求迟到失败不把刚成功登录的新会话降为游客', async () => {
  let configCalls = 0; let releaseConfig; let releaseLogin;
  const configResponse = new Promise(resolve => { releaseConfig = resolve; });
  const loginResponse = new Promise(resolve => { releaseLogin = resolve; });
  const h = await harness({ handleRequest(call) {
    if (call.path === '/auth/wechat-config' && ++configCalls === 2) return configResponse;
    if (call.path === '/auth/wechat-login') return loginResponse;
  } });
  await h.auth.bootstrapAuth();
  const login = h.auth.loginWithWechat();
  await new Promise(resolve => setImmediate(resolve));
  const oldBootstrap = h.auth.bootstrapAuth({ retry: true });
  releaseLogin({ status: 200, data: { token: 'new-login-token', user: defaultUser } });
  await login;
  releaseConfig({ fail: { errMsg: 'request:fail audit network interruption' } });
  await oldBootstrap;
  assert.equal(h.session.getToken(), 'new-login-token');
  assert.equal(h.session.hasSession(), true);
  assert.equal(h.session.authState.dialogVisible, false);
  assert.equal(h.session.authState.error, '');
  assert.deepEqual(h.calls.navigation, []);
});
