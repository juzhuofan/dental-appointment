<template>
  <view class="card appointment-card" @click="$emit('select', appointment)">
    <view class="row between"><text class="date">{{ dateText(appointment.startTime) }} {{ timeRange(appointment) }}</text><text :class="['pill', `pill--${appointment.status}`]">{{ statusLabel(appointment.status) }}</text></view>
    <view class="divider" />
    <view class="row gap"><view class="avatar mini-avatar">{{ appointment.doctorName?.slice(0, 1) || '医' }}</view><view class="flex-1"><view class="name">{{ appointment.doctorName }} <text class="muted small">{{ appointment.departmentName }}</text></view><view class="muted top-space small">就诊人：{{ appointment.patientName }}</view></view><text class="chevron">›</text></view>
    <view class="footer"><text class="muted small">{{ appointment.appointmentNo }}</text><text class="link small">查看详情</text></view>
  </view>
</template>
<script setup>
import { dateText, timeRange, statusLabel } from '../utils/format';
defineProps({ appointment: { type: Object, required: true } });
defineEmits(['select']);
</script>
<style scoped>
.appointment-card { margin-top: 20rpx; }
.date { font-size: 27rpx; font-weight: 600; }
.mini-avatar { height: 76rpx; width: 76rpx; font-size: 34rpx; }
.name { font-size: 30rpx; font-weight: 600; }
.name text { margin-left: 15rpx; font-weight: 400; }
.top-space { margin-top: 8rpx; }
.footer { margin-top: 24rpx; display: flex; justify-content: space-between; align-items: center; }
.chevron { font-size: 45rpx; color: #adc0b0; }
</style>
