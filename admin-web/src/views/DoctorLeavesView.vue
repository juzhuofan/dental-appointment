<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { Plus, Refresh } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { usePagedList } from '../composables/usePagedList';
import type { PageResult } from '../types';
import { errorText, formatTime, isDismissed, toApiTime } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';

type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
interface DoctorLeave {
  id: number; doctorId: number; doctorName: string; startTime: string; endTime: string;
  reason: string; status: LeaveStatus; reviewedBy?: number; reviewRemark?: string;
  reviewedAt?: string; createdAt: string;
}
interface DoctorOption { id: number; name: string; departmentName?: string }
const auth = useAuthStore();
const labels: Record<LeaveStatus, string> = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝', WITHDRAWN: '已撤回' };
const filters = reactive({ status: '' as LeaveStatus | '', doctorId: '' as number | '' });
const { records, paging, loading, error, load } = usePagedList<DoctorLeave>('/admin/doctor-leaves', () => ({
  status: filters.status || undefined, doctorId: auth.isAdmin ? filters.doctorId || undefined : undefined,
}));
const doctors = ref<DoctorOption[]>([]);
const doctorsLoading = ref(false);
const doctorsError = ref('');
const actionError = ref('');
const mutation = ref('');
const canApply = computed(() => auth.isDoctor && !!auth.user?.doctorId);
let disposed = false;
let doctorsSequence = 0;

const applyVisible = ref(false);
const applyForm = reactive({ startTime: '', endTime: '', reason: '' });
const applyFormRef = ref<FormInstance>();
const applyError = ref('');
const applying = computed(() => mutation.value === 'apply');
const applyRules: FormRules = {
  startTime: [{ required: true, message: '请选择请假开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择请假结束时间', trigger: 'change' }],
  reason: [{ required: true, whitespace: true, message: '请填写请假原因', trigger: 'blur' },
    { max: 500, message: '请假原因最多 500 字', trigger: 'blur' }],
};

const reviewVisible = ref(false);
const reviewTarget = ref<DoctorLeave | null>(null);
const reviewForm = reactive({ approved: true, remark: '' });
const reviewFormRef = ref<FormInstance>();
const reviewError = ref('');
const reviewing = computed(() => mutation.value.startsWith('review-'));
const reviewRules = computed<FormRules>(() => ({
  remark: [
    { required: !reviewForm.approved, whitespace: true, message: '拒绝申请时请填写原因', trigger: 'blur' },
    { max: 500, message: '审批说明最多 500 字', trigger: 'blur' },
  ],
}));

async function loadDoctors(): Promise<void> {
  if (!auth.isAdmin || disposed) return;
  const sequence = ++doctorsSequence;
  doctorsLoading.value = true; doctorsError.value = '';
  try {
    const options: DoctorOption[] = [];
    let page = 1;
    while (true) {
      const result = await api.get<PageResult<DoctorOption>>('/admin/doctors', { page, size: 100 });
      if (disposed || sequence !== doctorsSequence) return;
      options.push(...result.records);
      if (!result.records.length || page * 100 >= result.total) break;
      page++;
    }
    doctors.value = options;
  } catch (cause) { if (!disposed && sequence === doctorsSequence) doctorsError.value = errorText(cause); }
  finally { if (!disposed && sequence === doctorsSequence) doctorsLoading.value = false; }
}

function reset(): void { filters.status = ''; filters.doctorId = ''; void load(true); }
function openApply(): void {
  if (!canApply.value || mutation.value || reviewVisible.value) return;
  Object.assign(applyForm, { startTime: '', endTime: '', reason: '' });
  applyError.value = ''; applyVisible.value = true; applyFormRef.value?.clearValidate();
}
async function submitApply(): Promise<void> {
  if (!canApply.value || mutation.value || disposed) return;
  mutation.value = 'apply'; applyError.value = '';
  try {
    if (!await applyFormRef.value?.validate().catch(() => false) || disposed) return;
    const startTime = toApiTime(applyForm.startTime);
    const endTime = toApiTime(applyForm.endTime);
    const start = startTime ? new Date(startTime).getTime() : NaN;
    const end = endTime ? new Date(endTime).getTime() : NaN;
    if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
      applyError.value = '结束时间必须晚于开始时间'; return;
    }
    if (start <= Date.now()) { applyError.value = '请选择未来的请假时间'; return; }
    const reason = applyForm.reason.trim();
    if (!reason || reason.length > 500) { applyError.value = '请假原因需填写 1 至 500 字'; return; }
    await api.post('/admin/doctor-leaves', { startTime, endTime, reason });
    if (disposed) return;
    ElMessage.success('请假申请已提交，等待管理员审批'); applyVisible.value = false;
    await load(true);
  } catch (cause) { if (!disposed) applyError.value = errorText(cause); }
  finally { if (!disposed) mutation.value = ''; }
}

function openReview(row: DoctorLeave, approved: boolean): void {
  if (!auth.isAdmin || row.status !== 'PENDING' || mutation.value || applyVisible.value) return;
  reviewTarget.value = row; Object.assign(reviewForm, { approved, remark: '' });
  reviewError.value = ''; reviewVisible.value = true; reviewFormRef.value?.clearValidate();
}
async function submitReview(): Promise<void> {
  const target = reviewTarget.value;
  if (!auth.isAdmin || !target || target.status !== 'PENDING' || mutation.value || disposed) return;
  mutation.value = `review-${target.id}`; reviewError.value = '';
  try {
    if (!await reviewFormRef.value?.validate().catch(() => false) || disposed) return;
    const approved = reviewForm.approved;
    const remark = reviewForm.remark.trim();
    if ((!approved && !remark) || remark.length > 500) {
      reviewError.value = !approved && !remark ? '拒绝申请时请填写原因' : '审批说明最多 500 字'; return;
    }
    const endpoint = `/admin/doctor-leaves/${target.id}/review`;
    try {
      await api.post(endpoint, { approved, remark, confirmCancelAppointments: false });
    } catch (cause) {
      const conflict = cause as { status?: number; code?: string; message?: string };
      if (!approved || conflict.status !== 409
          || !(conflict.code === 'LEAVE_APPOINTMENT_CONFIRMATION_REQUIRED' || /预约/.test(conflict.message || ''))) throw cause;
      if (disposed) return;
      await ElMessageBox.confirm(`${conflict.message || '请假时段内存在未完成预约。'}\n继续批准会停诊受影响的排班，并取消相关待确认、已确认预约。请确认已安排患者就诊事宜。`, '批准请假并取消预约', {
        type: 'warning', confirmButtonText: '确认批准并取消预约', cancelButtonText: '返回审批',
      });
      if (disposed) return;
      await api.post(endpoint, { approved, remark, confirmCancelAppointments: true });
    }
    if (disposed) return;
    ElMessage.success(approved ? '请假申请已批准' : '请假申请已拒绝'); reviewVisible.value = false;
    await load();
  } catch (cause) { if (!disposed && !isDismissed(cause)) reviewError.value = errorText(cause); }
  finally { if (!disposed) mutation.value = ''; }
}

async function withdraw(row: DoctorLeave): Promise<void> {
  if (!canApply.value || row.doctorId !== auth.user?.doctorId || row.status !== 'PENDING' || mutation.value || disposed) return;
  mutation.value = `withdraw-${row.id}`; actionError.value = '';
  try {
    await ElMessageBox.confirm('撤回后此申请将不再进入审批。如仍需请假，可以重新提交。', '撤回请假申请', {
      type: 'warning', confirmButtonText: '确认撤回', cancelButtonText: '保留申请',
    });
    if (disposed) return;
    await api.post(`/admin/doctor-leaves/${row.id}/withdraw`, {});
    if (disposed) return;
    ElMessage.success('请假申请已撤回'); await load();
  } catch (cause) { if (!disposed && !isDismissed(cause)) actionError.value = errorText(cause); }
  finally { if (!disposed) mutation.value = ''; }
}

function tone(status: LeaveStatus): 'warning' | 'success' | 'danger' | 'info' {
  return status === 'PENDING' ? 'warning' : status === 'APPROVED' ? 'success' : status === 'REJECTED' ? 'danger' : 'info';
}
onMounted(() => { void load(); void loadDoctors(); });
onBeforeUnmount(() => { disposed = true; doctorsSequence++; });
</script>
<template>
  <PageHeading title="请假管理" :description="auth.isAdmin ? '审批医生请假，统筹门诊排班与患者预约。' : '提交本人请假时间，查看审批结果。审批通过后才会调整排班。'">
    <el-button v-if="auth.isDoctor" type="primary" :icon="Plus" :disabled="!canApply || !!mutation" @click="openApply">申请请假</el-button>
    <el-button :icon="Refresh" :loading="loading" :disabled="!!mutation" @click="load()">刷新列表</el-button>
  </PageHeading>
  <el-alert v-if="auth.isDoctor && !canApply" title="当前账号尚未关联医生档案，请联系管理员关联后再申请请假。" type="warning" :closable="false" show-icon class="section-alert"/>
  <div class="info-strip"><span>请假获批后，自动排班会避开请假时间；审批前，现有排班与预约继续有效。</span></div>
  <section class="panel list-panel">
    <div class="filter-bar">
      <el-select v-model="filters.status" clearable placeholder="全部审批状态" class="filter-select"><el-option v-for="(label, value) in labels" :key="value" :label="label" :value="value"/></el-select>
      <el-select v-if="auth.isAdmin" v-model="filters.doctorId" filterable clearable placeholder="全部医生" :loading="doctorsLoading" class="filter-select"><el-option v-for="doctor in doctors" :key="doctor.id" :label="`${doctor.name}${doctor.departmentName ? ` · ${doctor.departmentName}` : ''}`" :value="doctor.id"/></el-select>
      <el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button>
      <el-button v-if="doctorsError" text type="primary" :loading="doctorsLoading" @click="loadDoctors">重新加载医生选项</el-button>
    </div>
    <el-alert v-if="error || actionError || doctorsError" :title="error || actionError || doctorsError" type="error" :closable="false" show-icon class="section-alert"/>
    <div class="table-caption"><span>请假申请 <b>{{ paging.total }}</b></span><span class="muted">{{ auth.isAdmin ? '仅待审批申请可批准或拒绝' : '仅待审批申请可以撤回' }}</span></div>
    <el-table :data="records" v-loading="loading" row-key="id" empty-text="暂无符合条件的请假申请">
      <el-table-column v-if="auth.isAdmin" prop="doctorName" label="请假医生" min-width="120"/>
      <el-table-column label="请假时间（北京时间）" min-width="225"><template #default="{ row }"><span class="cell-emphasis">{{ formatTime(row.startTime) }}</span><div class="cell-secondary">至 {{ formatTime(row.endTime) }}</div></template></el-table-column>
      <el-table-column prop="reason" label="请假原因" min-width="225" show-overflow-tooltip/>
      <el-table-column label="审批状态" width="110"><template #default="{ row }"><el-tag :type="tone(row.status)" effect="light" round>{{ labels[row.status as LeaveStatus] || row.status }}</el-tag></template></el-table-column>
      <el-table-column label="审批说明" min-width="200" show-overflow-tooltip><template #default="{ row }">{{ row.reviewRemark || '—' }}<div v-if="row.reviewedAt" class="cell-secondary">{{ formatTime(row.reviewedAt) }}</div></template></el-table-column>
      <el-table-column label="提交时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" :width="auth.isAdmin ? 160 : 120" fixed="right"><template #default="{ row }"><template v-if="row.status === 'PENDING'"><template v-if="auth.isAdmin"><el-button text type="success" :disabled="!!mutation" @click="openReview(row, true)">批准</el-button><el-button text type="danger" :disabled="!!mutation" @click="openReview(row, false)">拒绝</el-button></template><el-button v-else-if="canApply && row.doctorId === auth.user?.doctorId" text type="warning" :loading="mutation === `withdraw-${row.id}`" :disabled="!!mutation" @click="withdraw(row)">撤回</el-button><span v-else class="muted">—</span></template><span v-else class="muted">—</span></template></el-table-column>
    </el-table>
    <div class="pagination-row"><span class="muted">共 {{ paging.total }} 条记录</span><el-pagination v-model:current-page="paging.page" v-model:page-size="paging.size" :total="paging.total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div>
  </section>

  <el-dialog v-model="applyVisible" title="申请请假" width="560px" :close-on-click-modal="false" :close-on-press-escape="!applying" :before-close="(done: () => void) => { if (!applying) done(); }" destroy-on-close>
    <el-alert v-if="applyError" :title="applyError" type="error" :closable="false" show-icon class="section-alert"/>
    <el-form ref="applyFormRef" :model="applyForm" :rules="applyRules" :disabled="applying" label-position="top">
      <div class="form-grid"><el-form-item label="开始时间（北京时间）" prop="startTime"><el-date-picker v-model="applyForm.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="选择请假开始时间" class="full-width"/></el-form-item><el-form-item label="结束时间（北京时间）" prop="endTime"><el-date-picker v-model="applyForm.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" placeholder="选择请假结束时间" class="full-width"/></el-form-item></div>
      <el-form-item label="请假原因" prop="reason"><el-input v-model="applyForm.reason" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请填写请假原因，供管理员审批"/></el-form-item>
      <div class="field-note">提交后等待管理员审批。待审批期间可以撤回申请。</div>
    </el-form>
    <template #footer><el-button :disabled="applying" @click="applyVisible = false">取消</el-button><el-button type="primary" :loading="applying" @click="submitApply">提交申请</el-button></template>
  </el-dialog>

  <el-dialog v-model="reviewVisible" :title="reviewForm.approved ? '批准请假' : '拒绝请假'" width="560px" :close-on-click-modal="false" :close-on-press-escape="!reviewing" :before-close="(done: () => void) => { if (!reviewing) done(); }" destroy-on-close>
    <div v-if="reviewTarget" class="detail-section"><h3>{{ reviewTarget.doctorName }}的请假申请</h3><dl><dt>请假开始</dt><dd>{{ formatTime(reviewTarget.startTime) }}</dd><dt>请假结束</dt><dd>{{ formatTime(reviewTarget.endTime) }}</dd><dt>请假原因</dt><dd class="preserve-lines">{{ reviewTarget.reason }}</dd></dl></div>
    <el-alert v-if="reviewError" :title="reviewError" type="error" :closable="false" show-icon class="section-alert"/>
    <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" :disabled="reviewing" label-position="top"><el-form-item :label="reviewForm.approved ? '审批说明（选填）' : '拒绝原因'" prop="remark"><el-input v-model="reviewForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit :placeholder="reviewForm.approved ? '如有补充安排，请在此说明' : '请说明拒绝原因，医生可以查看'"/></el-form-item></el-form>
    <div v-if="reviewForm.approved" class="field-note">批准会停诊与请假时间重叠的排班。如涉及未完成预约，系统会再次要求确认。</div>
    <template #footer><el-button :disabled="reviewing" @click="reviewVisible = false">返回列表</el-button><el-button :type="reviewForm.approved ? 'primary' : 'danger'" :loading="reviewing" @click="submitReview">{{ reviewForm.approved ? '确认批准' : '确认拒绝' }}</el-button></template>
  </el-dialog>
</template>
