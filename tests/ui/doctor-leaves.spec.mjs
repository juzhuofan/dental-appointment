import { test, expect } from '@playwright/test';
import { openPreview } from './support/open-preview.mjs';

// Built admin preview with isolated HTTP fixtures; no real login or database writes.
test.use({ baseURL: process.env.ADMIN_FIX_UI_BASE_URL || 'http://127.0.0.1:6190' });

function leave(id = 1, extra = {}) {
  return { id, doctorId: 7, doctorName: '请假测试医生', departmentName: '测试科室',
    startTime: '2099-12-01T09:00:00+08:00', endTime: '2099-12-01T12:00:00+08:00',
    reason: '处理个人事务', status: 'PENDING', createdAt: '2099-11-01T10:00:00+08:00', ...extra };
}

async function prepare(page, { role = 'DOCTOR', initialRows = [], appointmentConflict = false } = {}) {
  const errors = [];
  const writes = [];
  const rows = initialRows.map(row => ({ ...row }));
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => localStorage.setItem('dental.admin.token', 'doctor-leaves-ui-fixture-only'));
  const respond = (route, data, { status = 200, message = 'success', code = 'OK' } = {}) => route.fulfill({
    status, contentType: 'application/json', body: JSON.stringify({ code, message, data }),
  });
  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = request.method();
    if (path === '/me') return respond(route, { id: role === 'ADMIN' ? 9 : 20,
      username: `fixture-${role.toLowerCase()}`, displayName: role === 'ADMIN' ? '请假测试管理员' : '请假测试医生',
      roles: [role], doctorId: role === 'DOCTOR' ? 7 : null });
    if (path === '/admin/doctors' && method === 'GET') return respond(route, {
      records: [{ id: 7, name: '请假测试医生', departmentName: '测试科室', status: 1 }], total: 1, page: 1, size: 100,
    });
    if (path === '/admin/doctor-leaves' && method === 'GET') {
      const status = url.searchParams.get('status');
      const doctorId = url.searchParams.get('doctorId');
      const visible = rows.filter(row => (!status || row.status === status)
        && (!doctorId || row.doctorId === Number(doctorId)) && (role === 'ADMIN' || row.doctorId === 7));
      const pageNo = Number(url.searchParams.get('page') || 1);
      const size = Number(url.searchParams.get('size') || 10);
      return respond(route, { records: visible.slice((pageNo - 1) * size, pageNo * size), total: visible.length, page: pageNo, size });
    }
    if (path === '/admin/doctor-leaves' && method === 'POST') {
      const data = request.postDataJSON().data;
      writes.push({ path, data });
      const created = leave(100, data);
      rows.unshift(created);
      return respond(route, created);
    }
    const action = path.match(/^\/admin\/doctor-leaves\/(\d+)\/(review|withdraw)$/);
    if (action && method === 'POST') {
      const data = request.postDataJSON().data;
      writes.push({ path, data });
      const row = rows.find(item => item.id === Number(action[1]));
      if (!row) throw new Error(`Fixture leave not found: ${action[1]}`);
      if (action[2] === 'withdraw') {
        row.status = 'WITHDRAWN';
      } else {
        if (appointmentConflict && data.approved && !data.confirmCancelAppointments) return respond(route, null, {
          status: 409, code: 'LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED',
          message: '批准此请假将停诊 1 个排班，并取消 2 个未完成预约，请确认后再次审批',
        });
        row.status = data.approved ? 'APPROVED' : 'REJECTED';
        row.reviewRemark = data.remark;
        row.reviewedBy = 9;
        row.reviewedAt = '2099-11-02T10:00:00+08:00';
      }
      return respond(route, { ...row });
    }
    throw new Error(`Unexpected mocked request: ${method} ${path}`);
  });
  await openPreview(page, '/doctor-leaves');
  await expect(page.getByRole('heading', { name: '请假管理', exact: true })).toBeVisible();
  await expect(page.locator('.el-table .el-loading-mask')).toHaveCount(0);
  return { errors, writes, rows };
}

test('doctor submits a leave application and sees the new pending row', async ({ page }) => {
  const fixture = await prepare(page);
  await expect(page.locator('nav').getByRole('link', { name: '请假管理', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '申请请假', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '申请请假', exact: true });
  await dialog.getByPlaceholder('选择请假开始时间', { exact: true }).fill('2099-12-01 09:00');
  await dialog.getByPlaceholder('选择请假开始时间', { exact: true }).press('Tab');
  await dialog.getByPlaceholder('选择请假结束时间', { exact: true }).fill('2099-12-01 12:00');
  await dialog.getByPlaceholder('选择请假结束时间', { exact: true }).press('Tab');
  await dialog.getByPlaceholder('请填写请假原因，供管理员审批', { exact: true }).fill('  就诊时间安排测试  ');
  await dialog.getByRole('button', { name: '提交申请', exact: true }).click();
  await expect(dialog).not.toBeVisible();
  const row = page.locator('.el-table__body tr').filter({ hasText: '就诊时间安排测试' });
  await expect(row).toContainText('待审批');
  expect(fixture.writes).toEqual([{ path: '/admin/doctor-leaves', data: {
    startTime: '2099-12-01T09:00:00+08:00', endTime: '2099-12-01T12:00:00+08:00', reason: '就诊时间安排测试',
  } }]);
  expect(fixture.errors).toEqual([]);
  await page.screenshot({ path: '.local/screenshots/doctor-leaves-doctor.png', fullPage: true });
});

test('doctor withdraws a pending application after explicit confirmation', async ({ page }) => {
  const fixture = await prepare(page, { initialRows: [leave()] });
  const row = page.locator('.el-table__body tr').filter({ hasText: '处理个人事务' });
  await row.getByRole('button', { name: '撤回', exact: true }).click();
  const confirmation = page.locator('.el-message-box');
  await expect(confirmation).toContainText('撤回请假申请');
  expect(fixture.writes).toHaveLength(0);
  await confirmation.getByRole('button', { name: '确认撤回', exact: true }).click();
  await expect(row).toContainText('已撤回');
  await expect(row.getByRole('button', { name: '撤回', exact: true })).toHaveCount(0);
  expect(fixture.writes).toEqual([{ path: '/admin/doctor-leaves/1/withdraw', data: {} }]);
  expect(fixture.errors).toEqual([]);
});

test('admin cancelling the appointment-impact confirmation does not approve or retry', async ({ page }) => {
  const fixture = await prepare(page, { role: 'ADMIN', initialRows: [leave()], appointmentConflict: true });
  const row = page.locator('.el-table__body tr').filter({ hasText: '处理个人事务' });
  await row.getByRole('button', { name: '批准', exact: true }).click();
  const review = page.getByRole('dialog', { name: '批准请假', exact: true });
  await review.getByRole('button', { name: '确认批准', exact: true }).click();
  const confirmation = page.locator('.el-message-box');
  await expect(confirmation).toContainText('取消 2 个未完成预约');
  await expect(page.locator('.el-message--error')).toHaveCount(0);
  await confirmation.getByRole('button', { name: '返回审批', exact: true }).click();
  await expect(confirmation).not.toBeVisible();
  await expect(review).toBeVisible();
  await expect(review.getByRole('button', { name: '确认批准', exact: true })).toBeEnabled();
  expect(fixture.writes).toEqual([{ path: '/admin/doctor-leaves/1/review', data: {
    approved: true, remark: '', confirmCancelAppointments: false,
  } }]);
  expect(fixture.rows[0].status).toBe('PENDING');
  expect(fixture.errors).toEqual([]);
});

test('admin confirms appointment cancellation before retrying approval with true', async ({ page }) => {
  const fixture = await prepare(page, { role: 'ADMIN', initialRows: [leave()], appointmentConflict: true });
  const row = page.locator('.el-table__body tr').filter({ hasText: '处理个人事务' });
  await row.getByRole('button', { name: '批准', exact: true }).click();
  const review = page.getByRole('dialog', { name: '批准请假', exact: true });
  await review.getByPlaceholder('如有补充安排，请在此说明', { exact: true }).fill('已联系相关患者');
  await review.getByRole('button', { name: '确认批准', exact: true }).click();
  const confirmation = page.locator('.el-message-box');
  await expect(confirmation).toContainText('批准请假并取消预约');
  expect(fixture.writes).toHaveLength(1);
  await confirmation.getByRole('button', { name: '确认批准并取消预约', exact: true }).click();
  await expect(review).not.toBeVisible();
  await expect(row).toContainText('已批准');
  await expect(row).toContainText('已联系相关患者');
  await expect(page.locator('.el-message--error')).toHaveCount(0);
  expect(fixture.writes.map(write => write.data)).toEqual([
    { approved: true, remark: '已联系相关患者', confirmCancelAppointments: false },
    { approved: true, remark: '已联系相关患者', confirmCancelAppointments: true },
  ]);
  expect(fixture.errors).toEqual([]);
  await page.screenshot({ path: '.local/screenshots/doctor-leaves-admin.png', fullPage: true });
});
