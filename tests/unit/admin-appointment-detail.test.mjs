import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';

// 执行生产 composable，经项目现有 TypeScript 转译；不复制实现，不请求真实服务。
const requireAdmin = createRequire(new URL('../../admin-web/package.json', import.meta.url));
const ts = requireAdmin('typescript');
const { effectScope } = requireAdmin('vue');
const source = await readFile(new URL('../../admin-web/src/composables/useAppointmentDetail.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const compiledModule = { exports: {} };
new Function('require', 'module', 'exports', compiled)(requireAdmin, compiledModule, compiledModule.exports);
const { useAppointmentDetail } = compiledModule.exports;

function appointment(id, status = 'CONFIRMED') {
  return { id, status, appointmentNo: `fixture-${id}`, patientName: `就诊人${id}` };
}

function harness(t) {
  const requests = [];
  const scope = effectScope();
  const panel = scope.run(() => useAppointmentDetail(id => new Promise((resolve, reject) => {
    requests.push({ id, resolve, reject });
  })));
  t.after(() => scope.stop());
  return { panel, requests, scope };
}

test('正常详情请求显示加载状态，成功后显示当前记录', async t => {
  const { panel, requests } = harness(t);
  const opening = panel.open(1);
  assert.equal(panel.drawer.value, true);
  assert.equal(panel.detailBusy.value, true);
  assert.equal(panel.detail.value, null);
  requests[0].resolve(appointment(1));
  await opening;
  assert.equal(panel.detail.value.id, 1);
  assert.equal(panel.detailBusy.value, false);
  assert.equal(panel.detailError.value, '');
});

test('A→关闭→B，B先返回、A后返回时保持B详情', async t => {
  const { panel, requests } = harness(t);
  const first = panel.open(1);
  panel.drawer.value = false;
  const latest = panel.open(2);
  requests[1].resolve(appointment(2));
  await latest;
  requests[0].resolve(appointment(1));
  await first;
  assert.equal(panel.detail.value.id, 2);
  assert.equal(panel.detailBusy.value, false);
  assert.equal(panel.detailError.value, '');
});

test('旧请求完成时不能提前清除新详情的加载状态', async t => {
  const { panel, requests } = harness(t);
  const older = panel.open(1);
  const latest = panel.open(2);
  requests[0].resolve(appointment(1));
  await older;
  assert.equal(panel.detail.value, null);
  assert.equal(panel.detailBusy.value, true);
  requests[1].resolve(appointment(2));
  await latest;
  assert.equal(panel.detail.value.id, 2);
});

test('旧详情失败不污染新详情错误或加载状态', async t => {
  const { panel, requests } = harness(t);
  const older = panel.open(1);
  panel.drawer.value = false;
  const latest = panel.open(2);
  requests[0].reject(new Error('A请求已失败'));
  await older;
  assert.equal(panel.detailError.value, '');
  assert.equal(panel.detailBusy.value, true);
  requests[1].resolve(appointment(2));
  await latest;
  assert.equal(panel.detail.value.id, 2);
  assert.equal(panel.detailError.value, '');
});

test('关闭抽屉使请求立即失效，迟到失败不会重新显示错误', async t => {
  const { panel, requests } = harness(t);
  const opening = panel.open(1);
  panel.drawer.value = false;
  assert.equal(panel.detailBusy.value, false);
  requests[0].reject(new Error('关闭后的失败'));
  await opening;
  assert.equal(panel.drawer.value, false);
  assert.equal(panel.detail.value, null);
  assert.equal(panel.detailError.value, '');
});

test('关闭后重开相同预约也不能接收上一次打开的旧响应', async t => {
  const { panel, requests } = harness(t);
  const older = panel.open(1);
  panel.drawer.value = false;
  const latest = panel.open(1);
  requests[1].resolve(appointment(1, 'COMPLETED'));
  await latest;
  requests[0].resolve(appointment(1, 'CONFIRMED'));
  await older;
  assert.equal(panel.detail.value.status, 'COMPLETED');
});

test('状态操作的详情刷新已发出后切换到B，刷新结果不能覆盖B', async t => {
  const { panel, requests } = harness(t);
  const opening = panel.open(1);
  requests[0].resolve(appointment(1));
  await opening;
  const operationContext = panel.capture(1);
  const refreshing = panel.refresh(operationContext);
  const latest = panel.open(2);
  requests[2].resolve(appointment(2));
  await latest;
  requests[1].resolve(appointment(1, 'COMPLETED'));
  await refreshing;
  assert.equal(panel.detail.value.id, 2);
  assert.equal(panel.detailBusy.value, false);
});

test('状态操作结束前已切换抽屉，不再为旧记录刷新或写入操作错误', async t => {
  const { panel, requests } = harness(t);
  const first = panel.open(1);
  requests[0].resolve(appointment(1));
  await first;
  const operationContext = panel.capture(1);
  const latest = panel.open(2);
  await panel.refresh(operationContext);
  panel.reportError(operationContext, new Error('A状态操作失败'));
  panel.close(operationContext);
  assert.equal(requests.length, 2);
  assert.equal(panel.drawer.value, true);
  assert.equal(panel.detailBusy.value, true);
  assert.equal(panel.detailError.value, '');
  requests[1].resolve(appointment(2));
  await latest;
  assert.equal(panel.detail.value.id, 2);
});

test('同一抽屉连续刷新仅接收最新请求，旧失败和finally均被忽略', async t => {
  const { panel, requests } = harness(t);
  const opening = panel.open(1);
  requests[0].resolve(appointment(1));
  await opening;
  const context = panel.capture(1);
  const older = panel.refresh(context);
  const latest = panel.refresh(context);
  requests[1].reject(new Error('较早的刷新失败'));
  await older;
  assert.equal(panel.detailBusy.value, true);
  assert.equal(panel.detailError.value, '');
  requests[2].resolve(appointment(1, 'COMPLETED'));
  await latest;
  assert.equal(panel.detail.value.status, 'COMPLETED');
  assert.equal(panel.detailBusy.value, false);
});

test('当前详情失败仍正常显示错误，重试成功会清除错误', async t => {
  const { panel, requests } = harness(t);
  const opening = panel.open(1);
  requests[0].reject(new Error('当前预约加载失败'));
  await opening;
  assert.equal(panel.detailError.value, '当前预约加载失败');
  assert.equal(panel.detailBusy.value, false);
  const retry = panel.refresh(panel.capture(1));
  assert.equal(panel.detailError.value, '');
  requests[1].resolve(appointment(1));
  await retry;
  assert.equal(panel.detail.value.id, 1);
  panel.reportError(panel.capture(1), new Error('当前状态操作失败'));
  assert.equal(panel.detailError.value, '当前状态操作失败');
});

test('页面卸载使尚未完成的详情和操作上下文失效', async t => {
  const { panel, requests, scope } = harness(t);
  const opening = panel.open(1);
  const context = panel.capture(1);
  scope.stop();
  requests[0].resolve(appointment(1));
  await opening;
  panel.reportError(context, new Error('卸载后的操作失败'));
  assert.equal(panel.detail.value, null);
  assert.equal(panel.detailBusy.value, false);
  assert.equal(panel.detailError.value, '');
});
