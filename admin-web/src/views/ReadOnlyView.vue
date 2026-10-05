<script setup lang="ts">
import { computed, onMounted, reactive } from 'vue';
import { Search, Refresh } from '@element-plus/icons-vue';
import { usePagedList } from '../composables/usePagedList';
import type { DataRow } from '../types';
import { formatTime } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
const props = defineProps<{ resource: string }>();
const isPatients = computed(() => props.resource === 'patients');
const filters = reactive({ keyword: '', action: '' });
const { records, paging, loading, error, load } = usePagedList<DataRow>(`/admin/${props.resource}`, () => ({ keyword: filters.keyword.trim() || undefined, action: filters.action.trim() || undefined }));
const columns = computed(() => isPatients.value ? [
  { key: 'realName', label: '患者姓名', minWidth: 140 }, { key: 'phone', label: '联系电话', minWidth: 160 },
  { key: 'gender', label: '性别', minWidth: 90 }, { key: 'birthDate', label: '出生日期', minWidth: 120 },
  { key: 'remark', label: '资料备注', minWidth: 200 }, { key: 'createdAt', label: '创建时间', minWidth: 175 },
] : [
  { key: 'operatorName', label: '操作人', minWidth: 115 }, { key: 'operatorRole', label: '角色', minWidth: 110 },
  { key: 'action', label: '操作类型', minWidth: 150 }, { key: 'targetType', label: '对象类型', minWidth: 120 },
  { key: 'targetId', label: '对象编号', minWidth: 110 }, { key: 'summary', label: '操作说明', minWidth: 280 },
  { key: 'createdAt', label: '操作时间', minWidth: 175 },
]);
function cell(row: DataRow, key: string): string {
  if (key === 'createdAt') return formatTime(row[key]);
  if (key === 'gender') return ['未填写', '男', '女'][Number(row[key] || 0)] || '未填写';
  if (key === 'operatorRole') return ({ ADMIN: '管理员', DOCTOR: '医生', PATIENT: '患者' } as Record<string, string>)[String(row[key])] || String(row[key] || '—');
  return String(row[key] ?? '—') || '—';
}
function reset(): void { filters.keyword = ''; filters.action = ''; void load(true); }
onMounted(() => { void load(); });
</script>
<template>
  <PageHeading :title="isPatients ? '患者资料' : '操作日志'" :description="isPatients ? '查看患者自主维护的就诊资料，列表中的联系电话已脱敏。' : '记录关键业务操作，便于追溯预约、排班与基础资料的变更。'"><el-button :icon="Refresh" :loading="loading" @click="load()">刷新列表</el-button></PageHeading>
  <section class="panel list-panel"><div class="filter-bar"><el-input v-model="filters.keyword" :placeholder="isPatients ? '搜索患者姓名或手机号' : '搜索操作人或操作说明'" :prefix-icon="Search" clearable class="filter-search" @keyup.enter="load(true)"/><el-input v-if="!isPatients" v-model="filters.action" placeholder="操作类型，如 CREATE" clearable class="filter-select" @keyup.enter="load(true)"/><el-button type="primary" @click="load(true)">查询</el-button><el-button @click="reset">重置</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert"/><div class="table-caption"><span>{{ isPatients ? '患者资料' : '操作记录' }} <b>{{ paging.total }}</b></span><span class="muted">{{ isPatients ? '资料由患者在小程序中维护' : '关键操作保留完整历史' }}</span></div><el-table :data="records" v-loading="loading" row-key="id" :empty-text="isPatients ? '暂无患者资料' : '暂无操作记录'"><el-table-column v-for="column in columns" :key="column.key" :label="column.label" :min-width="column.minWidth" show-overflow-tooltip><template #default="{ row }"><span :class="{ 'cell-emphasis': column.key === 'realName' || column.key === 'operatorName' }">{{ cell(row, column.key) }}</span></template></el-table-column></el-table><div class="pagination-row"><span class="muted">共 {{ paging.total }} 条记录</span><el-pagination v-model:current-page="paging.page" v-model:page-size="paging.size" :total="paging.total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div></section>
</template>
