import { test as base, expect } from '@playwright/test';
import { openPreview } from './support/open-preview.mjs';
import { mkdirSync } from 'node:fs';

// A fresh Edge process per case releases cold Vite module resources between cases.
// The built-in browser fixture is worker-scoped and cannot change scope when overridden.
const test = base.extend({
  freshBrowser: [async ({ playwright, browserName, launchOptions }, use) => {
    const browser = await playwright[browserName].launch({ ...launchOptions, channel: 'msedge', headless: true });
    try { await use(browser); }
    finally { await browser.close(); }
  }, { scope: 'test' }],
  context: async ({ freshBrowser, _combinedContextOptions }, use) => {
    const context = await freshBrowser.newContext(_combinedContextOptions);
    try { await use(context); }
    finally { await context.close(); }
  },
});

// 独立 H5 预览与虚构接口夹具；不调用真实微信、付费模型或业务数据库。
test.use({ baseURL: process.env.AI_UI_BASE_URL || 'http://127.0.0.1:6184', viewport: { width: 375, height: 812 } });

async function prepare(page, authenticated = true, { history = [], failMessageIdOnce = null, failRefreshAfterDelete = false } = {}) {
  const saved = [];
  const errors = [];
  let conversations = history.map(item => ({ ...item }));
  let failedMessage = false; let failedRefresh = false; let deleted = false;
  const reads = []; const listPages = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace('/api/v1', '');
    let data;
    if (path === '/ai/config') data = { enabled: true, maxMessageLength: 300 };
    else if (path === '/auth/wechat-config') data = { enabled: true };
    else if (path === '/ai/conversations' && request.method() === 'GET') {
      const currentPage = Number(new URL(request.url()).searchParams.get('page') || 1);
      listPages.push(currentPage);
      if (deleted && failRefreshAfterDelete && !failedRefresh) {
        failedRefresh = true;
        await route.fulfill({ status: 502, contentType: 'application/json',
          body: JSON.stringify({ code: 'UNAVAILABLE', message: '历史列表暂不可用', data: null }) });
        return;
      }
      data = { records: conversations.slice((currentPage - 1) * 20, currentPage * 20), total: conversations.length };
    }
    else if (path === '/ai/conversations' && request.method() === 'POST') {
      data = { id: 8, title: '新会话' };
      conversations.unshift(data);
    }
    else if (path === '/ai/conversations/8/messages' && request.method() === 'POST') {
      const body = request.postDataJSON().data;
      const answer = body.content.includes('牙疼') ? { content: '请补充疼痛持续多久，以及是否有肿胀。具体原因需要口腔医生检查。',
        topic: 'SYMPTOM', guidance: 'GENERAL', candidates: [], references: [{ title: 'NHS：牙痛与就诊提示', url: 'https://www.nhs.uk/symptoms/toothache/' }] }
        : body.content.includes('有哪些医生') ? { content: '以下是当前公开的医生和可预约时段。', topic: 'DOCTOR', guidance: 'GENERAL',
          candidates: [{ doctorId: 101, doctorName: '测试医生甲', departmentName: '牙体牙髓科', title: '医师', specialty: '牙痛检查',
            schedules: [{ id: 88, doctorId: 101, startTime: '2026-10-10T14:00:00+08:00', endTime: '2026-10-10T15:00:00+08:00', remainingSlots: 2 }] }], references: [] }
        : { content: '诊所地址：测试街道1号\n营业时间：9:00—18:00', topic: 'CLINIC', guidance: 'GENERAL', candidates: [],
          references: [{ title: '依据：诊所当前公开配置与已发布公告', url: '' }] };
      const userMessage = { id: saved.length + 1, role: 'USER', requestId: body.requestId, content: body.content,
        createdAt: '2026-10-09T10:00:00+08:00' };
      const assistantMessage = { id: saved.length + 2, role: 'ASSISTANT', requestId: body.requestId,
        content: answer.content, answer, createdAt: '2026-10-09T10:00:01+08:00' };
      saved.push(userMessage, assistantMessage);
      data = { conversation: { id: 8, title: '诊所咨询' }, userMessage, assistantMessage };
    } else if (/^\/ai\/conversations\/\d+\/messages$/.test(path)) {
      const id = Number(path.split('/')[3]);
      reads.push(id);
      if (id === failMessageIdOnce && !failedMessage) {
        failedMessage = true;
        await route.fulfill({ status: 502, contentType: 'application/json',
          body: JSON.stringify({ code: 'UNAVAILABLE', message: '历史消息读取失败', data: null }) });
        return;
      }
      const messages = history.length ? [{ id: id * 10, role: 'ASSISTANT', content: `历史消息${id}`,
        createdAt: '2026-10-09T10:00:00+08:00' }] : saved;
      data = { records: messages, hasMore: false, nextBeforeId: messages[0]?.id };
    }
    else if (/^\/ai\/conversations\/\d+$/.test(path) && request.method() === 'DELETE') {
      const id = Number(path.split('/')[3]);
      conversations = conversations.filter(item => item.id !== id); deleted = true; data = null;
    }
    else if (path === '/schedules/88') data = { id: 88, doctorId: 101, remainingSlots: 0 };
    else if (path === '/me') data = { id: 18, displayName: '虚构测试用户', wechatBound: true, profileCompleted: true };
    else data = { records: [], total: 0 };
    await route.fulfill({ status: 200, contentType: 'application/json', headers: { 'access-control-allow-origin': '*' },
      body: JSON.stringify({ code: 'OK', message: 'success', data }) });
  });
  await openPreview(page, '/#/pages/ai/index', page.getByText('登录后开始交流', { exact: true }));
  // H5 compiles on demand; allow the initial application mount to finish on a cold preview.
  await expect(page.getByText('登录后开始交流', { exact: true })).toBeVisible({ timeout: 15000 });
  if (authenticated) {
    // 只在浏览器测试内给现有会话模块注入虚构状态，产品的微信登录逻辑未改动。
    await page.evaluate(async () => {
      const auth = await import('/src/utils/auth.js');
      await auth.bootstrapAuth();
      const session = await import('/src/utils/session.js');
      session.saveSession({ token: 'ui-fixture-only', user: { id: 18, displayName: '虚构测试用户', wechatBound: true, profileCompleted: true } });
    });
    if (history.length) {
      await page.locator('uni-tabbar').getByText('首页', { exact: true }).click();
      await page.locator('uni-tabbar').getByText('AI 助手', { exact: true }).click();
      await expect(page.locator('.message-text').last()).toHaveText(`历史消息${history[0].id}`);
    } else await expect(page.getByText('你好，今天想了解什么？', { exact: true })).toBeVisible();
  }
  return { errors, reads, listPages };
}

test('AI tab supports chatting, history deletion and mobile layout without script errors', async ({ page }) => {
  const { errors } = await prepare(page);
  await expect(page.locator('uni-tabbar').getByText('AI 助手', { exact: true })).toBeVisible();
  await page.locator('textarea').fill('诊所在哪里，什么时候营业？');
  await page.getByText('发送', { exact: true }).click();
  await expect(page.locator('.message-text').last()).toContainText('诊所地址：测试街道1号');
  await expect(page.locator('.message-text').last()).toContainText('营业时间：9:00—18:00');
  await expect(page.locator('textarea')).toHaveValue('');
  const composer = await page.locator('.chat-composer').boundingBox();
  const tabbar = await page.locator('uni-tabbar').boundingBox();
  expect(composer.y + composer.height).toBeLessThanOrEqual(tabbar.y + 2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(375);
  mkdirSync('.local/screenshots', { recursive: true });
  await page.screenshot({ path: '.local/screenshots/ai-chat.png' });
  await page.getByText('历史', { exact: true }).click();
  await page.getByText('删除', { exact: true }).click();
  await page.locator('.uni-modal__btn_primary').click();
  await expect(page.getByText('暂无聊天记录', { exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('symptom replies show sourced guidance and keep diagnosis scope visible', async ({ page }) => {
  const { errors } = await prepare(page);
  await page.locator('textarea').fill('我牙疼，应该怎么安排就诊？');
  await page.getByText('发送', { exact: true }).click();
  await expect(page.getByText('NHS：牙痛与就诊提示', { exact: true })).toBeVisible();
  await expect(page.getByText('复制来源链接', { exact: true })).toBeVisible();
  await expect(page.getByText('就诊提示供参考，诊断与治疗需由医生检查后确定。紧急情况请直接就医。', { exact: true })).toBeVisible();
  expect(errors).toEqual([]);
});

test('doctor cards recheck current availability before opening appointment confirmation', async ({ page }) => {
  const { errors } = await prepare(page);
  await page.locator('textarea').fill('有哪些医生？');
  await page.getByText('发送', { exact: true }).click();
  await expect(page.getByText('测试医生甲', { exact: true })).toBeVisible();
  await page.getByText('选择', { exact: true }).click();
  await expect(page.getByText('该时段已约满，请重新查询号源', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/pages\/ai\/index/);
  expect(errors).toEqual([]);
});

test('history retry reloads the selected conversation after a failed message request', async ({ page }) => {
  const { errors, reads } = await prepare(page, true, {
    history: [{ id: 1, title: '会话1' }, { id: 2, title: '会话2' }], failMessageIdOnce: 2,
  });
  await page.getByText('历史', { exact: true }).click();
  await page.getByText('会话2', { exact: true }).click();
  await expect(page.locator('.chat-error')).toContainText('历史消息读取失败');
  await page.locator('.retry-chat').click();
  await expect(page.locator('.message-text').last()).toHaveText('历史消息2');
  expect(reads).toEqual([1, 2, 2]);
  expect(errors).toEqual([]);
});

for (const failRefreshAfterDelete of [false, true]) {
  test(`deleting history preserves all remaining pages${failRefreshAfterDelete ? ' after refresh retry' : ''}`, async ({ page }) => {
    const history = Array.from({ length: 40 }, (_, index) => ({ id: index + 1, title: `会话${index + 1}` }));
    const { errors } = await prepare(page, true, { history, failRefreshAfterDelete });
    await page.getByText('历史', { exact: true }).click();
    await expect(page.locator('uni-tabbar')).toBeHidden();
    const row = page.locator('.history-row').filter({ has: page.getByText('会话2', { exact: true }) });
    await row.getByText('删除', { exact: true }).click();
    await page.locator('.uni-modal__btn_primary').click();
    await expect(page.locator('.history-name').filter({ hasText: /^会话2$/ })).toHaveCount(0);
    if (failRefreshAfterDelete) {
      await expect(page.locator('.history-error')).toContainText('历史列表刷新失败');
      await page.locator('.retry-history').click();
      await expect(page.locator('.history-error')).toHaveCount(0);
    }
    await page.getByText('查看更多会话', { exact: true }).click();
    await expect(page.locator('.history-row')).toHaveCount(39);
    await expect(page.getByText('会话21', { exact: true })).toHaveCount(1);
    await expect(page.getByText('查看更多会话', { exact: true })).toHaveCount(0);
    await page.locator('.history-heading').getByText('关闭', { exact: true }).click();
    await expect(page.locator('.history-mask')).toHaveCount(0);
    await expect(page.locator('uni-tabbar')).toBeVisible();
    expect(errors).toEqual([]);
  });
}
