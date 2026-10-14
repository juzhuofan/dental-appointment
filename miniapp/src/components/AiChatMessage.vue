<template>
  <view :class="['message-row', { 'from-user': message.role === 'USER' }]">
    <view v-if="message.role !== 'USER'" class="ai-avatar">AI</view>
    <view class="message-body">
      <view :class="['bubble', { emergency: message.answer?.guidance === 'EMERGENCY' }]">
        <view v-if="message.answer?.guidance === 'EMERGENCY'" class="risk-title">请优先寻求急诊帮助</view>
        <text class="message-text" selectable>{{ message.content }}</text>
      </view>
      <template v-if="message.role === 'ASSISTANT'">
        <view v-for="doctor in message.answer?.candidates || []" :key="doctor.doctorId" class="doctor-result">
          <view class="row between"><text class="strong">{{ doctor.doctorName }}</text>
            <text class="link small" @click="openDoctor(doctor.doctorId)">医生详情 ›</text></view>
          <view class="muted small top-space">{{ doctor.departmentName }} · {{ doctor.title || '口腔医师' }}</view>
          <view class="body small top-space">擅长：{{ doctor.specialty || '请进入医生详情查看' }}</view>
          <view v-for="schedule in doctor.schedules" :key="schedule.id" class="slot-result">
            <view><view>{{ dateText(schedule.startTime) }} {{ timeRange(schedule) }}</view>
              <text class="muted small">查询时剩余 {{ schedule.remainingSlots }} 个号源</text></view>
            <button class="btn btn-outline btn-small" :loading="bookingId === schedule.id"
              :disabled="!!bookingId" @click="$emit('book', schedule)">选择</button>
          </view>
        </view>
        <view v-if="message.answer?.references?.length" class="reference-list">
          <view v-for="reference in message.answer.references" :key="reference.title" class="reference">
            <text>{{ reference.title }}</text>
            <text v-if="reference.url" class="link" @click="copyReference(reference.url)">复制来源链接</text>
          </view>
        </view>
      </template>
      <text class="message-time">{{ dateText(message.createdAt) }} {{ timeText(message.createdAt) }}</text>
    </view>
  </view>
</template>
<script setup>
import { dateText, timeText, timeRange } from '../utils/format';
defineProps({ message: { type: Object, required: true }, bookingId: { type: [Number, String], default: null } });
defineEmits(['book']);
function openDoctor(id) { uni.navigateTo({ url: `/pages/doctor/index?id=${id}` }); }
function copyReference(url) { uni.setClipboardData({ data: url }); }
</script>
<style scoped>
.message-row { display: flex; align-items: flex-start; gap: 14rpx; padding: 16rpx 0; }
.from-user { justify-content: flex-end; }
.ai-avatar { flex-shrink: 0; width: 52rpx; height: 52rpx; line-height: 52rpx; text-align: center; background: #dfeee5; color: #12836d; border-radius: 15rpx; font-size: 22rpx; font-weight: 600; }
.message-body { max-width: 86%; min-width: 0; }
.bubble { padding: 23rpx; background: #fff; border-radius: 0 20rpx 20rpx 20rpx; border: 1rpx solid #e6ece4; }
.from-user .bubble { background: #12836d; border-color: #12836d; color: #fff; border-radius: 20rpx 0 20rpx 20rpx; }
.message-text { white-space: pre-wrap; word-break: break-word; line-height: 1.8; font-size: 27rpx; }
.emergency { background: #fff4ee; border-color: #ecc2ae; }
.risk-title { color: #a24f32; font-weight: 600; margin-bottom: 16rpx; }
.message-time { display: block; margin-top: 9rpx; color: #95a397; font-size: 20rpx; }
.from-user .message-time { text-align: right; }
.doctor-result { margin-top: 16rpx; background: #fff; border: 1rpx solid #e6ece4; border-radius: 18rpx; padding: 22rpx; }
.slot-result { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; padding: 18rpx 0; font-size: 23rpx; border-top: 1rpx solid #eff3ee; margin-top: 16rpx; }
.slot-result .btn { flex-shrink: 0; margin: 0; padding: 10rpx 16rpx; }
.reference-list { padding: 12rpx 4rpx; color: #8a998d; font-size: 20rpx; line-height: 1.7; }
.reference { display: flex; flex-wrap: wrap; gap: 8rpx; }
</style>
