<template>
  <view class="page page--bottom">
    <view class="eyebrow">PATIENT PROFILES</view>
    <view class="title top-space">就诊人管理</view>
    <view class="subtitle">同一微信账号可管理多位就诊人，预约时选择实际就诊者</view>
    <view v-if="loading" class="loading">正在加载就诊人…</view>
    <view v-else-if="error" class="error-card section">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view>
    <template v-else>
      <view v-for="item in profiles" :key="item.id" class="card patient-card section">
        <view class="row between">
          <text class="section-title">{{ item.realName || '待填写资料' }}</text>
          <text v-if="item.isDefault" class="pill">默认</text>
        </view>
        <view class="muted top-space">{{ item.phone || '联系电话未填写' }}</view>
        <view class="row patient-actions">
          <text class="link" @click="edit(item)">编辑资料</text>
          <text v-if="!item.isDefault" class="link" @click="makeDefault(item)">设为默认</text>
          <text v-if="profiles.length > 1" class="link delete-action" @click="remove(item)">删除</text>
        </view>
      </view>
      <button v-if="!editing" class="btn btn-outline section" @click="add">新增就诊人</button>
      <view v-if="editing" class="card section">
        <view class="section-title">{{ draft.id ? '编辑就诊人' : '新增就诊人' }}</view>
        <view class="field"><text class="field-label">就诊人姓名 *</text><input v-model="draft.realName" class="input" maxlength="80" placeholder="请输入真实姓名" /></view>
        <view class="field"><text class="field-label">联系电话 *</text><input v-model="draft.phone" class="input" type="number" maxlength="11" placeholder="请输入 11 位手机号" /></view>
        <view class="field"><text class="field-label">性别</text><picker :range="genderLabels" :value="draft.gender || 0" @change="draft.gender = Number($event.detail.value)"><view class="picker-value">{{ genderLabels[draft.gender || 0] }}<text class="muted">›</text></view></picker></view>
        <view class="field"><text class="field-label">出生日期（选填）</text><view class="row between"><picker class="flex-1" mode="date" :value="draft.birthDate || '2000-01-01'" start="1900-01-01" :end="today" @change="draft.birthDate = $event.detail.value"><view class="picker-value">{{ draft.birthDate || '请选择出生日期' }}<text class="muted">›</text></view></picker><text v-if="draft.birthDate" class="link" @click="draft.birthDate = null">清空</text></view></view>
        <view class="field"><text class="field-label">备注（选填）</text><textarea v-model="draft.remark" class="textarea" maxlength="255" placeholder="就诊相关备注" /></view>
        <button class="btn btn-primary" :loading="saving" :disabled="saving" @click="save">{{ saving ? '正在保存…' : '保存就诊人' }}</button>
        <button class="btn btn-outline cancel-action" :disabled="saving" @click="editing = false">取消</button>
      </view>
      <view class="hint section">账号昵称和头像可在“我的”中单独设置。删除就诊人不会删除历史预约；有未完成预约的就诊人暂不能删除。</view>
    </template>
  </view>
</template>

<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { ensureLogin } from '../../utils/auth';
import { dateKey, profilePayload, validateProfile } from '../../utils/format';

const profiles = ref([]);
const draft = ref(emptyProfile());
const loading = ref(true);
const saving = ref(false);
const editing = ref(false);
const error = ref('');
const genderLabels = ['未填写', '男', '女'];
const today = dateKey(new Date());

function emptyProfile() {
  return { realName: '', phone: '', gender: 0, birthDate: null, remark: '' };
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    profiles.value = await api.profiles();
    if (profiles.value.length === 1 && !profiles.value[0].realName && !editing.value) edit(profiles.value[0]);
  } catch (detail) { error.value = detail.message || '就诊人加载失败'; }
  finally { loading.value = false; }
}

function edit(profile) { draft.value = { ...profile }; editing.value = true; }
function add() { draft.value = emptyProfile(); editing.value = true; }

async function save() {
  if (saving.value) return;
  const invalid = validateProfile(draft.value);
  if (invalid) { uni.showToast({ title: invalid, icon: 'none' }); return; }
  saving.value = true;
  try {
    const payload = profilePayload(draft.value);
    if (draft.value.id) await api.updateProfile(draft.value.id, payload);
    else await api.createProfile(payload);
    editing.value = false;
    await load();
    uni.showToast({ title: '就诊人已保存', icon: 'success' });
  } catch (detail) { notifyError(detail); }
  finally { saving.value = false; }
}

async function makeDefault(profile) {
  try { await api.setDefaultProfile(profile.id); await load(); }
  catch (detail) { notifyError(detail); }
}

function remove(profile) {
  uni.showModal({
    title: '删除就诊人？',
    content: `确认删除${profile.realName || '此就诊人'}的资料？历史预约仍会保留。`,
    success: async result => {
      if (!result.confirm) return;
      try { await api.deleteProfile(profile.id); if (draft.value.id === profile.id) editing.value = false; await load(); }
      catch (detail) { notifyError(detail); }
    }
  });
}

onShow(async () => { if (await ensureLogin('/pages/profile/index')) load(); else loading.value = false; });
</script>

<style scoped>
.title { font-size: 36rpx; margin-top: 16rpx; }
.patient-card { padding: 30rpx; }
.patient-actions { gap: 34rpx; margin-top: 24rpx; font-size: 25rpx; }
.delete-action { color: #ad625a; }
.picker-value { height: 60rpx; display: flex; align-items: center; justify-content: space-between; font-size: 29rpx; }
.cancel-action { margin-top: 16rpx; }
</style>
