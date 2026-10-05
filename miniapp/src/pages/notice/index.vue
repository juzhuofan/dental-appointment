<template>
  <view class="page"><view v-if="loading" class="loading">正在加载公告…</view><view v-else-if="error" class="error-card">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view><view v-else-if="notice" class="card notice-card"><text class="eyebrow">CLINIC NOTICE</text><view class="notice-title">{{ notice.title }}</view><view class="muted small top-space">{{ dateText(notice.publishAt || notice.createdAt, true) }} · 诊所公告</view><view class="divider" /><text class="notice-body">{{ notice.content }}</text></view><view v-else class="hint">该公告已撤回或暂时不可查看，请返回首页查看最新信息。</view></view>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { api } from '../../utils/request';
import { dateText } from '../../utils/format';
const notice = ref(null); const loading = ref(true); const error = ref(''); let id = '';
async function load() { loading.value = true; error.value = ''; try { const result = await api.notices(); notice.value = result.records.find(item => String(item.id) === String(id)) || null; } catch (e) { error.value = e.message; } finally { loading.value = false; } }
onLoad(options => { id = options.id; load(); });
</script>
<style scoped>
.notice-card { padding: 38rpx 32rpx; }.notice-title { font-size: 39rpx; font-weight: 600; line-height: 1.5; margin-top: 20rpx; }.notice-body { display: block; color: #576e5e; font-size: 29rpx; line-height: 1.95; white-space: pre-wrap; word-break: break-word; }
</style>
