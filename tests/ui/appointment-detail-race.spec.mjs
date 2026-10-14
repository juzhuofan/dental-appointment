import { test, expect } from '@playwright/test';
import { openPreview } from './support/open-preview.mjs';

// Dedicated preview with in-memory account and appointment fixtures; no database writes.
test.use({ baseURL: process.env.ADMIN_FIX_UI_BASE_URL || 'http://127.0.0.1:6190' });

async function prepare(page, { holdARead = 1 } = {}) {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.addInitScript(() => localStorage.setItem('dental.admin.token', 'fix-regression-fixture-only'));
  const appointment = (id, name, status) => ({ id, appointmentNo: `FIX-${id === 1 ? 'A' : 'B'}`,
    patientName: name, patientPhone: '13800000000', doctorName: '测试医生', departmentName: '测试科室',
    status, startTime: '2026-12-20T10:00:00+08:00', endTime: '2026-12-20T11:00:00+08:00',
    createdAt: '2026-10-15T10:00:00+08:00' });
  const rows = [appointment(1, '测试患者甲', 'PENDING'), appointment(2, '测试患者乙', 'CONFIRMED')];
  let aReads = 0;
  let releaseHeld;
  let held = false;
  const respond = (route, data, status = 200) => route.fulfill({ status, contentType: 'application/json',
    body: JSON.stringify({ code: status === 200 ? 'OK' : 'AUDIT_FAILURE', message: status === 200 ? 'success' : '旧详情请求失败', data }) });
  await page.route('**/api/v1/**', async route => {
    const req = route.request();
    const path = new URL(req.url()).pathname.replace('/api/v1', '');
    if (path === '/me') return respond(route, { id: 7, username: 'fixture-admin', displayName: '测试管理员', roles: ['ADMIN'] });
    if (path === '/admin/appointments' && req.method() === 'GET') return respond(route, { records: rows, total: 2, page: 1, size: 10 });
    if (path === '/admin/doctors' || path === '/admin/departments') return respond(route, { records: [], total: 0, page: 1, size: 100 });
    if (path === '/appointments/1') {
      aReads++;
      const snapshot = { ...rows[0] };
      if (aReads === holdARead) {
        held = true;
        const failure = await new Promise(resolve => { releaseHeld = resolve; });
        return respond(route, failure ? null : snapshot, failure ? 503 : 200);
      }
      return respond(route, snapshot);
    }
    if (path === '/appointments/2') return respond(route, { ...rows[1] });
    if (path === '/admin/appointments/1/status' && req.method() === 'PATCH') {
      rows[0].status = req.postDataJSON().data.status;
      return respond(route, { ...rows[0] });
    }
    throw new Error(`Unexpected mocked request: ${req.method()} ${path}`);
  });
  await openPreview(page, '/appointments');
  await expect(page.getByRole('button', { name: 'FIX-A', exact: true })).toBeVisible();
  return { errors, held: () => held, aReads: () => aReads, release: failure => releaseHeld?.(failure) };
}

async function closeThenOpenB(page) {
  await page.locator('.el-drawer__close-btn').click();
  await expect(page.locator('.el-drawer')).not.toBeVisible();
  await page.getByRole('button', { name: 'FIX-B', exact: true }).click();
  await expect(page.locator('.detail-drawer h2')).toHaveText('FIX-B');
}

for (const failure of [false, true]) {
  test(`closing A and opening B ignores A's late ${failure ? 'failure' : 'success'}`, async ({ page }) => {
    const fixture = await prepare(page);
    try {
      await page.getByRole('button', { name: 'FIX-A', exact: true }).click();
      await expect.poll(fixture.held).toBe(true);
      await closeThenOpenB(page);
      const response = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/appointments/1'));
      fixture.release(failure);
      await response;
      await expect(page.locator('.detail-drawer h2')).toHaveText('FIX-B');
      await expect(page.locator('.detail-drawer')).toContainText('测试患者乙');
      await expect(page.locator('.detail-drawer .el-alert')).toHaveCount(0);
      await expect(page.locator('.detail-actions')).toContainText('完成接诊');
      expect(fixture.errors).toEqual([]);
    } finally { fixture.release(false); }
  });
}

test('status-update refresh for A cannot replace B after switching the drawer', async ({ page }) => {
  const fixture = await prepare(page, { holdARead: 2 });
  try {
    await page.getByRole('button', { name: 'FIX-A', exact: true }).click();
    await expect(page.locator('.detail-drawer h2')).toHaveText('FIX-A');
    await page.locator('.detail-actions').getByRole('button', { name: '确认预约', exact: true }).click();
    await page.locator('.el-message-box').getByRole('button', { name: '确认', exact: true }).click();
    await expect.poll(fixture.aReads).toBe(2);
    await expect.poll(fixture.held).toBe(true);
    await closeThenOpenB(page);
    const response = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/appointments/1'));
    fixture.release(false);
    await response;
    await expect(page.locator('.detail-drawer h2')).toHaveText('FIX-B');
    await expect(page.locator('.detail-drawer')).toContainText('测试患者乙');
    expect(fixture.errors).toEqual([]);
  } finally { fixture.release(false); }
});
