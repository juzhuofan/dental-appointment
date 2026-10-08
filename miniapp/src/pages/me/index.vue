<template>
  <view class="page">
    <view class="eyebrow">PERSONAL CENTER</view>
    <view class="title top-space">你好，{{ loggedIn ? (user?.displayName || '微信用户') : '欢迎来到微笑口腔' }}</view>
    <view class="subtitle">你的预约与资料，都可以在这里管理</view>
    <view v-if="accountNeedsSetup" class="card account-hint section" @click="editAccount">
      <view class="section-title">设置微信昵称与头像</view>
      <view class="muted top-space">点击选择微信建议资料，或自行填写；保存后显示在此页面。</view>
    </view>
    <view class="card profile-card section" @click="editAccount">
      <view class="row gap">
        <image v-if="loggedIn && (user?.avatarDisplayUrl || user?.avatarUrl)" class="avatar avatar-image" :src="user.avatarDisplayUrl || user.avatarUrl" mode="aspectFill" />
        <view v-else class="avatar">{{ loggedIn ? (user?.displayName?.slice(0, 1) || '我') : '你' }}</view>
        <view class="flex-1"><view class="profile-name">{{ loggedIn ? (user?.displayName || '微信用户') : '点击微信登录' }}</view><view class="muted top-space">{{ loggedIn ? '账号资料 · 点击设置昵称与头像' : '登录后开始你的门诊预约' }}</view></view><text class="arrow">›</text>
      </view>
    </view>
    <view v-if="error" class="hint top-space" @click="load">{{ error }} · 点击重试</view>
    <view class="card menu section">
      <view class="menu-item" @click="appointments"><view class="menu-icon">≡</view><text class="flex-1">我的预约</text><text class="arrow">›</text></view>
      <view class="menu-item" @click="editProfile"><view class="menu-icon">○</view><text class="flex-1">就诊人管理</text><text class="muted small">{{ profile?.realName || '待填写' }}</text><text class="arrow">›</text></view>
      <view v-if="loggedIn" class="menu-item" @click="editAccount"><view class="menu-icon">◇</view><text class="flex-1">账号昵称与头像</text><text class="arrow">›</text></view>
      <view class="menu-item" @click="callClinic"><view class="menu-icon">⌕</view><text class="flex-1">联系诊所</text><text class="muted small">{{ clinic.phone || '以首页为准' }}</text><text class="arrow">›</text></view>
    </view>
    <view class="card section"><text class="section-title">就诊小提示</text><view class="body top-space">请提前核对预约时间与诊所地址，按时到诊。若计划有变，可在规定时间内取消预约，将号源留给有需要的人。</view></view>
    <button v-if="loggedIn" class="btn btn-outline section" :loading="loggingOut" :disabled="loggingOut" @click="logout">退出登录</button>
    <button v-else class="btn btn-primary section" @click="login">微信一键登录</button>
    <view class="profile-note">请核对姓名与联系电话，方便诊所联系你</view>
    <AccountProfileEditor v-if="loggedIn" :visible="accountEditorVisible" @close="accountEditorVisible = false" />
  </view>
</template>
<script setup>
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { api, notifyError } from '../../utils/request';
import { authState, hasSession, clearSession, saveUser } from '../../utils/session';
import { bootstrapAuth, ensureLogin } from '../../utils/auth';
import AccountProfileEditor from '../../components/AccountProfileEditor.vue';
const loggedIn = computed(hasSession); const user = computed(() => authState.user); const profile = ref(null); const clinic = ref({}); const error = ref(''); const loggingOut = ref(false);
const accountEditorVisible = ref(false);
const accountNeedsSetup = computed(() => loggedIn.value
  && (user.value?.displayName === '微信用户' || !user.value?.avatarUrl));
async function load() {
  await bootstrapAuth(); error.value = '';
  if (!loggedIn.value) profile.value = null;
  const tasks = [api.clinic()]; if (loggedIn.value) tasks.push(api.me(), api.profile());
  try { const results = await Promise.all(tasks); clinic.value = results[0]; if (results[1]) { saveUser(results[1]); profile.value = results[2]; } } catch (e) { error.value = e.message; }
}
function openPendingAccountSetup() {
  if (loggedIn.value && authState.justLoggedIn) {
    authState.justLoggedIn = false;
    if (accountNeedsSetup.value) accountEditorVisible.value = true;
  }
}
const login = () => ensureLogin('/pages/me/index');
async function editProfile() { if (await ensureLogin('/pages/profile/index')) uni.navigateTo({ url: '/pages/profile/index' }); }
const appointments = () => uni.switchTab({ url: '/pages/appointments/index' });
function callClinic() { if (clinic.value.phone) uni.makePhoneCall({ phoneNumber: clinic.value.phone }); else uni.showToast({ title: '诊所尚未公布联系电话', icon: 'none' }); }
async function editAccount() { if (await ensureLogin('/pages/me/index')) accountEditorVisible.value = true; }
function logout() { uni.showModal({ title: '退出当前登录？', content: '预约与就诊资料会保留在你的微信账号中，再次微信登录可继续查看。', success: async result => { if (!result.confirm) return; loggingOut.value = true; try { await api.logout(); clearSession(); await load(); } catch (e) { notifyError(e); } finally { loggingOut.value = false; } } }); }
onShow(() => { openPendingAccountSetup(); load(); });
</script>
<style scoped>
.title { font-size: 35rpx; margin-top: 17rpx; }.profile-name { font-size: 31rpx; font-weight: 600; }.arrow { font-size: 40rpx; color: #aec1b0; margin-left: 10rpx; }.profile-card { padding: 32rpx; }.menu { padding: 4rpx 28rpx; }.menu-item { display: flex; align-items: center; gap: 17rpx; padding: 30rpx 0; border-bottom: 1rpx solid #ecf1e9; }.menu-item:last-child { border-bottom: 0; }.menu-icon { height: 48rpx; width: 48rpx; border-radius: 13rpx; font-size: 36rpx; color: #4f8b6c; background: #edf5ed; text-align: center; line-height: 46rpx; }.body { font-size: 25rpx; }.profile-note { text-align: center; font-size: 22rpx; color: #a3afa1; margin-top: 35rpx; }.avatar-image { display: block; flex-shrink: 0; }
.account-hint { padding: 25rpx 30rpx; border: 1rpx solid #b9dbca; background: #f0f8f2; }
</style>
