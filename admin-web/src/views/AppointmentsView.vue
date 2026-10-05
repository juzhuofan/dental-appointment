<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Refresh } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { usePagedList } from '../composables/usePagedList';
import type { Appointment, AppointmentStatus, Department, Doctor, PageResult } from '../types';
import { appointmentLabels, errorText, formatTime, isDismissed } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatusTag from '../components/StatusTag.vue';
const auth = useAuthStore();
const route = useRoute();
const doctors = ref<Doctor[]>([]);
const departments = ref<Department[]>([]);
const filters = reactive({ keyword: '', status: String(route.query.status || ''), doctorId: '' as string | number, departmentId: '' as string | number, dates: [] as string[] });
const { records, paging, loading, error, load } = usePagedList<Appointment>('/admin/appointments', () => ({
  keyword: filters.keyword.trim() || undefined, status: filters.status || undefined, doctorId: filters.doctorId || undefined,
  departmentId: filters.departmentId || undefined, dateFrom: filters.dates?.[0] || undefined, dateTo: filters.dates?.[1] || undefined,
}));
const drawer = ref(false);
const detail = ref<Appointment | null>(null);
const detailBusy = ref(false);
const detailError = ref('');
const changing = ref<number | null>(null);
async function loadOptions(): Promise<void> {
  if (!auth.isAdmin) return;
  try {
    const results = await Promise.all([api.get<PageResult<Doctor>>('/admin/doctors', { size: 100 }), api.get<PageResult<Department>>('/admin/departments', { size: 100 })]);
    doctors.value = results[0].records; departments.value = results[1].records;
  } catch { /* 请求层已展示错误，预约列表仍可独立使用。 */ }
}
async function openDetail(row: Appointment): Promise<void> {
  drawer.value = true; detail.value = null; detailBusy.value = true; detailError.value = '';
  try { detail.value = await api.get<Appointment>(`/appointments/${row.id}`); }
  catch (cause) { detailError.value = errorText(cause); }
  finally { detailBusy.value = false; }
}
function actions(row: Appointment): AppointmentStatus[] {
  if (row.status === 'PENDING' && auth.isAdmin) return ['CONFIRMED', 'CANCELLED'];
  if (row.status === 'CONFIRMED') return auth.isAdmin ? ['COMPLETED', 'NO_SHOW', 'CANCELLED'] : ['COMPLETED', 'NO_SHOW'];
  return [];
}
async function changeStatus(row: Appointment, status: AppointmentStatus): Promise<void> {
  try {
    let reason: string | undefined;
    if (status === 'CANCELLED') {
      const result = await ElMessageBox.prompt('取消将释放号源，请填写原因，患者可以查看该说明。', '取消预约', {
        type: 'warning', confirmButtonText: '确认取消预约', cancelButtonText: '返回',
        inputPlaceholder: '请输入取消原因', inputValidator: (value: string) => value?.trim() && value.trim().length <= 255 ? true : '原因必填，最多 255 字',
      });
      reason = result.value.trim();
    } else {
      const text = status === 'CONFIRMED' ? '确认后患者可按预约时间到诊。' : status === 'COMPLETED' ? '确认患者已经完成此次接诊？完成后不可再次修改状态。' : '确认患者未按时到诊？登记爽约后不可再次修改状态。';
      await ElMessageBox.confirm(text, `${appointmentLabels[status]}预约`, { type: status === 'NO_SHOW' ? 'warning' : 'info', confirmButtonText: '确认', cancelButtonText: '取消' });
    }
    changing.value = row.id;
    if (status === 'CANCELLED') await api.post(`/appointments/${row.id}/cancel`, { reason });
    else await api.patch(`/admin/appointments/${row.id}/status`, { status, reason });
    ElMessage.success(`预约已更新为「${appointmentLabels[status]}」`);
    await load();
    if (detail.value?.id === row.id) detail.value = await api.get<Appointment>(`/appointments/${row.id}`);
  } catch (cause) { if (!isDismissed(cause) && drawer.value) detailError.value = errorText(cause); }
  finally { changing.value = null; }
}
async function remove(row: Appointment): Promise<void> {
  try {
    await ElMessageBox.confirm('此操作会隐藏预约记录；如预约仍有效，会先取消并释放号源。历史数据与操作记录保留。', '删除预约', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' });
    changing.value = row.id;
    await api.delete(`/admin/appointments/${row.id}`); ElMessage.success('预约已删除');
    if (detail.value?.id === row.id) drawer.value = false;
    await load();
  } catch (cause) { if (!isDismissed(cause) && drawer.value) detailError.value = errorText(cause); }
  finally { changing.value = null; }
}
function reset(): void { filters.keyword = ''; filters.status = ''; filters.doctorId = ''; filters.departmentId = ''; filters.dates = []; void load(true); }
onMounted(() => { void load(); void loadOptions(); });
</script>
<template>
  <PageHeading :title="auth.isDoctor ? '我的接诊' : '预约管理'" :description="auth.isDoctor ? '查看分配给本人的患者预约，完成接诊后及时更新状态。' : '从预约确认到完成接诊，让每一位患者的就诊进度清楚可见。'"><el-button :icon="Refresh" :loading="loading" @click="load()">刷新预约</el-button></PageHeading>
  <div class="status-tabs"><button :class="{ selected: filters.status === '' }" @click="filters.status = ''; load(true)">全部预约</button><button v-for="(label, value) in appointmentLabels" :key="value" :class="{ selected: filters.status === value }" @click="filters.status = value; load(true)">{{ label }}</button></div>
  <section class="panel list-panel"><div class="filter-bar"><el-input v-model="filters.keyword" placeholder="搜索预约编号、患者姓名或手机号" :prefix-icon="Search" clearable class="filter-search" @keyup.enter="load(true)"/><el-select v-if="auth.isAdmin" v-model="filters.departmentId" placeholder="全部科室" filterable clearable class="filter-select"><el-option v-for="department in departments" :key="department.id" :label="department.name" :value="department.id"/></el-select><el-select v-if="auth.isAdmin" v-model="filters.doctorId" placeholder="全部医生" filterable clearable class="filter-select"><el-option v-for="doctor in doctors" :key="doctor.id" :label="doctor.name" :value="doctor.id"/></el-select><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="就诊开始日期" end-placeholder="结束日期" range-separator="至" class="filter-range"/><el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button></div><el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="section-alert"/><div class="table-caption"><span>{{ filters.status ? appointmentLabels[filters.status as AppointmentStatus] : '全部' }}预约 <b>{{ paging.total }}</b></span><span class="muted">预约状态变化会同步到患者小程序</span></div><el-table :data="records" v-loading="loading" row-key="id" empty-text="暂无符合条件的预约"><el-table-column label="预约编号" min-width="185"><template #default="{ row }"><button class="table-link" @click="openDetail(row)">{{ row.appointmentNo }}</button><div class="cell-secondary">{{ formatTime(row.createdAt) }} 提交</div></template></el-table-column><el-table-column label="患者" min-width="140"><template #default="{ row }"><span class="cell-emphasis">{{ row.patientName }}</span><div class="cell-secondary">{{ row.patientPhone }}</div></template></el-table-column><el-table-column label="接诊医生 / 科室" min-width="155"><template #default="{ row }">{{ row.doctorName }}<div class="cell-secondary">{{ row.departmentName }}</div></template></el-table-column><el-table-column label="就诊时间" min-width="175"><template #default="{ row }">{{ formatTime(row.startTime) }}<div class="cell-secondary">至 {{ formatTime(row.endTime, true) }}</div></template></el-table-column><el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status"/></template></el-table-column><el-table-column label="操作" :width="auth.isAdmin ? 240 : 180" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="openDetail(row)">详情</el-button><el-dropdown v-if="actions(row).length" @command="(status: AppointmentStatus) => changeStatus(row, status)"><el-button text type="primary" :loading="changing === row.id">处理</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="status in actions(row)" :key="status" :command="status">{{ status === 'CONFIRMED' ? '确认预约' : status === 'COMPLETED' ? '完成接诊' : status === 'NO_SHOW' ? '登记爽约' : '取消预约' }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><el-button v-if="auth.isAdmin" text type="danger" :disabled="changing === row.id" @click="remove(row)">删除</el-button></template></el-table-column></el-table><div class="pagination-row"><span class="muted">共 {{ paging.total }} 条记录</span><el-pagination v-model:current-page="paging.page" v-model:page-size="paging.size" :total="paging.total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div></section>
  <el-drawer v-model="drawer" title="预约详情" size="520px"><div v-loading="detailBusy" class="detail-drawer"><el-alert v-if="detailError" :title="detailError" type="error" :closable="false" show-icon class="section-alert"/><template v-if="detail"><div class="detail-header"><span class="eyebrow">APPOINTMENT</span><h2>{{ detail.appointmentNo }}</h2><StatusTag :value="detail.status"/></div><div class="detail-section"><h3>患者信息</h3><dl><dt>就诊人</dt><dd>{{ detail.patientName }}</dd><dt>联系电话</dt><dd>{{ detail.patientPhone }}</dd><dt>就诊描述</dt><dd class="preserve-lines">{{ detail.chiefComplaint || '患者未填写' }}</dd></dl></div><div class="detail-section"><h3>就诊安排</h3><dl><dt>接诊医生</dt><dd>{{ detail.doctorName }}</dd><dt>所属科室</dt><dd>{{ detail.departmentName }}</dd><dt>开始时间</dt><dd>{{ formatTime(detail.startTime) }}</dd><dt>结束时间</dt><dd>{{ formatTime(detail.endTime) }}</dd><dt>提交时间</dt><dd>{{ formatTime(detail.createdAt) }}</dd></dl></div><div v-if="detail.status === 'CANCELLED'" class="detail-section"><h3>取消信息</h3><dl><dt>取消原因</dt><dd>{{ detail.cancelReason || '—' }}</dd><dt>取消时间</dt><dd>{{ formatTime(detail.cancelledAt) }}</dd></dl></div><div class="detail-actions"><el-button v-for="status in actions(detail)" :key="status" :type="status === 'CANCELLED' || status === 'NO_SHOW' ? 'danger' : 'primary'" :plain="status !== 'CONFIRMED' && status !== 'COMPLETED'" :loading="changing === detail.id" @click="changeStatus(detail, status)">{{ status === 'CONFIRMED' ? '确认预约' : status === 'COMPLETED' ? '完成接诊' : status === 'NO_SHOW' ? '登记爽约' : '取消预约' }}</el-button></div></template><el-empty v-else-if="!detailBusy && !detailError" description="没有找到预约记录"/></div></el-drawer>
</template>
