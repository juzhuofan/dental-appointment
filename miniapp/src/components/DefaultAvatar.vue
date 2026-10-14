<template>
  <view class="default-avatar" :style="{ width: `${size}rpx`, height: `${size}rpx` }" aria-label="默认头像">
    <view class="avatar-head" />
    <view class="avatar-shoulders" />
  </view>
</template>

<script setup>
defineProps({ size: { type: Number, default: 96 } });
</script>

<style scoped>
.default-avatar { position: relative; flex-shrink: 0; overflow: hidden; border-radius: 24%; background: #e6f2eb; }
.avatar-head { position: absolute; top: 19%; left: 35%; width: 30%; height: 30%; border-radius: 50%; background: #77a38e; }
.avatar-shoulders { position: absolute; bottom: 7%; left: 18%; width: 64%; height: 38%; border-radius: 50% 50% 14% 14%; background: #77a38e; }
</style>
