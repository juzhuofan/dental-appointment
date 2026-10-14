import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

// Execute the production SFC script and list composable with in-memory API/UI adapters.
const requireAdmin = createRequire(new URL('../../admin-web/package.json', import.meta.url));
const ts = requireAdmin('typescript');
const vue = requireAdmin('vue');
const compile = source => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const component = await readFile(new URL('../../admin-web/src/views/DoctorLeavesView.vue', import.meta.url), 'utf8');
const script = component.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const viewCode = compile(`${script}\nexport { records, paging, loading, error, load, filters, doctors, doctorsLoading, doctorsError,
  loadDoctors, mutation, canApply, applyVisible, applyForm, applyFormRef, applyError, openApply, submitApply,
  reviewVisible, reviewTarget, reviewForm, reviewFormRef, reviewError, openReview, submitReview, withdraw, actionError };`);
const listCode = compile(await readFile(new URL('../../admin-web/src/composables/usePagedList.ts', import.meta.url), 'utf8'));
const formatCode = compile(await readFile(new URL('../../admin-web/src/utils/format.ts', import.meta.url), 'utf8'));

function evaluate(code, requireMock) {
  const module = { exports: {} };
  new Function('require', 'module', 'exports', code)(requireMock, module, module.exports);
  return module.exports;
}
const format = evaluate(formatCode, () => { throw new Error('Unexpected format dependency'); });
const leave = (id = 1, extra = {}) => ({ id, doctorId: 7, doctorName: '测试医生',
  startTime: '2099-12-01T09:00:00+08:00', endTime: '2099-12-01T12:00:00+08:00',
  reason: '测试请假', status: 'PENDING', createdAt: '2099-11-01T10:00:00+08:00', ...extra });
const page = (records = [], total = records.length) => ({ records, total, page: 1, size: 10 });
const futureForm = { startTime: '2099-12-01T09:00:00', endTime: '2099-12-01T12:00:00', reason: '  因事请假  ' };
function deferred() { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; }

function harness({ admin = false, doctorId = 7, get, post, confirm } = {}) {
  const calls = { get: [], post: [], confirm: [], success: [] };
  const unmount = [];
  const auth = { isAdmin: admin, isDoctor: !admin, user: { id: 20, doctorId } };
  const api = {
    async get(endpoint, params) { calls.get.push({ endpoint, params }); return get ? get(endpoint, params) : page(); },
    async post(endpoint, data) { calls.post.push({ endpoint, data }); return post ? post(endpoint, data) : {}; },
  };
  const list = evaluate(listCode, name => {
    if (name === 'vue') return vue;
    if (name === '../api/http') return { api };
    if (name === '../utils/format') return format;
    throw new Error(`Unexpected list dependency: ${name}`);
  });
  const view = evaluate(viewCode, name => {
    if (name === 'vue') return { ...vue, onMounted() {}, onBeforeUnmount(callback) { unmount.push(callback); } };
    if (name === 'element-plus') return {
      ElMessage: { success(message) { calls.success.push(message); } },
      ElMessageBox: { async confirm(...args) { calls.confirm.push(args); if (confirm) return confirm(...args); } },
    };
    if (name === '../api/http') return { api };
    if (name === '../stores/auth') return { useAuthStore: () => auth };
    if (name === '../composables/usePagedList') return list;
    if (name === '../utils/format') return format;
    if (name === '@element-plus/icons-vue' || name.endsWith('.vue')) return {};
    throw new Error(`Unexpected view dependency: ${name}`);
  });
  view.applyFormRef.value = { validate: async () => true, clearValidate() {} };
  view.reviewFormRef.value = { validate: async () => true, clearValidate() {} };
  return { view, calls, auth, unmount: () => unmount.forEach(callback => callback()) };
}

test('医生申请使用北京时间和本人身份，防并发提交，成功后刷新列表', async () => {
  const writing = deferred();
  const h = harness({ post: () => writing.promise });
  h.view.openApply(); Object.assign(h.view.applyForm, futureForm);
  const first = h.view.submitApply();
  await Promise.resolve();
  await h.view.submitApply();
  assert.equal(h.calls.post.length, 1);
  assert.deepEqual(h.calls.post[0], { endpoint: '/admin/doctor-leaves', data: {
    startTime: '2099-12-01T09:00:00+08:00', endTime: '2099-12-01T12:00:00+08:00', reason: '因事请假',
  } });
  assert.equal(h.view.mutation.value, 'apply');
  writing.resolve({}); await first;
  assert.equal(h.view.applyVisible.value, false);
  assert.equal(h.view.mutation.value, '');
  assert.equal(h.calls.get[0].endpoint, '/admin/doctor-leaves');
});

test('申请拒绝无效时间、过去时间、空白或过长原因，不发写请求', async () => {
  for (const invalid of [
    { endTime: '2099-12-01T08:00:00' }, { startTime: '2000-01-01T09:00:00' },
    { startTime: 'invalid-date' }, { reason: '   ' }, { reason: '字'.repeat(501) },
  ]) {
    const h = harness(); h.view.openApply(); Object.assign(h.view.applyForm, futureForm, invalid);
    await h.view.submitApply();
    assert.equal(h.calls.post.length, 0);
    assert.ok(h.view.applyError.value);
    assert.equal(h.view.mutation.value, '');
  }
});

test('申请失败保留弹窗和草稿，解除忙状态后可重试', async () => {
  let attempts = 0;
  const h = harness({ post: async () => { if (++attempts === 1) throw new Error('申请暂时失败'); } });
  h.view.openApply(); Object.assign(h.view.applyForm, futureForm);
  await h.view.submitApply();
  assert.equal(h.view.applyVisible.value, true);
  assert.equal(h.view.applyForm.reason, futureForm.reason);
  assert.equal(h.view.applyError.value, '申请暂时失败');
  assert.equal(h.view.mutation.value, '');
  await h.view.submitApply();
  assert.equal(attempts, 2);
  assert.equal(h.view.applyVisible.value, false);
});

test('未关联医生的账号及管理员不能使用医生申请接口', async () => {
  for (const options of [{ doctorId: null }, { admin: true }]) {
    const h = harness(options); h.view.openApply(); Object.assign(h.view.applyForm, futureForm);
    await h.view.submitApply();
    assert.equal(h.view.canApply.value, false);
    assert.equal(h.view.applyVisible.value, false);
    assert.equal(h.calls.post.length, 0);
  }
});

test('拒绝需要非空备注，通过时备注可空；非管理员不能审批', async () => {
  const h = harness({ admin: true }); h.view.openReview(leave(), false);
  h.view.reviewForm.remark = '   '; await h.view.submitReview();
  assert.equal(h.calls.post.length, 0);
  assert.match(h.view.reviewError.value, /拒绝原因|填写原因/);
  h.view.reviewForm.remark = '  请先调整接诊安排  '; await h.view.submitReview();
  assert.deepEqual(h.calls.post[0].data, { approved: false, remark: '请先调整接诊安排', confirmCancelAppointments: false });
  assert.equal(h.view.reviewVisible.value, false);
  h.view.openReview(leave(2), true); await h.view.submitReview();
  assert.deepEqual(h.calls.post[1].data, { approved: true, remark: '', confirmCancelAppointments: false });
  const doctor = harness(); doctor.view.openReview(leave(), true); await doctor.view.submitReview();
  assert.equal(doctor.calls.post.length, 0);
});

test('批准遇预约冲突只有明确确认后才携带true重试，并锁定原审批对象', async () => {
  let attempts = 0; const confirming = deferred();
  const h = harness({ admin: true, post: async () => {
    if (++attempts === 1) throw Object.assign(new Error('有2个未完成预约，请确认'), { status: 409, code: 'LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED' });
  }, confirm: () => confirming.promise });
  h.view.openReview(leave(), true);
  const approval = h.view.submitReview();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(h.calls.post.length, 1);
  assert.equal(h.calls.confirm.length, 1);
  assert.match(h.calls.confirm[0][0], /取消相关待确认、已确认预约/);
  h.view.openReview(leave(2), true); await h.view.submitReview();
  assert.equal(h.view.reviewTarget.value.id, 1);
  assert.equal(h.calls.post.length, 1);
  confirming.resolve(); await approval;
  assert.equal(h.calls.post.length, 2);
  assert.equal(h.calls.post[0].data.confirmCancelAppointments, false);
  assert.equal(h.calls.post[1].data.confirmCancelAppointments, true);
  assert.equal(h.calls.post[1].endpoint, '/admin/doctor-leaves/1/review');
});

test('取消预约影响确认不发第二次审批请求，保留可编辑弹窗', async () => {
  const h = harness({ admin: true, post: async () => {
    throw Object.assign(new Error('已有预约，需要确认'), { status: 409 });
  }, confirm: async () => { throw 'cancel'; } });
  h.view.openReview(leave(), true); h.view.reviewForm.remark = '保留审批说明';
  await h.view.submitReview();
  assert.equal(h.calls.post.length, 1);
  assert.equal(h.view.reviewVisible.value, true);
  assert.equal(h.view.reviewForm.remark, '保留审批说明');
  assert.equal(h.view.reviewError.value, '');
  assert.equal(h.view.mutation.value, '');
});

test('普通审批失败或二次审批失败展示错误并解除锁定，不误报成功', async () => {
  for (const secondFailure of [false, true]) {
    let attempts = 0;
    const h = harness({ admin: true, post: async () => {
      if (secondFailure && ++attempts === 1) throw Object.assign(new Error('已有预约需确认'), { status: 409 });
      throw Object.assign(new Error('审批状态已变化'), { status: 409 });
    } });
    h.view.openReview(leave(), true); await h.view.submitReview();
    assert.equal(h.calls.post.length, secondFailure ? 2 : 1);
    assert.equal(h.calls.confirm.length, secondFailure ? 1 : 0);
    assert.equal(h.calls.success.length, 0);
    assert.equal(h.view.reviewError.value, '审批状态已变化');
    assert.equal(h.view.reviewVisible.value, true);
    assert.equal(h.view.mutation.value, '');
  }
});

test('医生只能撤回本人待审批申请，取消确认或终态不提交', async () => {
  const h = harness();
  await h.view.withdraw(leave(1, { doctorId: 8 }));
  await h.view.withdraw(leave(1, { status: 'APPROVED' }));
  assert.equal(h.calls.confirm.length, 0);
  await h.view.withdraw(leave());
  assert.deepEqual(h.calls.post, [{ endpoint: '/admin/doctor-leaves/1/withdraw', data: {} }]);
  assert.equal(h.view.mutation.value, '');
  const cancelled = harness({ confirm: async () => { throw 'cancel'; } });
  await cancelled.view.withdraw(leave());
  assert.equal(cancelled.calls.post.length, 0);
  assert.equal(cancelled.view.actionError.value, '');
});

test('实际列表composable忽略乱序旧响应，医生查询不传可伪造doctorId', async () => {
  const oldPage = deferred(); const newPage = deferred(); let reads = 0;
  const h = harness({ get: () => ++reads === 1 ? oldPage.promise : newPage.promise });
  h.view.filters.doctorId = 99;
  const older = h.view.load(); h.view.filters.status = 'APPROVED'; const latest = h.view.load(true);
  assert.equal(h.calls.get[0].params.doctorId, undefined);
  newPage.resolve(page([leave(2, { status: 'APPROVED' })])); await latest;
  oldPage.resolve(page([leave(1)])); await older;
  assert.equal(h.view.records.value[0].id, 2);
  assert.equal(h.view.loading.value, false);
  const admin = harness({ admin: true }); admin.view.filters.doctorId = 99; await admin.view.load();
  assert.equal(admin.calls.get[0].params.doctorId, 99);
});

test('管理员医生选项读取全部分页，较早失败不覆盖新选项或错误状态', async () => {
  const oldRequest = deferred(); let requests = 0;
  const h = harness({ admin: true, get: async (_endpoint, params) => {
    if (++requests === 1) return oldRequest.promise;
    return params.page === 1 ? page(Array.from({ length: 100 }, (_, index) => ({ id: index + 1, name: `医生${index + 1}` })), 101)
      : page([{ id: 101, name: '医生101' }], 101);
  } });
  const older = h.view.loadDoctors(); await h.view.loadDoctors();
  oldRequest.reject(new Error('过期的医生选项请求失败')); await older;
  assert.equal(h.view.doctors.value.length, 101);
  assert.equal(h.view.doctors.value.at(-1).id, 101);
  assert.equal(h.view.doctorsError.value, '');
  assert.equal(h.view.doctorsLoading.value, false);
});

test('页面卸载后迟到写请求不关闭新界面、不提示成功或触发刷新', async () => {
  const writing = deferred();
  const h = harness({ post: () => writing.promise });
  h.view.openApply(); Object.assign(h.view.applyForm, futureForm);
  const application = h.view.submitApply(); await Promise.resolve();
  h.unmount(); writing.resolve({}); await application;
  assert.equal(h.calls.success.length, 0);
  assert.equal(h.calls.get.length, 0);
});
