<script setup lang="ts">
import { computed } from 'vue';
import { appointmentLabels, scheduleLabels } from '../utils/format';
const props = defineProps<{ value: unknown; kind?: 'appointment' | 'schedule' | 'enabled' | 'notice' }>();
const label = computed(() => {
  if (props.kind === 'enabled') return Number(props.value) === 1 ? '启用' : '停用';
  if (props.kind === 'notice') return ['草稿', '已发布', '已撤回'][Number(props.value)] || '未知';
  const labels = props.kind === 'schedule' ? scheduleLabels : appointmentLabels;
  return labels[String(props.value) as keyof typeof labels] || String(props.value);
});
const tone = computed(() => {
  const value = String(props.value);
  if (['CONFIRMED', 'PUBLISHED', 'COMPLETED', '1'].includes(value)) return 'success';
  if (['PENDING', 'DRAFT', '0'].includes(value)) return 'warning';
  if (value === 'NO_SHOW') return 'danger';
  return 'info';
});
</script>
<template><el-tag :type="tone" effect="light" round>{{ label }}</el-tag></template>
