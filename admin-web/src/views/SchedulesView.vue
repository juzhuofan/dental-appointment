<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { Plus, Refresh, Calendar } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { usePagedList } from '../composables/usePagedList';
import type { Clinic, Doctor, PageResult, Schedule, ScheduleStatus } from '../types';
import { clinicInput, errorText, formatTime, isDismissed, scheduleLabels, toApiTime } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatusTag from '../components/StatusTag.vue';
const auth = useAuthStore();
const doctors = ref<Doctor[]>([]);
const cancelDefault = ref(120);
const filters = reactive({ doctorId: '' as number | string, status: '', dates: [] as string[] });
const { records, paging, loading, error, load } = usePagedList<Schedule>('/admin/schedules', () => ({
  doctorId: filters.doctorId || undefined, status: filters.status || undefined, dateFrom: filters.dates?.[0] || undefined, dateTo: filters.dates?.[1] || undefined,
}));
const form = reactive({ doctorId: '' as number | string, startTime: '', endTime: '', totalSlots: 10, status: 'DRAFT' as ScheduleStatus, cancelBeforeMinutes: 120 });
const formRef = ref<FormInstance>();
const rules: FormRules = { doctorId: [{ required: true, message: '请选择接诊医生', trigger: 'change' }], startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }], endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }], totalSlots: [{ required: true, type: 'number', min: 1, max: 1000, message: '号源总量为 1–1000', trigger: 'change' }] };
const visible = ref(false);
const saving = ref(false);
const saveError = ref('');
const editing = ref<Schedule | null>(null);
const changing = ref<number | null>(null);
async function loadDoctors(): Promise<void> {
  if (!auth.isAdmin) return;
  const previousDefault = cancelDefault.value;
  try {
    const result = await Promise.all([api.get<PageResult<Doctor>>('/admin/doctors', { size: 100, status: 1 }), api.get<Clinic>('/admin/system-config')]);
    doctors.value = result[0].records;
    cancelDefault.value = result[1].cancelBeforeMinutes;
    if (visible.value && !editing.value && form.cancelBeforeMinutes === previousDefault) form.cancelBeforeMinutes = cancelDefault.value;
  } catch (cause) { saveError.value = errorText(cause); }
}
function open(row?: Schedule): void {
  editing.value = row || null; saveError.value = '';
  Object.assign(form, { doctorId: row?.doctorId || '', startTime: clinicInput(row?.startTime) || '', endTime: clinicInput(row?.endTime) || '', totalSlots: row?.totalSlots || 10, status: row?.status || 'DRAFT', cancelBeforeMinutes: row?.cancelBeforeMinutes ?? cancelDefault.value });
  visible.value = true; formRef.value?.clearValidate(); void loadDoctors();
}
async function save(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return;
  if (form.endTime <= form.startTime) { saveError.value = '结束时间必须晚于开始时间'; return; }
  if (editing.value && form.totalSlots < editing.value.bookedSlots) { saveError.value = '号源总量不能小于已经占用的号源'; return; }
  if (!editing.value && new Date(toApiTime(form.startTime)!).getTime() <= Date.now()) { saveError.value = '请创建未来的就诊时段'; return; }
  saving.value = true; saveError.value = '';
  try {
    const dto = { ...form, doctorId: Number(form.doctorId), startTime: toApiTime(form.startTime), endTime: toApiTime(form.endTime) };
    if (editing.value) await api.put(`/admin/schedules/${editing.value.id}`, dto); else await api.post('/admin/schedules', dto);
    ElMessage.success('排班已保存'); visible.value = false; await load();
  } catch (cause) { saveError.value = errorText(cause); } finally { saving.value = false; }
}
async function changeStatus(row: Schedule, status: ScheduleStatus): Promise<void> {
  try {
    const text = status === 'CANCELLED' ? '停诊会取消该时段内所有待确认和已确认预约，并释放号源。' : status === 'CLOSED' ? '关闭后患者不能提交新预约，已存在的预约仍然保留。' : '发布后患者可以在小程序中查询和预约这个时段。';
    await ElMessageBox.confirm(text, `${scheduleLabels[status]}排班`, { type: status === 'CANCELLED' ? 'warning' : 'info', confirmButtonText: '确认', cancelButtonText: '取消' });
    changing.value = row.id;
    await api.patch(`/admin/schedules/${row.id}/status`, { status }); ElMessage.success('排班状态已更新'); await load();
  } catch (cause) { if (!isDismissed(cause)) saveError.value = errorText(cause); } finally { changing.value = null; }
}
async function remove(row: Schedule): Promise<void> {
  try {
    await ElMessageBox.confirm('删除排班会取消关联的活动预约，并从常规列表中隐藏该排班。历史记录会保留。', '删除排班', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' });
    changing.value = row.id;
    await api.delete(`/admin/schedules/${row.id}`); ElMessage.success('排班已删除'); await load();
  } catch (cause) { if (!isDismissed(cause)) saveError.value = errorText(cause); } finally { changing.value = null; }
}
function reset(): void { filters.doctorId = ''; filters.status = ''; filters.dates = []; void load(true); }
onMounted(() => { void load(); void loadDoctors(); });
</script>
<template>
  <PageHeading :title="auth.isDoctor ? '我的排班' : '医生排班'" :description="auth.isDoctor ? '查看本人接诊时段与预约余量。排班调整由诊所管理员处理。' : '以就诊时段管理号源，合理安排医生的门诊时间。'"><el-button v-if="auth.isAdmin" type="primary" :icon="Plus" @click="open()">新增排班</el-button></PageHeading>
  <div class="info-strip"><el-icon><Calendar /></el-icon><span>排班发布后开放预约；关闭仅停止新预约；停诊会同时取消该时段内的活动预约。</span></div>
  <section class="panel list-panel"><div class="filter-bar"><el-select v-if="auth.isAdmin" v-model="filters.doctorId" placeholder="全部医生" clearable filterable class="filter-select"><el-option v-for="doctor in doctors" :key="doctor.id" :label="doctor.name" :value="doctor.id"/></el-select><el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-select"><el-option v-for="(label, value) in scheduleLabels" :key="value" :label="label" :value="value"/></el-select><el-date-picker v-model="filters.dates" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" class="filter-range"/><el-button type="primary" @click="load(true)">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert"/><div class="table-caption"><span>排班时段 <b>{{ paging.total }}</b></span><el-button text :loading="loading" @click="load()">刷新列表</el-button></div><el-table :data="records" v-loading="loading" row-key="id" empty-text="暂无排班，创建并发布就诊时段后即可接受预约"><el-table-column prop="doctorName" label="接诊医生" min-width="110"/><el-table-column prop="departmentName" label="科室" min-width="125"/><el-table-column label="就诊时段" min-width="245"><template #default="{ row }"><div class="cell-emphasis">{{ formatTime(row.startTime) }}</div><div class="cell-secondary">至 {{ formatTime(row.endTime) }}</div></template></el-table-column><el-table-column label="号源情况" min-width="165"><template #default="{ row }"><div class="slot-summary"><strong>{{ row.remainingSlots ?? row.totalSlots - row.bookedSlots }}</strong><span>可约 / 共 {{ row.totalSlots }} 号</span></div><el-progress :percentage="row.totalSlots ? Math.round(row.bookedSlots / row.totalSlots * 100) : 0" :stroke-width="5" :show-text="false" color="#2a9a8e"/></template></el-table-column><el-table-column label="状态" width="105"><template #default="{ row }"><StatusTag :value="row.status" kind="schedule"/></template></el-table-column><el-table-column v-if="auth.isAdmin" label="操作" width="310" fixed="right"><template #default="{ row }"><el-button text type="primary" :disabled="changing === row.id" @click="open(row)">编辑</el-button><el-button v-if="row.status === 'DRAFT' || row.status === 'CLOSED'" text type="success" :disabled="changing === row.id" @click="changeStatus(row, 'PUBLISHED')">发布</el-button><el-button v-if="row.status === 'PUBLISHED'" text type="warning" :disabled="changing === row.id" @click="changeStatus(row, 'CLOSED')">关闭</el-button><el-button v-if="row.status !== 'CANCELLED'" text type="danger" :disabled="changing === row.id" @click="changeStatus(row, 'CANCELLED')">停诊</el-button><el-button text type="danger" :disabled="changing === row.id" @click="remove(row)">删除</el-button></template></el-table-column></el-table><div class="pagination-row"><span class="muted">共 {{ paging.total }} 条记录</span><el-pagination v-model:current-page="paging.page" v-model:page-size="paging.size" :total="paging.total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div></section>
  <el-dialog v-model="visible" :title="editing ? '编辑排班' : '新增医生排班'" width="560px" :close-on-click-modal="false" :before-close="(done: () => void) => { if (!saving) done(); }" destroy-on-close><el-alert v-if="editing && editing.bookedSlots > 0" title="此排班已有活动预约，医生和就诊时间不可更改。" type="info" :closable="false" show-icon class="section-alert"/><el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="section-alert"/><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="接诊医生" prop="doctorId"><el-select v-model="form.doctorId" filterable placeholder="选择医生" :disabled="!!editing?.bookedSlots" class="full-width"><el-option v-for="doctor in doctors" :key="doctor.id" :label="`${doctor.name} · ${doctor.departmentName}`" :value="doctor.id"/></el-select></el-form-item><div class="form-grid"><el-form-item label="开始时间（北京时间）" prop="startTime"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="选择开始时间" :disabled="!!editing?.bookedSlots" class="full-width"/></el-form-item><el-form-item label="结束时间（北京时间）" prop="endTime"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="选择结束时间" :disabled="!!editing?.bookedSlots" class="full-width"/></el-form-item></div><div class="form-grid"><el-form-item label="号源总量" prop="totalSlots"><el-input-number v-model="form.totalSlots" :min="Math.max(1, editing?.bookedSlots || 1)" :max="1000" class="full-width"/></el-form-item><el-form-item label="患者取消提前量（分钟）"><el-input-number v-model="form.cancelBeforeMinutes" :min="0" :max="10080" class="full-width"/></el-form-item></div><el-form-item label="排班状态"><el-select v-model="form.status" class="full-width" :disabled="!!editing"><el-option v-for="(label, value) in scheduleLabels" v-show="editing || value === 'DRAFT' || value === 'PUBLISHED'" :key="value" :label="label" :value="value"/></el-select><div class="field-note">已有排班请在列表中使用发布、关闭或停诊操作改变状态。</div></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存排班</el-button></template></el-dialog>
</template>
