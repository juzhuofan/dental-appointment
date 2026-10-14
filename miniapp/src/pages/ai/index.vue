<template>
  <view class="chat-page" :style="{ height: `${viewportHeight}px` }">
    <view class="chat-heading"><view><view class="chat-title">诊所 AI 助手</view>
      <text class="small muted">了解诊所 · 选择医生 · 初步导诊</text></view>
      <view class="chat-actions"><button class="btn btn-outline btn-small" :disabled="state.sending || state.loading" @click="newChat">新对话</button>
        <button class="btn btn-outline btn-small" :disabled="state.sending || state.loading || !loggedIn" @click="historyVisible = true">历史</button></view>
    </view>
    <view class="chat-notice">就诊提示供参考，诊断与治疗需由医生检查后确定。紧急情况请直接就医。</view>
    <scroll-view class="chat-scroll" scroll-y :scroll-into-view="scrollTarget" scroll-with-animation>
      <view class="chat-content">
        <view v-if="!loggedIn" class="chat-welcome card"><view class="section-title">登录后开始交流</view>
          <view class="body top-space">你的聊天记录会保存在当前账号中，方便后续继续咨询。</view>
          <button class="btn btn-primary top-space" @click="login">微信登录</button></view>
        <view v-else-if="state.loading" class="loading">正在加载聊天记录…</view>
        <template v-else>
          <view v-if="!state.messages.length && !state.pending" class="chat-welcome card">
            <view class="section-title">你好，今天想了解什么？</view>
            <view class="body top-space">可以咨询营业时间、医生擅长方向，也可以描述口腔问题，我会帮助你整理就诊需求。</view>
            <view class="example-list"><button v-for="example in examples" :key="example" class="example" :disabled="state.sending" @click="chooseExample(example)">{{ example }}</button></view>
          </view>
          <button v-if="state.hasMore" class="older link" :loading="state.loadingOlder" @click="controller.loadOlder">查看更早的消息</button>
          <view v-for="message in state.messages" :id="`message-${message.id}`" :key="message.id">
            <AiChatMessage :message="message" :booking-id="bookingId" @book="book" />
          </view>
          <view v-if="state.pending" class="pending-message"><text>{{ state.pending.content }}</text>
            <view class="small">{{ state.sending ? '正在等待回复…' : '发送结果待确认，请重试同一条消息' }}</view></view>
          <view :id="bottomId" class="chat-bottom" />
        </template>
      </view>
    </scroll-view>
    <view class="chat-composer">
      <view v-if="configError" class="chat-error">{{ configError }}<text class="link" @click="activate"> 重新连接</text></view>
      <view v-if="state.error" class="chat-error">{{ state.error }}
        <text v-if="!state.pending && state.errorAction" class="link retry-chat" @click="controller.retry"> 重新加载</text></view>
      <view v-if="available === false" class="muted small">智能助手暂未开放，请手动选择医生。</view>
      <view class="composer-row"><textarea v-model="state.input" class="composer-input" maxlength="300" auto-height
        :disabled="state.sending || !!state.pending || !loggedIn || available !== true"
        placeholder="描述你的问题，每条最多 300 字" />
        <button class="btn btn-primary btn-small" :loading="state.sending"
          :disabled="state.sending || state.loading || !loggedIn || available !== true || (!state.pending && !state.input.trim())"
          @click="controller.send">{{ state.pending && !state.sending ? '重试发送' : '发送' }}</button></view>
      <view class="privacy-note">聊天按账号保存，需求会发送给 AI。请勿填写姓名、电话等身份信息。</view>
    </view>
    <view v-if="historyVisible" class="history-mask" @click="historyVisible = false">
      <view class="history-panel" @click.stop><view class="row between history-heading"><text class="section-title">我的会话</text>
        <text class="link" @click="historyVisible = false">关闭</text></view>
        <view v-if="state.conversationsNeedRefresh" class="chat-error history-error">
          {{ state.error || '会话列表需要刷新后继续查看。' }}
          <text class="link retry-history" @click="controller.retry">重新加载历史</text>
        </view>
        <scroll-view class="history-scroll" scroll-y>
          <view v-for="conversation in state.conversations" :key="conversation.id" class="history-row">
            <view class="history-name" @click="openConversation(conversation.id)">{{ conversation.title }}</view>
            <text class="link small" @click="deleteConversation(conversation)">删除</text></view>
          <view v-if="!state.conversations.length" class="loading">暂无聊天记录</view>
          <view v-if="state.conversationHasMore" class="history-tail"><button class="older link history-more"
            :loading="state.loadingConversations" :disabled="state.loadingConversations || state.loading"
            @click="controller.loadMoreConversations">查看更多会话</button></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>
<script setup>
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue';
import { onShow, onHide, onResize } from '@dcloudio/uni-app';
import AiChatMessage from '../../components/AiChatMessage.vue';
import { api, notifyError } from '../../utils/request';
import { bootstrapAuth, ensureLogin } from '../../utils/auth';
import { authState, getUser, hasSession } from '../../utils/session';
import { createAiChatController } from '../../utils/ai-chat';

const state = reactive({});
const loggedIn = computed(() => hasSession());
const controller = createAiChatController({ api, state, getUserId: () => hasSession() ? getUser()?.id : null });
const available = ref(null); const historyVisible = ref(false); const bookingId = ref(null);
const configError = ref('');
let historyTabBarHidden = false;
const viewportHeight = ref(640); const scrollTarget = ref('');
const examples = ['诊所在哪里，什么时候营业？', '有哪些医生，分别擅长什么？', '这周末下午有口腔检查号源吗？', '我牙齿疼，应该怎么安排就诊？'];
const bottomId = computed(() => `chat-bottom-${state.pending?.requestId || state.messages[state.messages.length - 1]?.id || 'empty'}`);

function resize() {
  try { viewportHeight.value = (uni.getWindowInfo ? uni.getWindowInfo() : uni.getSystemInfoSync()).windowHeight; }
  catch { viewportHeight.value = 640; }
}
async function activate() {
  resize();
  configError.value = '';
  try { available.value = (await api.aiConfig()).enabled; }
  catch (error) { configError.value = error.message; }
  await bootstrapAuth();
  await controller.activate();
}
async function login() { if (await ensureLogin('/pages/ai/index')) await controller.activate(); }
function newChat() { controller.startNew(); }
function chooseExample(value) { if (!state.sending && !state.pending) state.input = value; }
async function openConversation(id) { historyVisible.value = false; await controller.openConversation(id); }
function deleteConversation(conversation) {
  uni.showModal({ title: '删除会话', content: `删除“${conversation.title}”后，该会话将不再显示在历史记录中。`,
    confirmText: '删除', cancelText: '取消',
    success: async result => { if (result.confirm) await controller.removeConversation(conversation.id); } });
}
async function book(schedule) {
  if (bookingId.value) return;
  bookingId.value = schedule.id;
  try {
    const target = `/pages/confirm/index?scheduleId=${schedule.id}&doctorId=${schedule.doctorId}`;
    if (!(await ensureLogin(target))) return;
    const latest = await api.schedule(schedule.id);
    if (latest.remainingSlots <= 0) throw new Error('该时段已约满，请重新查询号源');
    uni.navigateTo({ url: target });
  } catch (error) { notifyError(error); }
  finally { bookingId.value = null; }
}
watch(() => authState.user?.id, () => { controller.synchronizeAccount(); historyVisible.value = false; });
function restoreHistoryTabBar() {
  if (!historyTabBarHidden) return;
  historyTabBarHidden = false;
  uni.showTabBar?.({ animation: false, fail() {} });
}
watch(historyVisible, visible => {
  if (!visible) { restoreHistoryTabBar(); return; }
  uni.hideKeyboard?.();
  if (typeof uni.hideTabBar === 'function') {
    historyTabBarHidden = true;
    uni.hideTabBar({ animation: false, fail() { historyTabBarHidden = false; } });
  }
});
watch(bottomId, async id => { await nextTick(); scrollTarget.value = id; });
onHide(() => { historyVisible.value = false; restoreHistoryTabBar(); });
onUnmounted(restoreHistoryTabBar);
onShow(activate); onResize(resize);
</script>
<style scoped>
.chat-page { display: flex; flex-direction: column; background: #f7f9f7; overflow: hidden; }
.chat-heading { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 24rpx; flex-shrink: 0; }
.chat-title { font-size: 32rpx; font-weight: 600; margin-bottom: 8rpx; }
.chat-actions { display: flex; gap: 10rpx; flex-shrink: 0; }.chat-actions .btn { margin: 0; padding: 10rpx 16rpx; font-size: 22rpx; }
.chat-notice { flex-shrink: 0; margin: 0 24rpx 12rpx; padding: 16rpx; background: #edf3e9; border-radius: 12rpx; color: #728370; font-size: 21rpx; line-height: 1.7; }
.chat-scroll { flex: 1; min-height: 0; height: 0; }.chat-content { padding: 0 24rpx; }.chat-welcome { margin-top: 24rpx; }
.example-list { margin-top: 26rpx; display: flex; flex-direction: column; gap: 16rpx; }.example { text-align: left; padding: 18rpx 20rpx; font-size: 24rpx; color: #3a715d; border-radius: 14rpx; background: #edf6ef; line-height: 1.6; margin: 0; }
.chat-composer { flex-shrink: 0; padding: 18rpx 24rpx; border-top: 1rpx solid #e6ece4; background: #fff; }.composer-row { display: flex; align-items: flex-end; gap: 16rpx; }
.composer-input { flex: 1; min-width: 0; min-height: 64rpx; max-height: 160rpx; font-size: 26rpx; line-height: 1.7; background: #f2f6f1; border-radius: 16rpx; padding: 16rpx; }
.composer-row .btn { margin: 0; flex-shrink: 0; padding: 17rpx 24rpx; }.privacy-note { font-size: 19rpx; color: #91a093; line-height: 1.6; margin-top: 12rpx; }
.chat-error { padding-bottom: 14rpx; color: #a2664c; font-size: 23rpx; line-height: 1.6; }.older { font-size: 23rpx; padding: 18rpx; background: transparent; text-align: center; }.chat-bottom { height: 20rpx; }
.pending-message { margin: 16rpx 0 16rpx 54rpx; border-radius: 18rpx; padding: 22rpx; background: #edf6ef; color: #4b806a; white-space: pre-wrap; font-size: 26rpx; line-height: 1.8; }.pending-message .small { margin-top: 10rpx; color: #90a293; }
.history-mask { position: fixed; inset: 0; padding-top: 32rpx; background: #172a2270; z-index: 940; display: flex; align-items: flex-end; }
.history-panel { display: flex; flex-direction: column; width: 100%; max-height: 100%; overflow: hidden; border-radius: 24rpx 24rpx 0 0; background: #fff; padding: 30rpx 30rpx calc(28rpx + env(safe-area-inset-bottom)); }
.history-heading, .history-error { flex-shrink: 0; }
.history-scroll { flex-shrink: 1; min-height: 0; height: 60vh; margin-top: 20rpx; }
.history-tail { padding: 12rpx 0 20rpx; }
.history-more { width: 100%; min-height: 88rpx; }
.history-row { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 26rpx 0; border-bottom: 1rpx solid #edf1eb; }.history-name { flex: 1; min-width: 0; font-size: 26rpx; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
</style>
