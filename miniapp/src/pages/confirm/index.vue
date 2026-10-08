<template>
  <view class="page page--bottom"><view class="eyebrow">CONFIRM APPOINTMENT</view><view class="title top-space">确认你的就诊安排</view><view v-if="loading" class="loading">正在核对号源与资料…</view><view v-else-if="error" class="error-card top-space">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view><template v-else-if="schedule"><view class="card section"><view class="row between"><text class="section-title">{{ schedule.doctorName }}</text><text class="pill">{{ schedule.departmentName }}</text></view><view class="divider" /><view class="info-row"><text class="info-label">就诊日期</text><text class="info-value strong">{{ dateText(schedule.startTime, true) }}</text></view><view class="info-row"><text class="info-label">预约时段</text><text class="info-value strong">{{ timeRange(schedule) }}</text></view><view class="info-row"><text class="info-label">剩余号源</text><text class="info-value">{{ schedule.remainingSlots }} 个</text></view></view><view class="section"><text class="section-title">选择就诊人</text><view class="muted top-space">同一账号可管理多位就诊人；预约将记录所选就诊人的资料</view><picker :range="patientLabels" :value="selectedIndex" :disabled="fieldsLocked" @change="selectPatient"><view class="input patient-select">{{ patientLabels[selectedIndex] || "选择就诊人" }} <text class="muted">›</text></view></picker><view class="link top-space" @click="managePatients">管理就诊人</view><view class="card top-space"><view class="field"><text class="field-label">就诊人姓名 *</text><input v-model="profile.realName" :disabled="fieldsLocked" class="input" maxlength="80" placeholder="请输入就诊人真实姓名" /></view><view class="field"><text class="field-label">联系电话 *</text><input v-model="profile.phone" :disabled="fieldsLocked" class="input" type="number" maxlength="11" placeholder="11 位手机号码" /></view><view class="field"><text class="field-label">性别</text><picker :disabled="fieldsLocked" :range="genderLabels" :value="profile.gender || 0" @change="profile.gender = Number($event.detail.value)"><view class="input picker-value">{{ genderLabels[profile.gender || 0] }} <text class="muted">›</text></view></picker></view></view></view><view class="section"><text class="section-title">就诊诉求</text><view class="card top-space"><textarea v-model="chiefComplaint" :disabled="fieldsLocked" class="textarea" maxlength="500" placeholder="例如：希望进行口腔检查（选填，最多 500 字）" /><view class="muted small text-right">{{ chiefComplaint.length }}/500</view></view></view><view class="hint section">提交后进入“待确认”状态。可取消时间：{{ cancellationHint }}。号源以提交时的实际余量为准。</view><view v-if="unknownResult" class="card section"><view class="strong">上次提交结果尚未确认</view><view class="body top-space">请重试确认同一份预约，或先查看“我的预约”。确认前已冻结本次资料，避免改变提交内容。</view><button class="btn btn-outline btn-small top-space" @click="viewAppointments">查看我的预约</button></view><view class="bottom-action"><button class="btn btn-primary" :loading="submitting" :disabled="submitting || (!pendingAttempt && schedule.remainingSlots <= 0)" @click="submit">{{ submitting ? '正在提交预约…' : unknownResult ? '重试确认上次预约结果' : '确认资料，提交预约' }}</button></view></template></view>
</template>
<script setup>
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { ensureLogin } from '../../utils/auth';
import { dateText, timeText, timeRange, profilePayload, validateProfile } from '../../utils/format';
const loading = ref(true); const submitting = ref(false); const error = ref(''); const schedule = ref(null); const profile = ref({ realName: '', phone: '', gender: 0 }); const profiles = ref([]); const selectedId = ref(null); const clinic = ref({ cancelBeforeMinutes: 120 }); const chiefComplaint = ref(''); const genderLabels = ['未填写', '男', '女']; let scheduleId = ''; let doctorId = '';
const patientLabels = computed(() => profiles.value.map(item => `${item.realName || '待填写资料'}${item.isDefault ? '（默认）' : ''}`));
const selectedIndex = computed(() => Math.max(0, profiles.value.findIndex(item => item.id === selectedId.value)));
function selectPatient(event) {
  const chosen = profiles.value[Number(event.detail.value)];
  if (!chosen || fieldsLocked.value) return;
  selectedId.value = chosen.id;
  profile.value = { ...chosen };
}
const pendingAttempt = ref(null);
const unknownResult = ref(false);
const fieldsLocked = computed(() => submitting.value || Boolean(pendingAttempt.value));
const cancellationHint = computed(() => {
  const minutes = schedule.value?.cancelBeforeMinutes ?? clinic.value.cancelBeforeMinutes ?? 120;
  const deadline = new Date(new Date(schedule.value?.startTime).getTime() - minutes * 60000);
  return `${dateText(deadline)} ${timeText(deadline)} 前`;
});
async function load() {
  loading.value = true; error.value = '';
  try {
    const [slot, list, c] = await Promise.all([api.schedule(scheduleId), api.profiles(), api.clinic()]);
    schedule.value = slot;
    profiles.value = list;
    const selected = list.find(item => item.id === selectedId.value) || list.find(item => item.isDefault) || list[0];
    if (!selected) throw new Error('请先到“我的”添加就诊人');
    selectedId.value = selected.id;
    profile.value = { ...selected }; clinic.value = c;
  } catch (e) { error.value = e.message; } finally { loading.value = false; }
}
async function submit() {
  if (submitting.value) return;
  if (!pendingAttempt.value) {
    const invalid = validateProfile(profile.value);
    if (invalid) { uni.showToast({ title: invalid, icon: 'none' }); return; }
  }
  submitting.value = true;
  try {
    if (!pendingAttempt.value) {
      const saved = await api.updateProfile(selectedId.value, profilePayload(profile.value));
      pendingAttempt.value = {
        key: `appointment-${Date.now()}-${Math.random().toString(36).slice(2)}`,
        payload: { scheduleId: Number(scheduleId), patientProfileId: saved.id, chiefComplaint: chiefComplaint.value.trim() }
      };
    }
    // 未知网络结果只重放这一次提交，不修改参数或重新生成幂等键。
    const appointment = await api.createAppointment(pendingAttempt.value.payload, pendingAttempt.value.key);
    unknownResult.value = false;
    uni.redirectTo({ url: `/pages/result/index?id=${appointment.id}` });
  } catch (e) {
    notifyError(e);
    if (!e.status || e.status >= 500) {
      unknownResult.value = Boolean(pendingAttempt.value);
    } else {
      pendingAttempt.value = null;
      unknownResult.value = false;
      if (e.status !== 401) await load();
    }
  } finally { submitting.value = false; }
}
onLoad(async options => { scheduleId = options.scheduleId; doctorId = options.doctorId; if (await ensureLogin(`/pages/confirm/index?scheduleId=${scheduleId}&doctorId=${doctorId}`)) load(); else loading.value = false; });
onShow(() => { if (scheduleId && !loading.value && !pendingAttempt.value) load(); });
const viewAppointments = () => uni.switchTab({ url: '/pages/appointments/index' });
const managePatients = () => { if (!fieldsLocked.value) uni.navigateTo({ url: '/pages/profile/index' }); };
</script>
<style scoped>
.title { font-size: 36rpx; margin-top: 16rpx; }.picker-value, .patient-select { display: flex; align-items: center; justify-content: space-between; }.patient-select { margin-top: 16rpx; }.text-right { text-align: right; }.hint { margin-bottom: 30rpx; }
</style>
