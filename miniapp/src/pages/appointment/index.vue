<template>
  <view class="page page--bottom"><view v-if="loading" class="loading">正在加载预约详情…</view><view v-else-if="error" class="error-card">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view><template v-else-if="appointment"><view class="status-banner"><view :class="['status-dot', `dot-${appointment.status}`]" /><view><view class="status-name">{{ statusLabel(appointment.status) }}</view><view class="muted top-space">{{ statusDescriptions[appointment.status] }}</view></view></view><view class="card section"><view class="section-title">就诊安排</view><view class="divider" /><view class="info-row"><text class="info-label">预约日期</text><text class="info-value strong">{{ dateText(appointment.startTime, true) }}</text></view><view class="info-row"><text class="info-label">预约时段</text><text class="info-value strong">{{ timeRange(appointment) }}</text></view><view class="info-row"><text class="info-label">就诊医生</text><text class="info-value">{{ appointment.doctorName }}</text></view><view class="info-row"><text class="info-label">就诊科室</text><text class="info-value">{{ appointment.departmentName }}</text></view><view v-if="clinic.address" class="info-row"><text class="info-label">诊所地址</text><text class="info-value">{{ clinic.address }}</text></view></view><view class="card section"><view class="section-title">预约资料</view><view class="divider" /><view class="info-row"><text class="info-label">就诊人</text><text class="info-value">{{ appointment.patientName }}</text></view><view class="info-row"><text class="info-label">联系电话</text><text class="info-value">{{ appointment.patientPhone }}</text></view><view class="info-row"><text class="info-label">预约编号</text><text class="info-value small">{{ appointment.appointmentNo }}</text></view><view class="info-row"><text class="info-label">提交时间</text><text class="info-value">{{ dateText(appointment.createdAt) }} {{ timeText(appointment.createdAt) }}</text></view><view v-if="appointment.chiefComplaint" class="top-space"><text class="field-label">就诊诉求</text><view class="body">{{ appointment.chiefComplaint }}</view></view><view v-if="appointment.cancelReason" class="top-space"><text class="field-label">取消原因</text><view class="body">{{ appointment.cancelReason }}</view></view></view><view v-if="canCancelStatus(appointment.status)" class="hint section">取消截止时间：{{ cancellationDeadline }}。超过截止时间如需变更，请联系诊所。</view><view v-if="canCancelStatus(appointment.status)" class="bottom-action"><button class="btn btn-danger" :loading="cancelling" :disabled="cancelling" @click="cancel">取消本次预约</button></view></template></view>
</template>
<script setup>
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { ensureLogin } from '../../utils/session';
import { dateText, timeText, timeRange, statusLabel, canCancelStatus } from '../../utils/format';
const appointment = ref(null); const clinic = ref({}); const loading = ref(true); const cancelling = ref(false); const error = ref(''); let id = ''; let initialized = false;
const statusDescriptions = { PENDING: '预约已提交，等待诊所确认', CONFIRMED: '预约已确认，请按时到诊', COMPLETED: '本次接诊已完成，感谢你的信任', CANCELLED: '本次预约已取消，号源已释放', NO_SHOW: '诊所已登记本次未到诊' };
const cancellationDeadline = computed(() => {
  if (!appointment.value) return '以诊所规则为准';
  // 后端若返回单排班取消规则则采用其值，否则展示诊所默认规则；操作最终由后端校验。
  const minutes = appointment.value.cancelBeforeMinutes ?? clinic.value.cancelBeforeMinutes ?? 120;
  const deadline = new Date(new Date(appointment.value.startTime).getTime() - minutes * 60000);
  return `${dateText(deadline)} ${timeText(deadline)}`;
});
async function load() { loading.value = true; error.value = ''; try { const [a, c] = await Promise.all([api.appointment(id), api.clinic()]); appointment.value = a; clinic.value = c; } catch (e) { error.value = e.message; } finally { loading.value = false; } }
function cancel() {
  if (cancelling.value) return;
  uni.showModal({ title: '确定取消这次预约？', content: '取消后会释放号源，如需就诊请重新预约。', editable: true, placeholderText: '取消原因（选填）', confirmText: '确定取消', cancelText: '保留预约', success: async result => { if (!result.confirm) return; cancelling.value = true; try { await api.cancelAppointment(id, result.content?.trim() || '患者主动取消'); uni.showToast({ title: '预约已取消', icon: 'success' }); await load(); } catch (e) { notifyError(e); } finally { cancelling.value = false; } } });
}
onLoad(options => { id = options.id; if (ensureLogin(`/pages/appointment/index?id=${id}`)) { load(); initialized = true; } else loading.value = false; });
onShow(() => { if (initialized && appointment.value) load(); });
</script>
<style scoped>
.status-banner { padding: 16rpx 5rpx 6rpx; display: flex; gap: 25rpx; align-items: center; }.status-name { font-size: 43rpx; font-weight: 600; }.status-dot { width: 24rpx; height: 24rpx; border-radius: 50%; background: #d4aa5c; box-shadow: 0 0 0 13rpx #f7edd9; margin: 0 15rpx; flex-shrink: 0; }.dot-CONFIRMED { background: #12836d; box-shadow: 0 0 0 13rpx #dceee3; }.dot-COMPLETED { background: #6c8ba9; box-shadow: 0 0 0 13rpx #e4edf3; }.dot-CANCELLED,.dot-NO_SHOW { background: #9baa9c; box-shadow: 0 0 0 13rpx #e8ede5; }.status-banner .muted { font-size: 24rpx; margin-top: 13rpx; }.hint { margin-bottom: 20rpx; }
</style>
