<template>
  <view class="page"><view class="eyebrow">PERSONAL CENTER</view><view class="title top-space">你好，{{ loggedIn ? (profile?.realName || user?.displayName || '就诊人') : '欢迎来到微笑口腔' }}</view><view class="subtitle">你的预约与资料，都可以在这里管理</view><view class="card profile-card section" @click="editProfile"><view class="row gap"><view class="avatar">{{ loggedIn ? (profile?.realName?.slice(0, 1) || '我') : '你' }}</view><view class="flex-1"><view class="profile-name">{{ loggedIn ? (profile?.realName || '我的就诊资料') : '点击一键登录' }}</view><view class="muted top-space">{{ loggedIn ? (profile?.phone || '完善资料，让预约更顺利') : '开始你的门诊预约体验' }}</view></view><text class="arrow">›</text></view></view><view v-if="error" class="hint top-space" @click="load">{{ error }} · 点击重试</view><view class="card menu section"><view class="menu-item" @click="appointments"><view class="menu-icon">≡</view><text class="flex-1">我的预约</text><text class="arrow">›</text></view><view class="menu-item" @click="editProfile"><view class="menu-icon">○</view><text class="flex-1">就诊资料</text><text class="arrow">›</text></view><view class="menu-item" @click="callClinic"><view class="menu-icon">⌕</view><text class="flex-1">联系诊所</text><text class="muted small">{{ clinic.phone || '以首页为准' }}</text><text class="arrow">›</text></view></view><view class="card section"><text class="section-title">就诊小提示</text><view class="body top-space">请提前核对预约时间与诊所地址，按时到诊。若计划有变，可在规定时间内取消预约，将号源留给有需要的人。</view></view><button v-if="loggedIn" class="btn btn-outline section" :loading="loggingOut" :disabled="loggingOut" @click="logout">退出登录</button><button v-else class="btn btn-primary section" @click="login">一键登录</button><view class="demo-note">本地演示版 · 仅使用虚构就诊资料</view></view>
</template>
<script setup>
import { ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { hasSession, getUser, ensureLogin, clearSession } from '../../utils/session';
const loggedIn = ref(false); const user = ref(null); const profile = ref(null); const clinic = ref({}); const error = ref(''); const loggingOut = ref(false);
async function load() {
  loggedIn.value = hasSession(); user.value = getUser(); error.value = '';
  if (!loggedIn.value) profile.value = null;
  const tasks = [api.clinic()]; if (loggedIn.value) tasks.push(api.profile());
  try { const results = await Promise.all(tasks); clinic.value = results[0]; if (loggedIn.value) profile.value = results[1]; } catch (e) { error.value = e.message; }
}
const login = () => ensureLogin('/pages/me/index');
function editProfile() { if (ensureLogin('/pages/profile/index')) uni.navigateTo({ url: '/pages/profile/index' }); }
const appointments = () => uni.switchTab({ url: '/pages/appointments/index' });
function callClinic() { if (clinic.value.phone) uni.makePhoneCall({ phoneNumber: clinic.value.phone }); else uni.showToast({ title: '诊所尚未公布联系电话', icon: 'none' }); }
function logout() { uni.showModal({ title: '退出当前登录？', content: '同设备再次登录仍会保留演示就诊人及预约。', success: async result => { if (!result.confirm) return; loggingOut.value = true; try { await api.logout(); clearSession(); await load(); } catch (e) { notifyError(e); } finally { loggingOut.value = false; } } }); }
onShow(load);
</script>
<style scoped>
.title { font-size: 35rpx; margin-top: 17rpx; }.profile-name { font-size: 31rpx; font-weight: 600; }.arrow { font-size: 40rpx; color: #aec1b0; margin-left: 10rpx; }.profile-card { padding: 32rpx; }.menu { padding: 4rpx 28rpx; }.menu-item { display: flex; align-items: center; gap: 17rpx; padding: 30rpx 0; border-bottom: 1rpx solid #ecf1e9; }.menu-item:last-child { border-bottom: 0; }.menu-icon { height: 48rpx; width: 48rpx; border-radius: 13rpx; font-size: 36rpx; color: #4f8b6c; background: #edf5ed; text-align: center; line-height: 46rpx; }.body { font-size: 25rpx; }.demo-note { text-align: center; font-size: 22rpx; color: #a3afa1; margin-top: 35rpx; }
</style>
