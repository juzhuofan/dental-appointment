<template>
  <view class="card doctor-card" @click="$emit('select', doctor)">
    <view class="row gap">
      <view class="avatar"><image v-if="doctor.avatarUrl" :src="doctor.avatarUrl" mode="aspectFill" /><text v-else>{{ doctor.name?.slice(0, 1) || '医' }}</text></view>
      <view class="flex-1"><view class="row gap"><text class="doctor-name">{{ doctor.name }}</text><text class="muted small">{{ doctor.title || '口腔医师' }}</text></view><view class="department">{{ doctor.departmentName }}</view></view>
      <text class="arrow">›</text>
    </view>
    <view class="specialty">擅长 · {{ doctor.specialty || '口腔常见问题诊疗' }}</view>
  </view>
</template>
<script setup>
defineProps({ doctor: { type: Object, required: true } });
defineEmits(['select']);
</script>
<style scoped>
.doctor-card { margin-top: 20rpx; }
.doctor-name { font-size: 31rpx; font-weight: 600; }
.department { color: #748b7c; font-size: 25rpx; margin-top: 12rpx; }
.specialty { font-size: 25rpx; color: #738779; padding-top: 22rpx; margin-top: 23rpx; border-top: 1rpx solid #edf1eb; line-height: 1.7; }
.arrow { font-size: 45rpx; color: #abc0b1; }
</style>
