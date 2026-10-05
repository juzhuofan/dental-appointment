<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { Calendar, Tickets, UserFilled, FirstAidKit, ArrowRight, Refresh } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { useAuthStore } from '../stores/auth';
import type { Dashboard, Appointment, PageResult } from '../types';
import { appointmentLabels, formatTime, errorText } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatusTag from '../components/StatusTag.vue';
const auth = useAuthStore();
const router = useRouter();
const data = ref<Dashboard>({ todayAppointments: 0, pendingAppointments: 0, totalPatients: 0, totalDoctors: 0, statusCounts: [], dailyTrend: [] });
const recent = ref<Appointment[]>([]);
const busy = ref(true);
const error = ref('');
const maxTrend = computed(() => Math.max(1, ...data.value.dailyTrend.map((item) => item.count)));
const statusTotal = computed(() => data.value.statusCounts.reduce((sum, item) => sum + item.count, 0));
const metrics = computed(() => [
  { title: '今日预约', value: data.value.todayAppointments, icon: Calendar, tone: 'mint', note: '今日排班中的预约' },
  { title: '待确认预约', value: data.value.pendingAppointments, icon: Tickets, tone: 'amber', note: '及时确认，减少等待' },
  { title: auth.isDoctor ? '预约患者' : '患者总数', value: data.value.totalPatients, icon: UserFilled, tone: 'blue', note: auth.isDoctor ? '本人预约的就诊人' : '已建立就诊资料' },
  { title: auth.isDoctor ? '接诊医生' : '在册医生', value: data.value.totalDoctors, icon: FirstAidKit, tone: 'violet', note: auth.isDoctor ? '本人接诊概况' : '诊所医生团队' },
]);
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try {
    const results = await Promise.all([api.get<Dashboard>('/admin/dashboard'), api.get<PageResult<Appointment>>('/admin/appointments', { page: 1, size: 5 })]);
    data.value = results[0]; recent.value = results[1].records;
  } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
onMounted(load);
</script>
<template>
  <PageHeading title="工作台" :description="`${auth.user?.displayName || '你好'}，欢迎回来。一起安排好今天的诊所工作。`"><el-button :icon="Refresh" :loading="busy" @click="load">刷新概况</el-button></PageHeading>
  <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="section-alert" />
  <div class="welcome-banner"><div><span class="banner-label">预约有序 · 接诊从容</span><h2>{{ auth.isDoctor ? '查看我的预约，开启今天的接诊。' : '每一份预约，都值得被认真安排。' }}</h2><p>{{ auth.isDoctor ? '在我的接诊中更新完成或爽约状态。' : '先发布医生排班，再确认患者预约，让就诊流程顺畅衔接。' }}</p><el-button type="primary" @click="router.push('/appointments')">{{ auth.isDoctor ? '进入我的接诊' : '查看预约' }} <el-icon><ArrowRight /></el-icon></el-button></div><div class="banner-illustration" aria-hidden="true"><div class="illustration-circle"></div><el-icon><FirstAidKit /></el-icon><span class="sparkle one">+</span><span class="sparkle two">+</span></div></div>
  <div class="metrics-grid" v-loading="busy"><article v-for="metric in metrics" :key="metric.title" class="metric-card"><div class="metric-top"><span>{{ metric.title }}</span><span class="metric-icon" :class="metric.tone"><el-icon><component :is="metric.icon" /></el-icon></span></div><strong>{{ metric.value }}</strong><p>{{ metric.note }}</p></article></div>
  <div class="dashboard-grid"><section class="panel"><div class="panel-heading"><div><h2>近 7 天预约提交趋势</h2><p>按提交日期统计预约量</p></div><span class="pill">最近 7 天</span></div><div v-if="data.dailyTrend.length" class="bar-chart" role="img" aria-label="近七日预约量柱状图"><div v-for="item in data.dailyTrend" :key="item.date" class="chart-column"><span class="chart-value">{{ item.count }}</span><div class="bar-track"><div class="chart-bar" :style="{ height: `${Math.max(item.count ? 6 : 0, item.count / maxTrend * 100)}%` }"></div></div><span class="chart-label">{{ item.date.slice(5) }}</span></div></div><el-empty v-else description="暂无趋势数据" :image-size="70"/></section><section class="panel"><div class="panel-heading"><div><h2>预约状态分布</h2><p>了解预约处理进度</p></div></div><div class="status-distribution"><div v-for="item in data.statusCounts" :key="item.status" class="distribution-row"><div><span>{{ appointmentLabels[item.status] }}</span><strong>{{ item.count }}</strong></div><el-progress :percentage="statusTotal ? Math.round(item.count / statusTotal * 100) : 0" :show-text="false" :stroke-width="8" :color="item.status === 'PENDING' ? '#d9a256' : item.status === 'CANCELLED' ? '#b4c1c0' : '#1f9688'" /></div><el-empty v-if="!data.statusCounts.length" description="暂无预约" :image-size="60"/></div></section></div>
  <section class="panel recent-panel"><div class="panel-heading"><div><h2>最近预约</h2><p>患者的最新预约记录</p></div><el-button text type="primary" @click="router.push('/appointments')">查看全部 <el-icon><ArrowRight /></el-icon></el-button></div><el-table :data="recent" v-loading="busy" empty-text="暂无预约，发布排班后即可接收患者预约"><el-table-column prop="appointmentNo" label="预约编号" min-width="170"/><el-table-column prop="patientName" label="患者" min-width="100"/><el-table-column prop="doctorName" label="医生" min-width="100"/><el-table-column label="就诊时间" min-width="160"><template #default="{ row }">{{ formatTime(row.startTime) }}</template></el-table-column><el-table-column label="状态" min-width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column></el-table></section>
</template>
