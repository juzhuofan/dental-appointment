<template>
  <view class="page"><view class="eyebrow">MY APPOINTMENTS</view><view class="title top-space">每一次预约，都在这里</view><view class="subtitle">查看预约进度，安排你的就诊时间</view><view v-if="!loggedIn" class="card section"><EmptyState title="登录后查看你的预约" description="一键登录即可开始体验完整就诊流程" action="立即登录" @action="login" /></view><template v-else><scroll-view class="status-scroll" scroll-x><view class="status-list"><view v-for="tab in tabs" :key="tab.value" :class="['status-tab', { active: status === tab.value }]" @click="setStatus(tab.value)">{{ tab.label }}</view></view></scroll-view><view v-if="loading" class="loading">正在更新预约…</view><view v-else-if="error" class="error-card top-space">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view><template v-else><view class="count-line"><text class="muted">共 {{ total }} 次预约</text><text class="link" @click="load">刷新</text></view><AppointmentCard v-for="item in appointments" :key="item.id" :appointment="item" @select="detail" /><EmptyState v-if="!appointments.length" title="这里还没有预约" description="去认识医生，选择一个适合你的就诊时间" action="去预约" @action="home" /><button v-if="appointments.length < total" class="btn btn-outline section" :loading="loadingMore" :disabled="loadingMore" @click="loadMore">加载更多</button></template></template></view>
</template>
<script setup>
import { ref } from 'vue';
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app';
import { api } from '../../utils/request';
import { hasSession, ensureLogin } from '../../utils/session';
import AppointmentCard from '../../components/AppointmentCard.vue';
import EmptyState from '../../components/EmptyState.vue';
const tabs = [{ value: '', label: '全部' }, { value: 'PENDING', label: '待确认' }, { value: 'CONFIRMED', label: '待就诊' }, { value: 'COMPLETED', label: '已完成' }, { value: 'CANCELLED', label: '已取消' }, { value: 'NO_SHOW', label: '未到诊' }];
const status = ref(''); const appointments = ref([]); const total = ref(0); const loggedIn = ref(false); const loading = ref(false); const loadingMore = ref(false); const error = ref(''); let page = 1; let generation = 0;
async function load() { loggedIn.value = hasSession(); if (!loggedIn.value) { appointments.value = []; uni.stopPullDownRefresh(); return; } const current = ++generation; loading.value = true; error.value = ''; try { const result = await api.appointments({ status: status.value, page: 1, size: 20 }); if (current === generation) { appointments.value = result.records; total.value = result.total; page = 1; } } catch (e) { if (current === generation) error.value = e.message; } finally { if (current === generation) loading.value = false; uni.stopPullDownRefresh(); } }
async function loadMore() { if (loadingMore.value) return; loadingMore.value = true; const current = generation; try { const result = await api.appointments({ status: status.value, page: page + 1, size: 20 }); if (current === generation) { appointments.value.push(...result.records); total.value = result.total; page += 1; } } catch (e) { uni.showToast({ title: e.message, icon: 'none' }); } finally { loadingMore.value = false; } }
function setStatus(value) { status.value = value; load(); }
const detail = appointment => uni.navigateTo({ url: `/pages/appointment/index?id=${appointment.id}` });
const home = () => uni.switchTab({ url: '/pages/home/index' });
const login = () => ensureLogin('/pages/appointments/index');
onShow(load); onPullDownRefresh(load);
</script>
<style scoped>
.title { font-size: 36rpx; margin-top: 16rpx; }.status-scroll { margin-top: 30rpx; white-space: nowrap; }.status-list { display: inline-flex; gap: 12rpx; }.status-tab { padding: 18rpx 24rpx; border-radius: 15rpx; background: #eff3ec; font-size: 24rpx; color: #7f9181; }.active { background: #12836d; color: #fff; }.count-line { display: flex; align-items: center; justify-content: space-between; margin-top: 28rpx; }
</style>
