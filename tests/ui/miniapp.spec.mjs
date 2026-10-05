import { test, expect, request as playwrightRequest } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';

const env = Object.fromEntries(readFileSync(new URL('../../.env.local', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => /^[A-Z_][A-Z0-9_]*=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const apiBase = `http://127.0.0.1:${env.SERVER_PORT || 8080}/api/v1`;
const h5Base = 'http://127.0.0.1:5174';

test.use({ baseURL: h5Base, viewport: { width: 375, height: 812 } });

async function api(request, path, { method = 'GET', token, data } = {}) {
  const response = await request.fetch(`${apiBase}${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(data !== undefined ? { data: { data } } : {})
  });
  const body = await response.json();
  expect(response.ok(), `${method} ${path} 返回 ${response.status()}`).toBeTruthy();
  expect(body.code, `${method} ${path} 应返回 R.code=OK`).toBe('OK');
  return body.data;
}

function chinaDay(value) {
  const date = new Date(value.getTime() + 8 * 3600000);
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}

async function prepareFixture(request) {
  const admin = await api(request, '/auth/login', { method: 'POST', data: {
    username: env.DEMO_ADMIN_USERNAME || 'admin', password: env.DEMO_ADMIN_PASSWORD
  } });
  const departments = await api(request, '/departments?page=1&size=100');
  const department = departments.records.find(row => row.name === '牙体牙髓科') || departments.records[0];
  expect(department, '本地种子数据必须至少提供一个启用的科室').toBeTruthy();
  const tag = Date.now().toString(36);
  const doctor = await api(request, '/admin/doctors', { method: 'POST', token: admin.token,
    data: { departmentId: department.id, name: `流程验证医师${tag}`, title: '测试医生', specialty: '预约流程验证', status: 1 } });
  const day = chinaDay(new Date(Date.now() + 12 * 86400000));
  const schedule = await api(request, '/admin/schedules', { method: 'POST', token: admin.token,
    data: { doctorId: doctor.id, startTime: `${day}T10:00:00+08:00`, endTime: `${day}T11:00:00+08:00`,
      totalSlots: 2, status: 'PUBLISHED', cancelBeforeMinutes: 120 } });
  return { admin, doctor, schedule, day, patient: null, appointments: [] };
}

async function cleanup(ignoredRequest, fixture) {
  if (!fixture) return;
  // 独立上下文保证失败/超时后仍可通过 API 清理本用例的虚构数据。
  const request = await playwrightRequest.newContext();
  try {
  for (const id of fixture.appointments) {
    await api(request, `/admin/appointments/${id}`, { method: 'DELETE', token: fixture.admin.token });
  }
  await api(request, `/admin/schedules/${fixture.schedule.id}`, { method: 'DELETE', token: fixture.admin.token });
  await api(request, `/admin/doctors/${fixture.doctor.id}`, { method: 'DELETE', token: fixture.admin.token });
  if (fixture.patient) await api(request, `/admin/users/${fixture.patient.user.id}`, { method: 'DELETE', token: fixture.admin.token });
  await api(request, '/auth/logout', { method: 'POST', token: fixture.admin.token, data: {} });
  } finally {
    await request.dispose();
  }
}

async function clickDemoLogin(page) {
  const responsePromise = page.waitForResponse(response => response.url().endsWith('/auth/demo-login') && response.request().method() === 'POST');
  await page.getByText('一键登录，开始预约', { exact: true }).click();
  const response = await responsePromise;
  const body = await response.json();
  expect(response.ok()).toBeTruthy();
  expect(body.code).toBe('OK');
  expect(body.data.user.roles).toEqual(['PATIENT']);
  return body.data;
}

test('患者在375×812屏幕登录、编辑资料、浏览号源、预约并取消，页面无脚本异常', async ({ page, request }) => {
  const scriptErrors = [];
  page.on('pageerror', error => scriptErrors.push(error.message));
  mkdirSync('.local/screenshots', { recursive: true });
  let fixture;
  try {
    await page.goto('/#/pages/home/index');
    await expect(page.getByText('从一份安心预约开始', { exact: true })).toBeVisible();
    await expect(page.getByText('牙体牙髓科', { exact: true }).first()).toBeVisible();
    await page.screenshot({ path: '.local/screenshots/miniapp-home.png', fullPage: true });
    fixture = await prepareFixture(request);

    await page.getByText('我的', { exact: true }).click();
    await page.getByText('一键登录', { exact: true }).click();
    fixture.patient = await clickDemoLogin(page);
    await expect(page).toHaveURL(/pages\/me\/index/);
    await page.getByText('就诊资料', { exact: true }).click();
    await expect(page.getByText('完善就诊资料', { exact: true })).toBeVisible();
    await page.locator('.field').filter({ hasText: '就诊人姓名 *' }).locator('input').fill('前端验证患者');
    await page.locator('.field').filter({ hasText: '联系电话 *' }).locator('input').fill('13800001234');
    await page.locator('textarea').fill('自动验证使用的虚构资料');
    const savePromise = page.waitForResponse(response => response.url().endsWith('/me/patient-profile') && response.request().method() === 'PUT');
    await page.getByText('保存就诊资料', { exact: true }).click();
    const saved = (await (await savePromise).json()).data;
    expect(saved.realName).toBe('前端验证患者');
    expect(saved.phone).toBe('13800001234');

    await page.goto('/#/pages/home/index');
    await page.getByText('全部医生 ›', { exact: true }).click();
    const search = page.locator('.search-box input');
    await search.fill(fixture.doctor.name);
    await search.press('Enter');
    await page.locator('.doctor-card').filter({ hasText: fixture.doctor.name }).click();
    await expect(page).toHaveURL(/pages\/doctor\/index/);
    const datePromise = page.waitForResponse(response => response.url().includes('/schedules?') && response.url().includes(`dateFrom=${fixture.day}`));
    await page.locator('.date-item').nth(12).click();
    await datePromise;
    await page.locator('.slot-card').filter({ hasText: '10:00–11:00' }).getByText('预约', { exact: true }).click();
    await expect(page).toHaveURL(/pages\/confirm\/index/);
    await expect(page.locator('.field').filter({ hasText: '就诊人姓名 *' }).locator('input')).toHaveValue('前端验证患者');
    await page.locator('textarea').fill('验证浏览器完整预约与取消流程');
    const bookingPromise = page.waitForResponse(response => response.url().endsWith('/appointments') && response.request().method() === 'POST');
    await page.getByText('确认资料，提交预约', { exact: true }).click();
    const bookingResponse = await bookingPromise;
    const booking = (await bookingResponse.json()).data;
    expect(bookingResponse.ok()).toBeTruthy();
    expect(booking.status).toBe('PENDING');
    fixture.appointments.push(booking.id);
    await expect(page.getByText('预约已提交', { exact: true }).last()).toBeVisible();
    await page.getByText('查看这次预约', { exact: true }).click();
    await expect(page.getByText('取消本次预约', { exact: true })).toBeVisible();
    await expect(page.locator('.status-name')).toHaveText('待确认');
    await expect(page.locator('uni-toast')).toBeHidden();
    await page.screenshot({ path: '.local/screenshots/miniapp-appointment.png', fullPage: true });

    await page.getByText('取消本次预约', { exact: true }).click();
    await page.getByPlaceholder('取消原因（选填）').fill('端到端验证结束');
    const cancelPromise = page.waitForResponse(response => response.url().endsWith(`/appointments/${booking.id}/cancel`));
    await page.getByText('确定取消', { exact: true }).click();
    const cancelled = (await (await cancelPromise).json()).data;
    expect(cancelled.status).toBe('CANCELLED');
    await expect(page.locator('.status-name')).toHaveText('已取消');
    await expect(page.getByText('取消本次预约', { exact: true })).toHaveCount(0);
    const slot = await api(request, `/schedules/${fixture.schedule.id}`);
    expect(slot.bookedSlots).toBe(0);
    expect(slot.remainingSlots).toBe(2);
    expect(scriptErrors).toEqual([]);
  } finally {
    await cleanup(request, fixture);
  }
});

test('预约提交结果未知时冻结编辑，原幂等键及参数重试只占一个号源', async ({ page, request }) => {
  const scriptErrors = [];
  page.on('pageerror', error => scriptErrors.push(error.message));
  let fixture;
  try {
    fixture = await prepareFixture(request);
    const next = `/pages/confirm/index?scheduleId=${fixture.schedule.id}&doctorId=${fixture.doctor.id}`;
    await page.goto(`/#/pages/login/index?next=${encodeURIComponent(next)}`);
    fixture.patient = await clickDemoLogin(page);
    await expect(page).toHaveURL(/pages\/confirm\/index/);
    const complaint = page.locator('textarea');
    await complaint.fill('验证结果未知时保持原提交内容');
    let firstAttempt = true;
    let committedAppointment;
    const submissions = [];
    await page.route('**/api/v1/appointments', async route => {
      if (route.request().method() !== 'POST') { await route.continue(); return; }
      submissions.push({ key: route.request().headers()['idempotency-key'], data: route.request().postDataJSON() });
      if (firstAttempt) {
        firstAttempt = false;
        // 实际服务已经提交成功，再模拟响应丢失。
        const response = await route.fetch();
        const body = await response.json();
        expect(body.code).toBe('OK');
        committedAppointment = body.data;
        fixture.appointments.push(committedAppointment.id);
        await route.abort('failed');
      } else {
        await route.continue();
      }
    });
    await page.getByText('确认资料，提交预约', { exact: true }).click();
    await expect(page.getByText('上次提交结果尚未确认', { exact: true })).toBeVisible();
    await expect(complaint).toBeDisabled();
    await expect(page.locator('.field').filter({ hasText: '就诊人姓名 *' }).locator('input')).toBeDisabled();
    await expect(page.locator('.field').filter({ hasText: '联系电话 *' }).locator('input')).toBeDisabled();
    await expect(page.getByText('查看我的预约', { exact: true })).toBeVisible();
    const retryPromise = page.waitForResponse(response => response.url().endsWith('/appointments') && response.request().method() === 'POST');
    await page.getByText('重试确认上次预约结果', { exact: true }).click();
    const repeated = (await (await retryPromise).json()).data;
    expect(repeated.id).toBe(committedAppointment.id);
    expect(submissions).toHaveLength(2);
    expect(submissions[1]).toEqual(submissions[0]);
    await expect(page).toHaveURL(/pages\/result\/index/);
    const slot = await api(request, `/schedules/${fixture.schedule.id}`);
    expect(slot.bookedSlots).toBe(1);
    expect(slot.remainingSlots).toBe(1);
    expect(scriptErrors).toEqual([]);
  } finally {
    await cleanup(request, fixture);
  }
});
