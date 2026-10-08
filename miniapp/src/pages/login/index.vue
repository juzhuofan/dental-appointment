<template>
  <view class="page login-page"><view class="eyebrow">WELCOME TO DENTAL CARE</view><view class="title">让每一次就诊<br />从安心开始</view><view class="subtitle">登录后即可预约门诊、查看预约进度<br />以及管理你的就诊资料</view><view class="login-card"><LoginDialog :modal="false" @success="success" @close="home" /></view></view>
</template>
<script setup>
import { onLoad } from '@dcloudio/uni-app';
import LoginDialog from '../../components/LoginDialog.vue';
import { bootstrapAuth, offerAccountProfileSetup } from '../../utils/auth';
import { hasSession, navigateAfterLogin } from '../../utils/session';
let next = '/pages/me/index';
onLoad(async options => {
  if (options.next) {
    try { next = decodeURIComponent(options.next); } catch { next = '/pages/me/index'; }
  }
  await bootstrapAuth();
  if (hasSession()) navigateAfterLogin(next);
});
const success = async user => {
  if (await offerAccountProfileSetup(user)) uni.switchTab({ url: '/pages/me/index' });
  else navigateAfterLogin(next);
};
const home = () => uni.switchTab({ url: '/pages/home/index' });
</script>
<style scoped>
.login-page { padding-top: 45rpx; }.title { font-size: 46rpx; margin-top: 25rpx; line-height: 1.5; }.subtitle { margin-top: 20rpx; line-height: 1.8; }.login-card { margin-top: 35rpx; }
</style>
