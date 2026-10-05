<template>
  <view class="page"><view class="eyebrow">OUR DOCTORS</view><view class="title top-space">为你找到合适的医生</view><view class="search-box"><text class="search-icon">⌕</text><input v-model="keyword" class="input" placeholder="搜索医生姓名或擅长方向" confirm-type="search" @confirm="load" /><text v-if="keyword" class="link" @click="clearSearch">清除</text></view><scroll-view class="department-scroll" scroll-x><view class="filter-list"><view :class="['filter', { active: !departmentId }]" @click="chooseDepartment('')">全部科室</view><view v-for="department in departments" :key="department.id" :class="['filter', { active: String(departmentId) === String(department.id) }]" @click="chooseDepartment(department.id)">{{ department.name }}</view></view></scroll-view><view class="row between result-line"><text class="muted">{{ doctors.length }} 位医生</text><text class="link" @click="load">刷新</text></view><view v-if="loading" class="loading">正在查找医生…</view><view v-else-if="error" class="error-card">{{ error }}<button class="btn btn-outline btn-small" @click="load">重新加载</button></view><template v-else><DoctorCard v-for="doctor in doctors" :key="doctor.id" :doctor="doctor" @select="openDoctor" /><EmptyState v-if="!doctors.length" title="暂未找到合适的医生" description="试试其他科室或搜索词" /></template></view>
</template>
<script setup>
import { ref } from 'vue';
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app';
import { api } from '../../utils/request';
import DoctorCard from '../../components/DoctorCard.vue';
import EmptyState from '../../components/EmptyState.vue';
const keyword = ref(''); const departmentId = ref(''); const doctors = ref([]); const departments = ref([]); const loading = ref(true); const error = ref(''); let generation = 0;
async function load() {
  const current = ++generation; loading.value = true; error.value = '';
  try { const result = await api.doctors({ departmentId: departmentId.value, keyword: keyword.value.trim() }); if (current === generation) doctors.value = result.records; }
  catch (e) { if (current === generation) error.value = e.message; }
  finally { if (current === generation) loading.value = false; uni.stopPullDownRefresh(); }
}
function chooseDepartment(id) { departmentId.value = id; load(); }
function clearSearch() { keyword.value = ''; load(); }
const openDoctor = doctor => uni.navigateTo({ url: `/pages/doctor/index?id=${doctor.id}` });
onLoad(async options => { departmentId.value = options.departmentId || ''; load(); try { departments.value = (await api.departments()).records; } catch (e) { error.value = e.message; } });
onPullDownRefresh(load);
</script>
<style scoped>
.title { font-size: 36rpx; margin-top: 16rpx; }.search-box { background: #fff; border: 1rpx solid #e6ece4; border-radius: 18rpx; padding: 15rpx 23rpx; margin-top: 28rpx; display: flex; align-items: center; gap: 15rpx; }.input { font-size: 25rpx; flex: 1; }.search-icon { font-size: 44rpx; color: #8b9e8f; }.department-scroll { margin-top: 28rpx; white-space: nowrap; }.filter-list { display: inline-flex; gap: 18rpx; }.filter { padding: 18rpx 23rpx; border-radius: 15rpx; background: #eff3ee; color: #819080; font-size: 24rpx; }.active { color: #fff; background: #12836d; }.result-line { margin-top: 26rpx; }
</style>
