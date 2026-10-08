<template>
  <view v-if="visible" :class="['login-wrapper', { 'login-mask': modal }]">
    <view class="login-panel">
      <button class="close-button" :disabled="authState.busy" @click="close">×</button>
      <view class="login-mark">+</view>
      <view class="login-heading">欢迎来到微笑口腔</view>
      <view class="login-description">微信登录后，预约与就诊资料会保留在你的账号中</view>
      <view v-if="!authState.ready" class="login-hint">正在检查登录状态…</view>
      <view v-if="!wechatSupported" class="login-hint">请在微信小程序中登录，网页端可浏览诊所信息。</view>
      <view v-if="errorMessage || authState.error" class="login-error">{{ errorMessage || authState.error }}</view>
      <view v-if="authState.ready && (authState.error || privacyError)" class="retry-link" @click="retry">重新确认登录状态</view>
      <template v-if="wechatSupported && authState.ready && authState.config.enabled">
        <view v-if="privacyChecking" class="login-hint">正在读取微信隐私授权状态…</view>
        <view v-else-if="authState.privacyRequired" class="privacy-card">
          <view class="login-description">微信要求使用授权能力前同意隐私保护指引，请阅读后继续。</view>
          <view class="retry-link" @click="openPrivacyContract">查看{{ authState.privacyContractName || '隐私保护指引' }}</view>
          <!-- #ifdef MP-WEIXIN -->
          <button id="wechat-privacy-agree" class="btn btn-primary" open-type="agreePrivacyAuthorization"
            @agreeprivacyauthorization="agreePrivacy">同意并继续</button>
          <!-- #endif -->
        </view>
        <template v-else-if="!privacyError">
          <view class="login-hint">{{ authState.config.phoneNumberEnabled ? '使用当前微信身份登录，并由微信确认你授权的手机号。' : '使用当前微信账号登录，联系电话可在就诊资料中填写。' }}</view>
          <!-- #ifdef MP-WEIXIN -->
          <button v-if="authState.config.phoneNumberEnabled" class="btn btn-primary login-submit"
            open-type="getPhoneNumber" :loading="authState.busy" :disabled="!canLogin" @getphonenumber="phoneLogin">
            {{ authState.busy ? '正在登录…' : '微信一键登录' }}
          </button>
          <button v-else class="btn btn-primary login-submit" :loading="authState.busy" :disabled="!canLogin" @click="login()">
            {{ authState.busy ? '正在登录…' : '微信一键登录' }}
          </button>
          <!-- #endif -->
        </template>
      </template>
      <button class="btn btn-outline phone-login" disabled>手机号登录（暂未开放）</button>
      <view class="browse-link" @click="close">暂不登录，先浏览诊所 ›</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { authState, getUser } from '../utils/session';
import { bootstrapAuth, dismissLoginDialog, loginWithWechat, loginWithWechatPhoneAuthorization, refreshPrivacyRequirement } from '../utils/auth';
import { completePrivacyAuthorization, isWechatMiniProgram, openPrivacyContract } from '../utils/wechat';

const props = defineProps({ visible: { type: Boolean, default: true }, modal: { type: Boolean, default: true } });
const emit = defineEmits(['close', 'success']);
const errorMessage = ref('');
const privacyError = ref(false);
const privacyChecking = ref(true);
const wechatSupported = isWechatMiniProgram();
const canLogin = computed(() => authState.ready && !authState.busy && !privacyChecking.value
  && !privacyError.value && !authState.privacyRequired);

async function loadPrivacy() {
  if (!wechatSupported || !authState.config.phoneNumberEnabled) {
    privacyChecking.value = false;
    privacyError.value = false;
    return;
  }
  privacyChecking.value = true;
  privacyError.value = false;
  try { await refreshPrivacyRequirement(); }
  catch (error) { errorMessage.value = error.message; privacyError.value = true; }
  finally { privacyChecking.value = false; }
}

watch(() => props.visible, async visible => {
  if (!visible) return;
  await bootstrapAuth();
  await loadPrivacy();
}, { immediate: true });

async function retry() {
  if (authState.busy) return;
  errorMessage.value = '';
  await bootstrapAuth({ retry: true });
  await loadPrivacy();
  if (authState.status === 'authenticated') emit('success', getUser());
}

async function agreePrivacy() {
  completePrivacyAuthorization('wechat-privacy-agree');
  errorMessage.value = '';
  privacyChecking.value = true;
  privacyError.value = false;
  try { await refreshPrivacyRequirement(); }
  catch (error) { errorMessage.value = error.message; privacyError.value = true; }
  finally { privacyChecking.value = false; }
}

async function phoneLogin(event) {
  if (!canLogin.value) return;
  errorMessage.value = '';
  try {
    const user = await loginWithWechatPhoneAuthorization(event);
    if (user) emit('success', user);
  } catch (error) { errorMessage.value = error.message || '微信登录未完成，请重试'; }
}

async function login() {
  if (!canLogin.value) return;
  errorMessage.value = '';
  try {
    const confirmed = await new Promise(resolve => uni.showModal({
      title: '确认微信登录',
      content: '将使用当前微信应用已登录的账号进入微笑口腔。微信不会提供其他账号选择列表。',
      confirmText: '确认登录',
      success: result => resolve(Boolean(result.confirm)),
      fail: () => resolve(false)
    }));
    if (!confirmed) return;
    const user = await loginWithWechat();
    if (user) emit('success', user);
  } catch (error) { errorMessage.value = error.message || '微信登录未完成，请重试'; }
}

function close() {
  if (authState.busy) return;
  dismissLoginDialog();
  emit('close');
}
</script>

<style scoped>
.login-mask { position: fixed; inset: 0; z-index: 900; display: flex; align-items: center; justify-content: center; padding: 45rpx; background: rgba(20, 46, 36, .48); }
.login-panel { position: relative; width: 100%; max-height: 88vh; overflow-y: auto; padding: 44rpx 34rpx 32rpx; background: #fff; border-radius: 28rpx; text-align: center; box-sizing: border-box; }
.close-button { position: absolute; top: 8rpx; right: 12rpx; margin: 0; width: 64rpx; padding: 0; line-height: 64rpx; background: transparent; color: #8ba195; font-size: 42rpx; border: 0; }
.close-button::after { border: 0; }
.login-mark { width: 74rpx; height: 74rpx; margin: 0 auto 22rpx; border-radius: 22rpx; line-height: 68rpx; background: #12836d; color: #fff; font-size: 58rpx; font-weight: 500; }
.login-heading { font-size: 34rpx; font-weight: 600; color: #244c3c; }
.login-description { margin-top: 16rpx; color: #799082; font-size: 25rpx; line-height: 1.7; }
.login-hint { margin-top: 22rpx; color: #82948b; font-size: 23rpx; line-height: 1.7; }
.login-error { margin-top: 22rpx; border-radius: 12rpx; padding: 16rpx; background: #fff0ec; color: #a55a45; font-size: 24rpx; line-height: 1.6; }
.retry-link { margin: 18rpx 0; color: #12836d; font-size: 24rpx; }
.privacy-card { margin-top: 20rpx; padding: 20rpx; border-radius: 16rpx; background: #f2f7f2; }
.login-submit { margin-top: 26rpx; }
.phone-login { margin-top: 18rpx; font-size: 26rpx; }
.browse-link { margin-top: 26rpx; color: #789086; font-size: 24rpx; }
</style>
