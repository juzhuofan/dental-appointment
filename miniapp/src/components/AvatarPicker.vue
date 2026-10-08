<template>
  <view v-if="visible" class="avatar-mask">
    <view class="avatar-panel">
      <view class="avatar-title">更换头像</view>
      <view class="avatar-description">头像由你选择，保存后会展示在个人中心。</view>
      <view v-if="error" class="avatar-error">{{ error }}</view>
      <view v-if="checking" class="avatar-description">正在检查隐私授权…</view>
      <template v-else-if="authState.privacyRequired">
        <view class="avatar-description">选择头像前，请阅读并同意微信隐私保护指引。</view>
        <view class="privacy-link" @click="openPrivacyContract">查看{{ authState.privacyContractName || '隐私保护指引' }}</view>
        <!-- #ifdef MP-WEIXIN -->
        <button id="avatar-privacy-agree" class="btn btn-primary" open-type="agreePrivacyAuthorization"
          @agreeprivacyauthorization="agreePrivacy">同意并继续</button>
        <!-- #endif -->
      </template>
      <template v-else-if="!privacyError">
        <!-- #ifdef MP-WEIXIN -->
        <button class="btn btn-primary" open-type="chooseAvatar" :loading="saving" :disabled="saving"
          @chooseavatar="chooseAvatar">{{ saving ? '正在保存头像…' : '选择头像' }}</button>
        <!-- #endif -->
      </template>
      <button v-if="privacyError" class="btn btn-outline" @click="loadPrivacy">重新检查</button>
      <button class="btn btn-outline close-button" :disabled="saving" @click="emit('close')">关闭</button>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue';
import { authState, hasSession } from '../utils/session';
import { refreshPrivacyRequirement, uploadAvatar } from '../utils/auth';
import { completePrivacyAuthorization, isWechatMiniProgram, openPrivacyContract } from '../utils/wechat';

const props = defineProps({ visible: { type: Boolean, default: false } });
const emit = defineEmits(['close', 'success']);
const checking = ref(false);
const saving = ref(false);
const error = ref('');
const privacyError = ref(false);

async function loadPrivacy() {
  checking.value = true;
  privacyError.value = false;
  error.value = '';
  try {
    if (!isWechatMiniProgram()) throw new Error('请在微信小程序中更换头像');
    await refreshPrivacyRequirement();
  } catch (detail) {
    error.value = detail.message;
    privacyError.value = true;
  } finally { checking.value = false; }
}

watch(() => props.visible, visible => { if (visible) loadPrivacy(); }, { immediate: true });

async function agreePrivacy() {
  completePrivacyAuthorization('avatar-privacy-agree');
  await loadPrivacy();
}

async function chooseAvatar(event) {
  if (saving.value || checking.value || privacyError.value || authState.privacyRequired) return;
  const avatarPath = event.detail?.avatarUrl;
  if (!avatarPath) return;
  if (!hasSession()) { error.value = '登录状态已失效，请重新登录后更换头像'; return; }
  saving.value = true;
  error.value = '';
  try {
    const user = await uploadAvatar(avatarPath);
    emit('success', user);
    emit('close');
    uni.showToast({ title: '头像已保存', icon: 'success' });
  } catch (detail) { error.value = detail.message || '头像未保存，请重试'; }
  finally { saving.value = false; }
}
</script>

<style scoped>
.avatar-mask { position: fixed; inset: 0; z-index: 950; display: flex; align-items: center; justify-content: center; padding: 45rpx; background: rgba(20, 46, 36, .48); }
.avatar-panel { width: 100%; padding: 38rpx 32rpx; background: #fff; border-radius: 28rpx; box-sizing: border-box; text-align: center; }
.avatar-title { font-size: 34rpx; font-weight: 600; color: #244c3c; }
.avatar-description { margin: 20rpx 0 26rpx; font-size: 25rpx; line-height: 1.7; color: #799082; }
.avatar-error { padding: 16rpx; margin: 22rpx 0; border-radius: 12rpx; background: #fff0ec; color: #a55a45; font-size: 24rpx; line-height: 1.6; }
.privacy-link { margin: 18rpx 0; color: #12836d; font-size: 25rpx; }
.close-button { margin-top: 18rpx; }
</style>
