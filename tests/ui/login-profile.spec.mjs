import { test, expect } from '@playwright/test';
import { openPreview } from './support/open-preview.mjs';
import { mkdirSync, writeFileSync } from 'node:fs';

// H5 checks use isolated account fixtures; real WeChat identity is covered by the auth tests and device validation.
test.use({ baseURL: process.env.LOGIN_UI_BASE_URL || 'http://127.0.0.1:6185', viewport: { width: 390, height: 844 } });

async function writeComponentPreview(page) {
  const rendered = await page.evaluate(() => {
    const mask = document.querySelector('.editor-mask');
    const css = Array.from(document.styleSheets).flatMap(sheet => {
      try { return Array.from(sheet.cssRules, rule => rule.cssText); } catch { return []; }
    }).join('\n');
    const states = ['default', 'hover', 'focus', 'active', 'disabled', 'loading', 'error', 'success'];
    const panels = states.map(state => {
      const clone = mask.cloneNode(true);
      clone.querySelector('.nickname-field').classList.remove('is-focused');
      const input = clone.querySelector('input');
      input.setAttribute('value', document.querySelector('.editor-panel input').value);
      const save = clone.querySelector('.save-button');
      if (['hover', 'focus', 'active', 'disabled', 'loading', 'success'].includes(state)) save.classList.add(`is-${state}`);
      if (['disabled', 'loading', 'success'].includes(state)) save.setAttribute('disabled', 'true');
      if (state === 'loading') save.textContent = '正在保存…';
      if (state === 'success') save.textContent = '已保存';
      if (state === 'error') {
        const error = document.createElement('uni-view');
        error.className = 'editor-error';
        for (const name of clone.querySelector('.status-slot').getAttributeNames().filter(name => name.startsWith('data-v-'))) error.setAttribute(name, '');
        error.textContent = '资料未保存。请检查网络后重试。';
        clone.querySelector('.status-slot').append(error);
      }
      return `<section><h2>${state}</h2><uni-page-body>${clone.outerHTML}</uni-page-body></section>`;
    }).join('\n');
    return { css, panels, rootStyle: document.documentElement.getAttribute('style') || '' };
  });
  mkdirSync('.local/previews', { recursive: true });
  writeFileSync('.local/previews/AccountProfileEditor.preview.html', `<!doctype html><html lang="zh-CN" style="${rendered.rootStyle}"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>个人资料面板交互状态预览</title><style>${rendered.css}
html,body{overflow:auto!important;background:#edf3ef!important}body{margin:0;padding:24px;font-family:system-ui,sans-serif}h1{font-size:24px;margin:0 0 8px}p{font-size:14px;line-height:1.7;color:#526a5d}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,390px));gap:24px;align-items:start}section{min-width:0}h2{font-size:14px;margin:16px 0 8px;color:#243b34}uni-page-body{display:block;height:auto!important;min-height:0!important}.editor-mask{position:relative!important;inset:auto!important;min-height:510px;align-items:flex-end}.editor-panel{max-height:none!important}.editor-content{max-height:none!important}.save-button.is-hover{background:var(--account-accent-hover);border-color:var(--account-accent-hover)}
</style><h1>个人资料面板交互状态预览</h1><p>由实际组件渲染结果生成的内部检查页。八种状态用于检查视觉反馈；实际保存成功后面板自动关闭。</p><main>${rendered.panels}</main></html>`, 'utf8');
}

async function openAccount(page, saved = {}, { failFirstProfileSave = false } = {}) {
  let user = { id: 18, displayName: '微信用户', wechatBound: true, profileCompleted: true,
    avatarUrl: null, avatarDisplayUrl: null, ...saved };
  const errors = [];
  const updates = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace('/api/v1', '');
    let data;
    if (path === '/auth/wechat-config') data = { enabled: true };
    else if (path === '/me/account-profile' && req.method() === 'PUT') {
      const update = req.postDataJSON().data;
      updates.push(update);
      if (failFirstProfileSave && updates.length === 1) {
        await route.fulfill({ status: 502, contentType: 'application/json',
          headers: { 'access-control-allow-origin': '*' },
          body: JSON.stringify({ code: 'PROFILE_SAVE_FAILED', message: '资料保存失败，请重试', data: null }) });
        return;
      }
      user = { ...user, displayName: update.displayName };
      data = user;
    } else if (path === '/me') data = user;
    else if (path === '/me/patient-profile') data = null;
    else if (path === '/clinic') data = { name: '测试诊所', phone: '02900000000' };
    else data = { records: [], total: 0 };
    await route.fulfill({ status: 200, contentType: 'application/json',
      headers: { 'access-control-allow-origin': '*' }, body: JSON.stringify({ code: 'OK', data }) });
  });
  await openPreview(page, '/#/pages/me/index');
  await expect(page.locator('.profile-name')).toHaveText('点击微信登录');
  await page.evaluate(async fixture => {
    const session = await import('/src/utils/session.js');
    session.saveSession({ token: 'login-profile-fixture-only', user: fixture });
  }, user);
  await expect(page.locator('.profile-name')).toHaveText(user.displayName);
  return { errors, updates };
}

test('new account shows default identity without opening or requiring the profile editor', async ({ page }) => {
  const { errors, updates } = await openAccount(page);
  await expect(page.getByText('完善账号资料（选填）', { exact: true })).toBeVisible();
  await expect(page.locator('.profile-card .default-avatar')).toBeVisible();
  await expect(page.locator('.editor-mask')).toHaveCount(0);
  await expect(page.getByText('退出登录', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  mkdirSync('.local/screenshots', { recursive: true });
  await page.screenshot({ path: '.local/screenshots/login-default-profile.png', fullPage: true });
  expect(updates).toEqual([]);
  expect(errors).toEqual([]);
});

test('one save updates the nickname and closes the editor without requiring an avatar', async ({ page }) => {
  const { errors, updates } = await openAccount(page);
  await page.locator('.profile-card').click();
  const editor = page.locator('.editor-panel');
  await expect(editor).toBeVisible();
  await expect(editor.locator('.avatar-button')).toHaveCount(1);
  await expect(page.locator('.avatar-mask')).toHaveCount(0);
  const panelBox = await editor.boundingBox();
  expect(panelBox.x).toBeGreaterThanOrEqual(0);
  expect(panelBox.x + panelBox.width).toBeLessThanOrEqual(390);
  await editor.locator('input').fill('自选昵称');
  mkdirSync('.local/screenshots', { recursive: true });
  await page.screenshot({ path: '.local/screenshots/account-profile-editor.png' });
  await writeComponentPreview(page);
  await editor.getByText('保存资料', { exact: true }).click();
  await expect(page.locator('.profile-name')).toHaveText('自选昵称');
  await expect(editor).toHaveCount(0);
  await expect(page.locator('.profile-card .default-avatar')).toBeVisible();
  expect(updates).toEqual([{ displayName: '自选昵称' }]);
  expect(errors).toEqual([]);
});

test('closing the editor discards nickname changes and reopening restores the saved name', async ({ page }) => {
  const { errors, updates } = await openAccount(page, { displayName: '已保存昵称' });
  await page.locator('.profile-card').click();
  const editor = page.locator('.editor-panel');
  await editor.locator('input').fill('未保存修改');
  await editor.locator('.close-button').click();
  await expect(editor).toHaveCount(0);
  await expect(page.locator('.profile-name')).toHaveText('已保存昵称');
  await page.locator('.profile-card').click();
  await expect(editor.locator('input')).toHaveValue('已保存昵称');
  await expect(editor.locator('.editor-error')).toHaveCount(0);
  await editor.locator('.close-button').click();
  expect(updates).toEqual([]);
  expect(errors).toEqual([]);
});

test('a failed save keeps the draft open for retry and closes only after success', async ({ page }) => {
  const { errors, updates } = await openAccount(page, { displayName: '旧昵称' }, { failFirstProfileSave: true });
  await page.locator('.profile-card').click();
  const editor = page.locator('.editor-panel');
  await editor.locator('input').fill('重试后保存');
  await editor.getByText('保存资料', { exact: true }).click();
  await expect(editor.locator('.editor-error')).toContainText('资料保存失败，请重试');
  await expect(editor.locator('input')).toHaveValue('重试后保存');
  await expect(page.locator('.profile-name')).toHaveText('旧昵称');
  await editor.getByText('保存资料', { exact: true }).click();
  await expect(editor).toHaveCount(0);
  await expect(page.locator('.profile-name')).toHaveText('重试后保存');
  expect(updates).toEqual([{ displayName: '重试后保存' }, { displayName: '重试后保存' }]);
  expect(errors).toEqual([]);
});

test('returning account keeps saved nickname and avatar without an automatic editor', async ({ page }) => {
  const avatar = 'https://avatar.example.test/saved.png';
  await page.route(avatar, route => route.fulfill({ contentType: 'image/png',
    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==', 'base64') }));
  const { errors, updates } = await openAccount(page, { displayName: '已保存的昵称', avatarUrl: avatar, avatarDisplayUrl: avatar });
  await expect(page.locator('.profile-card .avatar-image')).toBeVisible();
  await expect(page.locator('.profile-card .default-avatar')).toHaveCount(0);
  await expect(page.locator('.account-hint')).toHaveCount(0);
  await expect(page.locator('.editor-mask')).toHaveCount(0);
  expect(updates).toEqual([]);
  expect(errors).toEqual([]);
});

test('profile editor keeps its fields and save action usable across phone widths', async ({ page }) => {
  const { errors } = await openAccount(page);
  for (const viewport of [{ width: 320, height: 568 }, { width: 375, height: 812 },
    { width: 414, height: 896 }, { width: 768, height: 1024 }]) {
    await page.setViewportSize(viewport);
    await page.locator('.profile-card').click();
    const editor = page.locator('.editor-panel');
    await expect(editor).toBeVisible();
    const box = await editor.boundingBox();
    const button = await editor.locator('.save-button').boundingBox();
    const close = await editor.locator('.close-button').boundingBox();
    expect(box.x).toBeGreaterThanOrEqual(-1);
    expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1);
    expect(button.y + button.height).toBeLessThanOrEqual(viewport.height + 1);
    expect(button.height).toBeGreaterThanOrEqual(44);
    expect(close.height).toBeGreaterThanOrEqual(44);
    expect(close.width).toBeGreaterThanOrEqual(44);
    await editor.locator('input').fill('手机端昵称');
    await expect(editor.locator('.save-button')).not.toHaveAttribute('disabled');
    await page.screenshot({ path: `.local/screenshots/account-profile-editor-${viewport.width}.png` });
    await editor.locator('.close-button').click();
    await expect(editor).toHaveCount(0);
  }
  expect(errors).toEqual([]);
});
