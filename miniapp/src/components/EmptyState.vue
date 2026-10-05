<template>
  <view class="empty">
    <view class="empty-symbol"><view class="empty-line" /><view class="empty-line short" /></view>
    <view class="empty-title">{{ title }}</view>
    <view class="empty-text">{{ description }}</view>
    <button v-if="action" class="btn btn-outline btn-small" @click="$emit('action')">{{ action }}</button>
  </view>
</template>
<script setup>
defineProps({ title: { type: String, default: '暂无内容' }, description: { type: String, default: '新的信息发布后会在这里显示' }, action: String });
defineEmits(['action']);
</script>
<style scoped>
.empty { padding: 65rpx 25rpx; text-align: center; }
.empty-symbol { margin: 0 auto 25rpx; width: 82rpx; height: 90rpx; border: 3rpx solid #bad0be; border-radius: 15rpx; padding: 23rpx 15rpx; background: #f0f6ef; }
.empty-line { height: 4rpx; background: #bad0be; margin: 9rpx 0; border-radius: 3rpx; }
.short { width: 65%; }
.empty-title { color: #5b7967; font-size: 29rpx; font-weight: 600; margin-bottom: 12rpx; }
.btn { display: inline-block; margin-top: 25rpx; }
</style>
