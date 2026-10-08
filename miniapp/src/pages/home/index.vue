<template>
  <view class="page">
    <view class="welcome"><view><text class="eyebrow">DENTAL CARE</text><view class="title">从一份安心预约开始</view><view class="subtitle">关注每一次微笑，照顾每一位就诊人</view></view><view class="brand-mark"><view class="cross-h" /><view class="cross-v" /></view></view>
    <view class="hero" @click="openDoctors()"><view class="hero-label">门诊预约</view><view class="hero-title">专业口腔照护<br />让微笑轻松一点</view><view class="hero-bottom"><text>选择医生与时间，随时查看预约</text><text class="hero-arrow">↗</text></view><view class="hero-orbit orbit-one" /><view class="hero-orbit orbit-two" /></view>
    <view v-if="loading" class="loading">正在加载诊所信息…</view>
    <view v-else-if="error" class="error-card">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view>
    <template v-else>
      <view v-if="notices.length" class="notice-strip" @click="openNotice(notices[0])"><view class="notice-dot" /><text class="notice-label">公告</text><text class="notice-text">{{ notices[0].title }}</text><text class="link">›</text></view>
      <view class="section"><view class="row between"><text class="section-title">选择科室</text><text class="link" @click="openDoctors()">全部医生 ›</text></view><view class="department-grid"><view v-for="(item, index) in departments" :key="item.id" class="department-item" @click="openDoctors(item.id)"><view :class="['department-icon', `tone-${index % 4}`]"><view class="tooth-top" /><view class="tooth-root" /></view><view class="department-name">{{ item.name }}</view></view></view><EmptyState v-if="!departments.length" title="科室尚未发布" description="诊所发布后即可选择医生" /></view>
      <view class="section"><view class="row between"><text class="section-title">认识我们的医生</text><text class="link" @click="openDoctors()">查看全部 ›</text></view><DoctorCard v-for="doctor in doctors.slice(0, 3)" :key="doctor.id" :doctor="doctor" @select="openDoctor" /><EmptyState v-if="!doctors.length" title="医生资料准备中" /></view>
      <view class="section"><text class="section-title">诊所信息</text><view class="card top-space"><view class="clinic-name">{{ clinic.name || '微笑口腔诊所' }}</view><view class="info-row"><text class="info-label">营业时间</text><text class="info-value">{{ clinic.openingHours || '以诊所公告为准' }}</text></view><view class="info-row"><text class="info-label">诊所地址</text><text class="info-value">{{ clinic.address || '待补充' }}</text></view><view class="info-row" @click="callClinic"><text class="info-label">联系电话</text><text class="info-value link">{{ clinic.phone || '待补充' }}</text></view><view v-if="clinic.introduction" class="body top-space">{{ clinic.introduction }}</view></view></view>
      <view v-if="notices.length > 1" class="section"><text class="section-title">诊所公告</text><view v-for="notice in notices.slice(1, 4)" :key="notice.id" class="notice-row" @click="openNotice(notice)"><text>{{ notice.title }}</text><text class="muted">›</text></view></view>
      <view class="end-note">为每一份信任，认真对待每一次预约</view>
    </template>
    <LoginDialog :visible="authState.dialogVisible" @success="loggedIn" />
  </view>
</template>
<script setup>
import { ref } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import DoctorCard from '../../components/DoctorCard.vue';
import EmptyState from '../../components/EmptyState.vue';
import LoginDialog from '../../components/LoginDialog.vue';
import { api } from '../../utils/request';
import { bootstrapAuth, offerAccountProfileSetup } from '../../utils/auth';
import { authState } from '../../utils/session';
const clinic = ref({}); const departments = ref([]); const doctors = ref([]); const notices = ref([]); const loading = ref(true); const error = ref('');
async function load() {
  loading.value = true; error.value = '';
  try {
    const [c, d, docs, n] = await Promise.all([api.clinic(), api.departments(), api.doctors(), api.notices()]);
    clinic.value = c; departments.value = d.records; doctors.value = docs.records; notices.value = n.records;
  } catch (e) { error.value = e.message; } finally { loading.value = false; uni.stopPullDownRefresh(); }
}
const openDoctors = id => uni.navigateTo({ url: `/pages/doctors/index${id ? `?departmentId=${id}` : ''}` });
const openDoctor = doctor => uni.navigateTo({ url: `/pages/doctor/index?id=${doctor.id}` });
const openNotice = notice => uni.navigateTo({ url: `/pages/notice/index?id=${notice.id}` });
function callClinic() { if (clinic.value.phone) uni.makePhoneCall({ phoneNumber: clinic.value.phone }); }
async function loggedIn(user) {
  if (await offerAccountProfileSetup(user)) uni.switchTab({ url: '/pages/me/index' });
  else uni.showToast({ title: '微信登录成功', icon: 'success' });
}
onShow(() => { load(); bootstrapAuth(); }); onPullDownRefresh(load);
</script>
<style scoped>
.welcome { display: flex; align-items: center; justify-content: space-between; margin: 8rpx 0 28rpx; }
.title { font-size: 35rpx; margin-top: 11rpx; }
.subtitle { font-size: 23rpx; }
.brand-mark { width: 66rpx; height: 66rpx; border-radius: 19rpx; background: #e1f0e6; position: relative; margin-left: 14rpx; flex-shrink: 0; }
.cross-h,.cross-v { position: absolute; background: #12836d; border-radius: 4rpx; top: 30rpx; left: 17rpx; height: 8rpx; width: 32rpx; }
.cross-v { transform: rotate(90deg); }
.hero { position: relative; overflow: hidden; border-radius: 28rpx; padding: 34rpx; background: #12836d; color: #fff; min-height: 300rpx; }
.hero-label { display: inline-block; font-size: 22rpx; color: #daf2e6; padding: 7rpx 14rpx; background: #ffffff1c; border-radius: 8rpx; }
.hero-title { position: relative; z-index: 2; font-size: 43rpx; font-weight: 600; line-height: 1.45; margin-top: 17rpx; }
.hero-bottom { display: flex; align-items: center; justify-content: space-between; font-size: 22rpx; margin-top: 20rpx; color: #ceecdd; position: relative; z-index: 2; }
.hero-arrow { font-size: 36rpx; width: 58rpx; height: 58rpx; text-align: center; line-height: 58rpx; border-radius: 50%; background: #ffffff20; }
.hero-orbit { position: absolute; border: 1rpx solid #ffffff29; border-radius: 50%; width: 290rpx; height: 290rpx; top: -52rpx; right: -65rpx; }
.orbit-two { width: 400rpx; height: 400rpx; right: -120rpx; top: -105rpx; }
.notice-strip { display: flex; align-items: center; gap: 12rpx; margin-top: 22rpx; background: #eef4e8; border-radius: 16rpx; padding: 20rpx; font-size: 24rpx; }
.notice-dot { width: 12rpx; height: 12rpx; border-radius: 50%; background: #9fa665; }
.notice-label { color: #7b8550; font-weight: 600; }
.notice-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #788575; }
.department-grid { display: flex; flex-wrap: wrap; margin-top: 28rpx; row-gap: 26rpx; }
.department-item { width: 25%; text-align: center; padding: 0 7rpx; }
.department-icon { width: 86rpx; height: 86rpx; position: relative; margin: 0 auto; border-radius: 24rpx; background: #e4f1e7; color: #548971; }
.tone-1 { background: #f4eedf; color: #a89264; }.tone-2 { background: #e5edf5; color: #6e89a1; }.tone-3 { background: #f2e7e4; color: #b38573; }
.tooth-top { width: 38rpx; height: 31rpx; border: 3rpx solid currentColor; border-bottom: 0; border-radius: 15rpx 15rpx 7rpx 7rpx; position: absolute; left: 24rpx; top: 22rpx; }
.tooth-root { width: 31rpx; height: 24rpx; border: 3rpx solid currentColor; border-top: 0; border-radius: 0 0 12rpx 12rpx; position: absolute; left: 27rpx; top: 46rpx; }
.department-name { font-size: 24rpx; margin-top: 15rpx; color: #5d7869; line-height: 1.5; }
.clinic-name { font-size: 30rpx; font-weight: 600; margin-bottom: 16rpx; }
.notice-row { display: flex; align-items: center; justify-content: space-between; padding: 27rpx 0; border-bottom: 1rpx solid #e6ece4; font-size: 26rpx; }
.end-note { text-align: center; padding: 40rpx 0 0; font-size: 22rpx; color: #a0aca2; }
</style>
