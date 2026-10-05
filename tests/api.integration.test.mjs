import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const env = Object.fromEntries(readFileSync(resolve(root, '.env.local'), 'utf8').split(/\r?\n/)
  .filter(line => /^[A-Z_][A-Z0-9_]*=/.test(line))
  .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]));
const base = `http://127.0.0.1:${env.SERVER_PORT || 8080}/api/v1`;
const runTag = `qa-${Date.now().toString(36)}`;

async function api(path, { method = 'GET', data, token, headers = {} } = {}) {
  const response = await fetch(base + path, {
    method,
    headers: { ...(data !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: data !== undefined ? JSON.stringify({ data }) : undefined,
    signal: AbortSignal.timeout(15000)
  });
  const body = await response.json();
  assert.equal(typeof body.code, 'string', '响应必须使用 R 包装');
  assert.equal(typeof body.message, 'string');
  assert.equal(typeof body.traceId, 'string');
  assert.ok(body.traceId.length > 0);
  return { status: response.status, body, data: body.data };
}
function ok(result) {
  assert.ok(result.status >= 200 && result.status < 300, `HTTP ${result.status}: ${result.body.message}`);
  assert.equal(result.body.code, 'OK', result.body.message);
  return result.data;
}
function denied(result, allowed = [400, 401, 403, 404, 409]) {
  assert.ok(allowed.includes(result.status), `应拒绝操作，实际 HTTP ${result.status}`);
  assert.notEqual(result.body.code, 'OK');
}

test('真实本地 MySQL/Redis 下的预约、权限及逻辑删除集成验证', async t => {
  const admin = ok(await api('/auth/login', { method: 'POST', data: {
    username: env.DEMO_ADMIN_USERNAME || 'admin', password: env.DEMO_ADMIN_PASSWORD } }));
  assert.ok(admin.user.roles.includes('ADMIN'));
  const adminToken = admin.token;
  const tracked = { appointments: [], schedules: [], doctors: [], users: [], departments: [], notices: [] };
  let patient, profile, doctorUser, doctor, schedule, booking, secondPatient;
  const futureStart = new Date(Date.now() + 4 * 86400000);
  futureStart.setUTCHours(2, 0, 0, 0);
  const scheduleData = (doctorId, start = futureStart, totalSlots = 2) => ({
    doctorId, startTime: start.toISOString(), endTime: new Date(start.getTime() + 3600000).toISOString(),
    totalSlots, status: 'PUBLISHED', cancelBeforeMinutes: 120
  });
  const demo = async name => {
    const login = ok(await api('/auth/demo-login', { method: 'POST', data: { deviceId: `${runTag}-${name}` } }));
    tracked.users.push(login.user.id);
    assert.deepEqual(login.user.roles, ['PATIENT']);
    return login;
  };
  try {
    await t.test('统一 R、未认证错误和嵌套请求校验', async () => {
      ok(await api('/clinic'));
      denied(await api('/admin/users'), [401]);
      denied(await api('/admin/departments', { method: 'POST', token: adminToken, data: null }), [400]);
      denied(await api('/admin/departments', { method: 'POST', token: adminToken,
        data: { name: '', status: 1, sortOrder: 0 } }), [400]);
    });
    await t.test('独立演示身份与本人就诊资料', async () => {
      patient = await demo('patient');
      const sameDevice = ok(await api('/auth/demo-login', { method: 'POST', data: { deviceId: `${runTag}-patient` } }));
      assert.equal(sameDevice.user.id, patient.user.id);
      profile = ok(await api('/me/patient-profile', { method: 'PUT', token: patient.token,
        data: { realName: '集成验证患者', phone: '13800009999', gender: 0 } }));
      assert.equal(profile.userId, patient.user.id);
      secondPatient = await demo('other');
    });
    await t.test('科室医生与合法排班、重叠时段校验', async () => {
      const department = ok(await api('/admin/departments', { method: 'POST', token: adminToken,
        data: { name: runTag, description: '自动验证数据', status: 1, sortOrder: 90 } }));
      tracked.departments.push(department.id);
      doctorUser = ok(await api('/admin/users', { method: 'POST', token: adminToken,
        data: { username: `${runTag}-doctor`, password: 'QaOnly-17@2026', displayName: '验证医生账号', role: 'DOCTOR', status: 1 } }));
      tracked.users.push(doctorUser.id);
      doctor = ok(await api('/admin/doctors', { method: 'POST', token: adminToken,
        data: { userId: doctorUser.id, departmentId: department.id, name: '验证医生', title: '主治医师', status: 1 } }));
      tracked.doctors.push(doctor.id);
      schedule = ok(await api('/admin/schedules', { method: 'POST', token: adminToken, data: scheduleData(doctor.id) }));
      tracked.schedules.push(schedule.id);
      denied(await api('/admin/schedules', { method: 'POST', token: adminToken,
        data: scheduleData(doctor.id, new Date(futureStart.getTime() + 1800000)) }), [400, 409]);
    });
    await t.test('创建预约、幂等重试与重复预约不重复占号', async () => {
      const data = { scheduleId: schedule.id, patientProfileId: profile.id, chiefComplaint: '验证预约流程' };
      booking = ok(await api('/appointments', { method: 'POST', token: patient.token, data,
        headers: { 'Idempotency-Key': `${runTag}-create` } }));
      tracked.appointments.push(booking.id);
      assert.equal(booking.status, 'PENDING');
      const retry = ok(await api('/appointments', { method: 'POST', token: patient.token, data,
        headers: { 'Idempotency-Key': `${runTag}-create` } }));
      assert.equal(retry.id, booking.id);
      denied(await api('/appointments', { method: 'POST', token: patient.token, data }), [409]);
      const schedules = ok(await api(`/admin/schedules?doctorId=${doctor.id}`, { token: adminToken }));
      assert.equal(schedules.records.find(row => row.id === schedule.id).bookedSlots, 1);
      const dashboard = ok(await api('/admin/dashboard', { token: adminToken }));
      assert.ok(dashboard.pendingAppointments > 0);
      assert.ok(dashboard.dailyTrend.some(row => /^\d{4}-\d{2}-\d{2}$/.test(row.date) && row.count > 0));
      assert.ok(dashboard.statusCounts.some(row => row.status === 'PENDING' && row.count > 0));
    });
    await t.test('患者和医生不能越权，已有预约排班禁止改时间', async () => {
      denied(await api(`/appointments/${booking.id}`, { token: secondPatient.token }), [403, 404]);
      denied(await api('/appointments', { method: 'POST', token: secondPatient.token,
        data: { scheduleId: schedule.id, patientProfileId: profile.id } }), [403]);
      denied(await api('/admin/doctors', { token: patient.token }), [403]);
      denied(await api(`/admin/schedules/${schedule.id}`, { method: 'PUT', token: adminToken,
        data: scheduleData(doctor.id, new Date(futureStart.getTime() + 7200000)) }), [400, 409]);
      const doctorLogin = ok(await api('/auth/login', { method: 'POST', data: {
        username: `${runTag}-doctor`, password: 'QaOnly-17@2026' } }));
      denied(await api('/admin/users', { token: doctorLogin.token }), [403]);
      const doctorRows = ok(await api('/admin/schedules', { token: doctorLogin.token }));
      assert.ok(doctorRows.records.every(row => row.doctorId === doctor.id));
    });
    await t.test('取消释放一次，重复取消和终态变更被拒绝', async () => {
      ok(await api(`/appointments/${booking.id}/cancel`, { method: 'POST', token: patient.token, data: { reason: '验证取消' } }));
      denied(await api(`/appointments/${booking.id}/cancel`, { method: 'POST', token: patient.token, data: {} }), [409]);
      const rows = ok(await api(`/admin/schedules?doctorId=${doctor.id}`, { token: adminToken }));
      assert.equal(rows.records.find(row => row.id === schedule.id).bookedSlots, 0);
      denied(await api(`/admin/appointments/${booking.id}/status`, { method: 'PATCH', token: adminToken, data: { status: 'CONFIRMED' } }), [409]);
    });
    await t.test('医生接诊状态流转、完成和爽约后号源守恒', async () => {
      const doctorLogin = ok(await api('/auth/login', { method: 'POST', data: {
        username: `${runTag}-doctor`, password: 'QaOnly-17@2026' } }));
      for (const finalStatus of ['COMPLETED', 'NO_SHOW']) {
        const visit = ok(await api('/appointments', { method: 'POST', token: patient.token,
          data: { scheduleId: schedule.id, patientProfileId: profile.id } }));
        tracked.appointments.push(visit.id);
        denied(await api(`/admin/appointments/${visit.id}/status`, { method: 'PATCH', token: doctorLogin.token,
          data: { status: finalStatus } }), [403]);
        ok(await api(`/admin/appointments/${visit.id}/status`, { method: 'PATCH', token: adminToken,
          data: { status: 'CONFIRMED' } }));
        const finished = ok(await api(`/admin/appointments/${visit.id}/status`, { method: 'PATCH', token: doctorLogin.token,
          data: { status: finalStatus } }));
        assert.equal(finished.status, finalStatus);
        denied(await api(`/admin/appointments/${visit.id}/status`, { method: 'PATCH', token: adminToken,
          data: { status: 'CONFIRMED' } }), [409]);
        const rows = ok(await api(`/admin/schedules?doctorId=${doctor.id}`, { token: adminToken }));
        assert.equal(rows.records.find(row => row.id === schedule.id).bookedSlots, 0);
      }
    });
    await t.test('并发抢最后一个号源只成功一次', async () => {
      const raceSchedule = ok(await api('/admin/schedules', { method: 'POST', token: adminToken,
        data: scheduleData(doctor.id, new Date(futureStart.getTime() + 86400000), 1) }));
      tracked.schedules.push(raceSchedule.id);
      const contenders = await Promise.all(Array.from({ length: 8 }, (_, index) => demo(`race-${index}`)));
      const profiles = await Promise.all(contenders.map(login => api('/me/patient-profile', { token: login.token }).then(ok)));
      const results = await Promise.all(contenders.map((login, index) => api('/appointments', {
        method: 'POST', token: login.token, data: { scheduleId: raceSchedule.id, patientProfileId: profiles[index].id } })));
      const winners = results.filter(result => result.body.code === 'OK');
      assert.equal(winners.length, 1);
      tracked.appointments.push(winners[0].data.id);
      assert.ok(results.filter(result => result.body.code !== 'OK').every(result => result.status === 409));
      const rows = ok(await api(`/admin/schedules?doctorId=${doctor.id}`, { token: adminToken }));
      assert.equal(rows.records.find(row => row.id === raceSchedule.id).bookedSlots, 1);
      ok(await api(`/admin/schedules/${raceSchedule.id}/status`, { method: 'PATCH', token: adminToken, data: { status: 'CLOSED' } }));
      assert.equal(ok(await api(`/appointments/${winners[0].data.id}`, { token: adminToken })).status, 'CANCELLED');
    });
    await t.test('公告逻辑删除后不出现在公开列表，审计日志可追踪', async () => {
      const notice = ok(await api('/admin/notices', { method: 'POST', token: adminToken,
        data: { title: runTag, content: '仅用于自动验证', status: 1, publishAt: new Date().toISOString() } }));
      tracked.notices.push(notice.id);
      ok(await api(`/admin/notices/${notice.id}`, { method: 'DELETE', token: adminToken }));
      const publicNotices = ok(await api('/notices?size=100'));
      assert.ok(publicNotices.records.every(row => row.id !== notice.id));
      assert.ok(ok(await api('/admin/operation-logs', { token: adminToken })).total > 0);
    });
    await t.test('退出后令牌失效且返回 R 错误', async () => {
      ok(await api('/auth/logout', { method: 'POST', token: patient.token, data: {} }));
      denied(await api('/me', { token: patient.token }), [401]);
    });
  } finally {
    // 验证数据只通过应用的逻辑删除接口清理，保留数据库审计记录。
    for (const [type, ids] of Object.entries(tracked)) {
      for (const id of ids) {
        try { await api(`/admin/${type}/${id}`, { method: 'DELETE', token: adminToken }); } catch { }
      }
    }
    mkdirSync(resolve(root, '.local'), { recursive: true });
    writeFileSync(resolve(root, '.local', 'last-api-verification.json'), JSON.stringify({ runTag, finishedAt: new Date().toISOString() }, null, 2));
  }
});
