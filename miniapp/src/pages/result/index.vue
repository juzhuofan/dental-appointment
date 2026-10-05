<template>
  <view class="page result-page"><view class="success-icon"><view class="check" /></view><view class="title result-title">预约已提交</view><view class="subtitle centered">诊所会尽快确认你的预约<br />请在预约页面留意处理状态</view><view v-if="loading" class="loading">正在读取预约信息…</view><view v-else-if="appointment" class="card section"><view class="row between"><text class="strong">预约信息</text><text :class="['pill', `pill--${appointment.status}`]">{{ statusLabel(appointment.status) }}</text></view><view class="divider" /><view class="info-row"><text class="info-label">就诊医生</text><text class="info-value">{{ appointment.doctorName }} · {{ appointment.departmentName }}</text></view><view class="info-row"><text class="info-label">预约时间</text><text class="info-value">{{ dateText(appointment.startTime) }} {{ timeRange(appointment) }}</text></view><view class="info-row"><text class="info-label">就诊人</text><text class="info-value">{{ appointment.patientName }}</text></view><view class="info-row"><text class="info-label">预约编号</text><text class="info-value small">{{ appointment.appointmentNo }}</text></view></view><view v-else class="hint section">{{ error || '预约已提交，可以在“我的预约”中查看记录。' }}</view><button class="btn btn-primary section" @click="detail">查看这次预约</button><button class="btn btn-outline top-space" @click="appointments">进入我的预约</button><view class="back-home" @click="home">返回首页 ›</view></view>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { api } from '../../utils/request';
import { dateText, timeRange, statusLabel } from '../../utils/format';
const appointment = ref(null); const loading = ref(true); const error = ref(''); let id = '';
onLoad(async options => { id = options.id; try { appointment.value = await api.appointment(id); } catch (e) { error.value = e.message; } finally { loading.value = false; } });
const detail = () => uni.redirectTo({ url: `/pages/appointment/index?id=${id}` });
const appointments = () => uni.switchTab({ url: '/pages/appointments/index' });
const home = () => uni.switchTab({ url: '/pages/home/index' });
</script>
<style scoped>
.result-page { padding-top: 65rpx; }.success-icon { width: 135rpx; height: 135rpx; background: #e4f3e9; border-radius: 50%; margin: 0 auto 32rpx; position: relative; }.check { border-right: 7rpx solid #12836d; border-bottom: 7rpx solid #12836d; width: 28rpx; height: 55rpx; transform: rotate(45deg); position: absolute; top: 32rpx; left: 54rpx; }.result-title,.centered { text-align: center; }.result-title { font-size: 43rpx; }.subtitle { line-height: 1.9; }.back-home { margin-top: 35rpx; text-align: center; color: #7b9782; font-size: 25rpx; }
</style>
