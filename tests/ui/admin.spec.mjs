import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
const env = Object.fromEntries(readFileSync(new URL('../../.env.local', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => /^[A-Z_][A-Z0-9_]*=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));

async function login(page, role = 'ADMIN') {
  await page.goto('/login');
  const username = role === 'ADMIN' ? env.DEMO_ADMIN_USERNAME || 'admin' : env.DEMO_DOCTOR_USERNAME || 'doctor';
  const password = role === 'ADMIN' ? env.DEMO_ADMIN_PASSWORD : env.DEMO_DOCTOR_PASSWORD;
  if (!password) throw new Error(`本地配置缺少 ${role} 演示账号密码`);
  await page.getByPlaceholder('请输入账号', { exact: true }).fill(username);
  await page.getByPlaceholder('请输入密码', { exact: true }).fill(password);
  await page.locator('button[type="submit"]').click();
  await expect(page).toHaveURL(/dashboard/);
}

async function authenticatedRequest(page, method, path, data) {
  const token = await page.evaluate(() => localStorage.getItem('dental.admin.token'));
  if (!token) throw new Error('验证请求需要已登录的管理端会话');
  return page.request.fetch(`/api/v1${path}`, {
    method,
    headers: { Authorization: `Bearer ${token}` },
    data: data === undefined ? undefined : { data },
  });
}

function writeResponse(page, method, suffix) {
  return page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname === `/api/v1${suffix}` && response.request().method() === method;
  });
}

test('管理员登录及全部业务页面能加载，页面无脚本异常', async ({ page }) => {
  const scriptErrors = [];
  page.on('pageerror', error => scriptErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') scriptErrors.push(`${message.text()} (${message.location().url})`); });
  const dashboardResponse = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/admin/dashboard');
  await login(page);
  expect((await dashboardResponse).status()).toBe(200);
  await expect(page.locator('.el-loading-mask')).toHaveCount(0);
  await expect(page.getByText('科室管理', { exact: true }).first()).toBeVisible();
  await page.screenshot({ path: '.local/screenshots/admin-dashboard.png', fullPage: true });
  const routes = [
    ['/departments', '科室管理'], ['/doctors', '医生档案'], ['/schedules', '医生排班'],
    ['/appointments', '预约管理'], ['/notices', '诊所公告'], ['/users', '账号管理'],
    ['/patients', '患者资料'], ['/logs', '操作日志'], ['/settings', '诊所设置'], ['/password', '修改密码'],
  ];
  for (const [route, title] of routes) {
    await page.goto(route);
    await expect(page.getByRole('heading', { name: title, exact: true }).first()).toBeVisible();
    await expect(page).not.toHaveURL(/login/);
    await expect(page.locator('.el-loading-mask')).toHaveCount(0);
    await expect(page.locator('main .el-alert--error')).toHaveCount(0);
  }
  expect(scriptErrors).toEqual([]);
});

test('管理员能够新增、编辑、逻辑删除科室，删除后列表不再显示', async ({ page }) => {
  const identifier = `UI科室${Date.now()}${Math.random().toString(36).slice(2, 7)}`;
  const updatedName = `${identifier}修订`;
  let departmentId;
  let deleted = false;
  await login(page);
  try {
    await page.goto('/departments');
    await page.getByRole('button', { name: '新增科室', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByPlaceholder('请输入科室名称', { exact: true }).fill(identifier);
    await dialog.getByPlaceholder('请输入科室介绍', { exact: true }).fill('自动验证使用的独立科室，验证完成后逻辑删除。');
    const createResponse = writeResponse(page, 'POST', '/admin/departments');
    await dialog.getByRole('button', { name: '保存科室', exact: true }).click();
    const created = await createResponse;
    expect(created.status()).toBeLessThan(300);
    const envelope = await created.json();
    expect(envelope.code).toBe('OK');
    departmentId = typeof envelope.data?.id === 'number' ? envelope.data.id : undefined;
    await expect(dialog).not.toBeVisible();

    const search = page.getByPlaceholder('搜索科室名称或关键词', { exact: true });
    await search.fill(identifier);
    await page.getByRole('button', { name: '查询', exact: true }).click();
    let row = page.locator('.el-table__body tr').filter({ hasText: identifier });
    await expect(row).toHaveCount(1);
    await expect(row.getByText(identifier, { exact: true })).toBeVisible();

    if (!departmentId) {
      const response = await authenticatedRequest(page, 'GET', `/admin/departments?keyword=${encodeURIComponent(identifier)}&size=100`);
      const result = await response.json();
      departmentId = result.data.records.find(record => record.name === identifier)?.id;
      expect(typeof departmentId).toBe('number');
    }

    await row.getByRole('button', { name: '编辑', exact: true }).click();
    await dialog.getByPlaceholder('请输入科室名称', { exact: true }).fill(updatedName);
    const editResponse = writeResponse(page, 'PUT', `/admin/departments/${departmentId}`);
    await dialog.getByRole('button', { name: '保存科室', exact: true }).click();
    const edited = await editResponse;
    expect(edited.status()).toBeLessThan(300);
    expect((await edited.json()).code).toBe('OK');
    await expect(dialog).not.toBeVisible();
    row = page.locator('.el-table__body tr').filter({ hasText: updatedName });
    await expect(row).toHaveCount(1);
    await expect(row.getByText(updatedName, { exact: true })).toBeVisible();

    await row.getByRole('button', { name: '删除', exact: true }).click();
    const deleteResponse = writeResponse(page, 'DELETE', `/admin/departments/${departmentId}`);
    await page.getByRole('button', { name: '确认删除', exact: true }).click();
    const removed = await deleteResponse;
    expect(removed.status()).toBeLessThan(300);
    expect((await removed.json()).code).toBe('OK');
    deleted = true;
    await expect(page.locator('.el-table__body tr').filter({ hasText: updatedName })).toHaveCount(0);
    await expect(page.getByText('暂无科室，点击右上角新增开始维护', { exact: true })).toBeVisible();

    const response = await authenticatedRequest(page, 'GET', `/admin/departments?keyword=${encodeURIComponent(identifier)}&size=100`);
    const result = await response.json();
    expect(result.code).toBe('OK');
    expect(result.data.records.some(record => record.id === departmentId)).toBe(false);
  } finally {
    // 用 API 清理仅本用例创建的记录，失败也不会遗留演示数据。
    if (departmentId && !deleted) await authenticatedRequest(page, 'DELETE', `/admin/departments/${departmentId}`);
  }
});

test('医生只看到本人接诊与排班，不能进入管理员管理页面', async ({ page }) => {
  const scriptErrors = [];
  page.on('pageerror', error => scriptErrors.push(error.message));
  await login(page, 'DOCTOR');
  const navigation = page.getByRole('navigation', { name: '管理菜单' });
  await expect(navigation.getByRole('link')).toHaveCount(3);
  await expect(navigation.getByRole('link', { name: '工作台', exact: true })).toBeVisible();
  await expect(navigation.getByRole('link', { name: '我的接诊', exact: true })).toBeVisible();
  await expect(navigation.getByRole('link', { name: '我的排班', exact: true })).toBeVisible();
  await expect(navigation.getByRole('link', { name: '科室管理', exact: true })).toHaveCount(0);
  await expect(navigation.getByRole('link', { name: '账号管理', exact: true })).toHaveCount(0);

  const meResponse = await authenticatedRequest(page, 'GET', '/me');
  expect(meResponse.status()).toBe(200);
  const me = (await meResponse.json()).data;
  expect(me.roles).toContain('DOCTOR');
  expect(typeof me.doctorId).toBe('number');
  const dashboard = await authenticatedRequest(page, 'GET', '/admin/dashboard');
  expect(dashboard.status()).toBe(200);
  expect((await dashboard.json()).code).toBe('OK');

  await navigation.getByRole('link', { name: '我的接诊', exact: true }).click();
  await expect(page).toHaveURL(/appointments/);
  await expect(page.getByRole('heading', { name: '我的接诊', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '删除', exact: true })).toHaveCount(0);
  const appointmentsResponse = await authenticatedRequest(page, 'GET', '/admin/appointments?size=100');
  expect(appointmentsResponse.status()).toBe(200);
  const appointments = (await appointmentsResponse.json()).data.records;
  expect(appointments.every(record => record.doctorId === me.doctorId)).toBe(true);

  await navigation.getByRole('link', { name: '我的排班', exact: true }).click();
  await expect(page).toHaveURL(/schedules/);
  await expect(page.getByRole('heading', { name: '我的排班', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增排班', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '编辑', exact: true })).toHaveCount(0);
  const schedulesResponse = await authenticatedRequest(page, 'GET', '/admin/schedules?size=100');
  expect(schedulesResponse.status()).toBe(200);
  const schedules = (await schedulesResponse.json()).data.records;
  expect(schedules.length).toBeGreaterThan(0);
  expect(schedules.every(record => record.doctorId === me.doctorId)).toBe(true);

  for (const route of ['/departments', '/doctors', '/notices', '/users', '/patients', '/logs', '/settings']) {
    await page.goto(route);
    await expect(page).toHaveURL(/dashboard/);
  }
  const forbidden = await authenticatedRequest(page, 'GET', '/admin/users');
  expect(forbidden.status()).toBe(403);
  expect((await forbidden.json()).code).not.toBe('OK');
  expect(scriptErrors).toEqual([]);
});

test('未登录不能访问预约管理', async ({ page }) => {
  await page.goto('/appointments');
  await expect(page).toHaveURL(/login/);
  await expect(page.getByPlaceholder('请输入账号')).toBeVisible();
});
