<template>
  <view class="page login-page"><view class="logo"><view class="cross-h" /><view class="cross-v" /></view><view class="eyebrow">WELCOME TO DENTAL CARE</view><view class="title">让每一次就诊<br />从安心开始</view><view class="subtitle">登录后即可预约门诊、查看预约进度<br />以及管理你的就诊资料</view><view class="card login-card"><view class="strong">欢迎来到口腔诊所预约</view><view class="body top-space">初版使用演示登录，一键进入即可体验完整预约流程。</view><button class="btn btn-primary top-space" :loading="loading" :disabled="loading" @click="login">{{ loading ? '正在登录…' : '一键登录，开始预约' }}</button><view class="hint top-space">每台设备保留独立的演示就诊人。请使用虚构资料进行体验。</view></view><view class="login-footer" @click="home">先看看诊所信息 ›</view></view>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { getDeviceId, saveSession, navigateAfterLogin } from '../../utils/session';
const loading = ref(false); let next = '/pages/me/index';
onLoad(options => { if (options.next) next = decodeURIComponent(options.next); });
async function login() {
  if (loading.value) return;
  loading.value = true;
  try { const session = await api.demoLogin(getDeviceId()); saveSession(session); navigateAfterLogin(next); }
  catch (e) { notifyError(e); } finally { loading.value = false; }
}
const home = () => uni.switchTab({ url: '/pages/home/index' });
</script>
<style scoped>
.login-page { padding-top: 75rpx; }
.logo { width: 116rpx; height: 116rpx; position: relative; border-radius: 35rpx; background: #12836d; margin-bottom: 35rpx; }
.cross-h,.cross-v { position: absolute; width: 52rpx; height: 13rpx; left: 32rpx; top: 52rpx; border-radius: 5rpx; background: #fff; }
.cross-v { transform: rotate(90deg); }.title { font-size: 53rpx; margin-top: 25rpx; line-height: 1.5; }.subtitle { margin-top: 24rpx; line-height: 1.9; }
.login-card { margin-top: 55rpx; padding: 35rpx; }.strong { font-size: 31rpx; }.body { font-size: 25rpx; }.btn { margin-top: 35rpx; }.hint { font-size: 22rpx; background: transparent; padding: 0; color: #90a08d; }.login-footer { margin-top: 38rpx; color: #6c8e7b; text-align: center; font-size: 25rpx; }
</style>
