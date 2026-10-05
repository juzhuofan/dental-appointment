<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { Plus, Search, Refresh } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { usePagedList } from '../composables/usePagedList';
import type { DataRow, Department, PageResult } from '../types';
import { clinicInput, errorText, formatTime, isDismissed, toApiTime } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
import StatusTag from '../components/StatusTag.vue';

type FormValue = string | number;
type Option = { label: string; value: string | number };
type Field = { key: string; label: string; type?: 'textarea' | 'number' | 'select' | 'password' | 'datetime'; required?: boolean; max?: number; options?: Option[]; initial?: FormValue; note?: string };
type Column = { key: string; label: string; width?: number; minWidth?: number; kind?: 'enabled' | 'notice' | 'time' | 'role' };
type Config = { title: string; description: string; singular: string; fields: Field[]; columns: Column[] };
const props = defineProps<{ resource: string }>();
const enabled: Option[] = [{ label: '启用', value: 1 }, { label: '停用', value: 0 }];
const roles: Option[] = [{ label: '管理员', value: 'ADMIN' }, { label: '医生', value: 'DOCTOR' }, { label: '患者', value: 'PATIENT' }];
const departments = ref<Department[]>([]);
const doctorUsers = ref<DataRow[]>([]);
const configs = computed<Record<string, Config>>(() => ({
  departments: {
    title: '科室管理', description: '维护诊所科室与服务介绍，帮助患者找到合适的就诊方向。', singular: '科室',
    fields: [
      { key: 'name', label: '科室名称', required: true, max: 80 },
      { key: 'description', label: '科室介绍', type: 'textarea', max: 1000 },
      { key: 'sortOrder', label: '展示顺序', type: 'number', initial: 0, note: '数值越小，展示越靠前。' },
      { key: 'status', label: '使用状态', type: 'select', options: enabled, initial: 1, note: '停用会停止相关排班并取消活动预约，历史记录仍保留。' },
    ],
    columns: [{ key: 'name', label: '科室名称', minWidth: 150 }, { key: 'description', label: '科室介绍', minWidth: 280 }, { key: 'sortOrder', label: '顺序', width: 85 }, { key: 'status', label: '状态', width: 110, kind: 'enabled' }],
  },
  doctors: {
    title: '医生档案', description: '完善医生的专业信息，并关联医生账号用于查看排班与接诊。', singular: '医生',
    fields: [
      { key: 'name', label: '医生姓名', required: true, max: 80 },
      { key: 'departmentId', label: '所属科室', type: 'select', required: true, options: departments.value.map((item) => ({ label: `${item.name}${item.status === 0 ? '（停用）' : ''}`, value: item.id })) },
      { key: 'title', label: '医生职称', max: 80 },
      { key: 'specialty', label: '擅长方向', type: 'textarea', max: 500 },
      { key: 'introduction', label: '个人介绍', type: 'textarea', max: 5000 },
      { key: 'userId', label: '医生登录账号', type: 'select', options: doctorUsers.value.map((item) => ({ label: `${String(item.displayName)}（${String(item.username)}）`, value: item.id })), note: '可留空；需先在账号管理中创建医生角色账号。' },
      { key: 'avatarUrl', label: '头像地址', max: 500, note: '可选，填写可公开访问的 http/https 图片地址。' },
      { key: 'status', label: '使用状态', type: 'select', options: enabled, initial: 1, note: '停用会停止该医生的排班并取消活动预约，历史记录仍保留。' },
    ],
    columns: [{ key: 'name', label: '医生姓名', width: 130 }, { key: 'departmentName', label: '所属科室', minWidth: 130 }, { key: 'title', label: '职称', minWidth: 120 }, { key: 'specialty', label: '擅长方向', minWidth: 240 }, { key: 'status', label: '状态', width: 100, kind: 'enabled' }],
  },
  notices: {
    title: '诊所公告', description: '向小程序患者发布就诊提醒、门诊安排和诊所动态。', singular: '公告',
    fields: [
      { key: 'title', label: '公告标题', required: true, max: 120 },
      { key: 'content', label: '公告内容', type: 'textarea', required: true, max: 10000 },
      { key: 'status', label: '发布状态', type: 'select', initial: 0, options: [{ label: '草稿', value: 0 }, { label: '发布', value: 1 }, { label: '撤回', value: 2 }] },
      { key: 'publishAt', label: '发布时间', type: 'datetime', note: '留空表示立即发布（选择发布状态时）。' },
      { key: 'expireAt', label: '失效时间', type: 'datetime', note: '留空表示长期有效。' },
    ],
    columns: [{ key: 'title', label: '公告标题', minWidth: 230 }, { key: 'content', label: '内容摘要', minWidth: 230 }, { key: 'status', label: '状态', width: 100, kind: 'notice' }, { key: 'publishAt', label: '发布时间', minWidth: 160, kind: 'time' }, { key: 'expireAt', label: '失效时间', minWidth: 160, kind: 'time' }],
  },
  users: {
    title: '账号管理', description: '管理后台登录账号与角色。医生账号需在医生档案中关联后才能接诊。', singular: '账号',
    fields: [
      { key: 'username', label: '登录账号', required: true, max: 64 },
      { key: 'displayName', label: '显示姓名', required: true, max: 80 },
      { key: 'password', label: '登录密码', type: 'password', max: 72, note: '新增必填，至少 8 位；编辑时留空保留原密码。' },
      { key: 'role', label: '账号角色', type: 'select', initial: 'DOCTOR', options: roles, required: true },
      { key: 'status', label: '使用状态', type: 'select', options: enabled, initial: 1 },
    ],
    columns: [{ key: 'username', label: '登录账号', minWidth: 160 }, { key: 'displayName', label: '显示姓名', minWidth: 140 }, { key: 'role', label: '角色', minWidth: 110, kind: 'role' }, { key: 'status', label: '状态', width: 100, kind: 'enabled' }, { key: 'createdAt', label: '创建时间', minWidth: 180, kind: 'time' }],
  },
}));
const config = computed(() => configs.value[props.resource]!);
const filters = reactive({ keyword: '', status: '' as string | number, role: '' });
const { records, paging, loading, error, load } = usePagedList<DataRow>(`/admin/${props.resource}`, () => ({
  keyword: filters.keyword.trim() || undefined, status: filters.status === '' ? undefined : filters.status, role: filters.role || undefined,
}));
const form = reactive<Record<string, FormValue>>({});
const formRef = ref<FormInstance>();
const editingId = ref<number | null>(null);
const originalStatus = ref<number | null>(null);
const visible = ref(false);
const saving = ref(false);
const saveError = ref('');
const rules = computed<FormRules>(() => {
  const result: FormRules = {};
  for (const field of config.value.fields) {
    if (field.required) result[field.key] = [{ required: true, message: `请填写${field.label}`, trigger: ['blur', 'change'] }];
  }
  if (props.resource === 'users') {
    result.username = [{ required: true, message: '请输入登录账号', trigger: 'blur' }, { pattern: /^[A-Za-z0-9_.-]{3,64}$/, message: '账号为 3–64 位字母、数字、下划线、点或连字符', trigger: 'blur' }];
    result.password = [{ validator: (_rule, value: string, callback) => {
      if (!editingId.value && !value) callback(new Error('新增账号请设置登录密码'));
      else if (value && (value.length < 8 || value.length > 72 || new TextEncoder().encode(value).length > 72)) callback(new Error('密码至少 8 位，UTF-8 长度最多 72 字节'));
      else callback();
    }, trigger: 'blur' }];
  }
  if (props.resource === 'doctors') {
    result.avatarUrl = [{ validator: (_rule, value: string, callback) => {
      if (value && !/^https?:\/\//i.test(value)) callback(new Error('头像地址必须以 http:// 或 https:// 开头')); else callback();
    }, trigger: 'blur' }];
  }
  return result;
});
async function loadOptions(): Promise<void> {
  if (props.resource !== 'doctors') return;
  try {
    const results = await Promise.all([api.get<PageResult<Department>>('/admin/departments', { size: 100 }), api.get<PageResult<DataRow>>('/admin/users', { role: 'DOCTOR', size: 100 })]);
    departments.value = results[0].records;
    doctorUsers.value = results[1].records.filter((item) => item.role === 'DOCTOR' || (Array.isArray(item.roles) && item.roles.includes('DOCTOR')));
  } catch (cause) { saveError.value = errorText(cause); }
}
function open(row?: DataRow): void {
  editingId.value = row?.id ?? null;
  originalStatus.value = row ? Number(row.status) : null;
  saveError.value = '';
  for (const field of config.value.fields) {
    if (field.type === 'password') form[field.key] = '';
    else if (field.type === 'datetime') form[field.key] = clinicInput(row?.[field.key]) || '';
    else if (field.key === 'role' && row) form.role = String(row.role || (Array.isArray(row.roles) ? row.roles[0] : '') || 'PATIENT');
    else form[field.key] = (row?.[field.key] ?? field.initial ?? '') as FormValue;
  }
  visible.value = true;
  formRef.value?.clearValidate();
  void loadOptions();
}
async function save(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return;
  if (props.resource === 'notices' && form.expireAt && form.publishAt && String(form.expireAt) <= String(form.publishAt)) { saveError.value = '失效时间必须晚于发布时间'; return; }
  if (editingId.value && originalStatus.value === 1 && Number(form.status) === 0 && ['departments', 'doctors', 'users'].includes(props.resource)) {
    try {
      await ElMessageBox.confirm('停用会取消相关的活动预约，并停止该医生或科室的排班。历史记录会保留。', `停用${config.value.singular}`, { type: 'warning', confirmButtonText: '确认停用', cancelButtonText: '返回编辑' });
    } catch { return; }
  }
  saving.value = true; saveError.value = '';
  try {
    const dto: Record<string, unknown> = {};
    for (const field of config.value.fields) {
      const value = form[field.key];
      if (field.key === 'password' && value === '' && editingId.value) continue;
      dto[field.key] = field.type === 'datetime' ? toApiTime(String(value || '')) : value === '' && !field.required ? null : typeof value === 'string' && field.type !== 'password' ? value.trim() : value;
    }
    if (editingId.value) await api.put(`/admin/${props.resource}/${editingId.value}`, dto);
    else await api.post(`/admin/${props.resource}`, dto);
    ElMessage.success(`${config.value.singular}已保存`); visible.value = false; await load();
  } catch (cause) { saveError.value = errorText(cause); } finally { saving.value = false; }
}
async function remove(row: DataRow): Promise<void> {
  try {
    const impact = props.resource === 'departments' ? '所属医生将被停用，相关排班和活动预约会取消。' : props.resource === 'doctors' ? '该医生的排班和活动预约会取消。' : props.resource === 'users' ? '该账号将无法登录，关联的活动预约会取消。' : '该公告将不再向患者展示。';
    await ElMessageBox.confirm(`删除${config.value.singular}「${String(row.name || row.title || row.username)}」后，它将不再显示在常规列表中。${impact}历史记录会保留。`, `删除${config.value.singular}`, { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' });
    await api.delete(`/admin/${props.resource}/${row.id}`); ElMessage.success('已删除'); await load();
  } catch (cause) { if (!isDismissed(cause)) saveError.value = errorText(cause); }
}
function reset(): void { filters.keyword = ''; filters.status = ''; filters.role = ''; void load(true); }
function roleLabel(row: DataRow): string { const role = String(row.role || (Array.isArray(row.roles) ? row.roles[0] : '')); return roles.find((item) => item.value === role)?.label || role; }
onMounted(() => { void load(); void loadOptions(); });
</script>
<template>
  <PageHeading :title="config.title" :description="config.description"><el-button type="primary" :icon="Plus" @click="open()">新增{{ config.singular }}</el-button></PageHeading>
  <section class="panel list-panel"><div class="filter-bar"><el-input v-model="filters.keyword" :placeholder="`搜索${config.singular}名称或关键词`" :prefix-icon="Search" clearable class="filter-search" @keyup.enter="load(true)"/><el-select v-model="filters.status" clearable placeholder="全部状态" class="filter-select"><el-option v-for="item in resource === 'notices' ? configs.notices!.fields.find((f) => f.key === 'status')!.options : enabled" :key="item.value" :label="item.label" :value="item.value"/></el-select><el-select v-if="resource === 'users'" v-model="filters.role" clearable placeholder="全部角色" class="filter-select"><el-option v-for="item in roles" :key="item.value" :label="item.label" :value="item.value"/></el-select><el-button type="primary" @click="load(true)">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert"/><div class="table-caption"><span>全部{{ config.singular }} <b>{{ paging.total }}</b></span><el-button text :loading="loading" @click="load()">刷新列表</el-button></div><el-table :data="records" v-loading="loading" row-key="id" :empty-text="`暂无${config.singular}，点击右上角新增开始维护`"><el-table-column v-for="column in config.columns" :key="column.key" :prop="column.key" :label="column.label" :width="column.width" :min-width="column.minWidth" show-overflow-tooltip><template #default="{ row }"><StatusTag v-if="column.kind === 'enabled' || column.kind === 'notice'" :value="row[column.key]" :kind="column.kind"/><span v-else-if="column.kind === 'time'">{{ formatTime(row[column.key]) }}</span><span v-else-if="column.kind === 'role'">{{ roleLabel(row) }}</span><span v-else :class="{ 'cell-emphasis': column.key === 'name' || column.key === 'title' || column.key === 'username' }">{{ row[column.key] || (row[column.key] === 0 ? 0 : '—') }}</span></template></el-table-column><el-table-column label="操作" width="140" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="open(row)">编辑</el-button><el-button text type="danger" @click="remove(row)">删除</el-button></template></el-table-column></el-table><div class="pagination-row"><span class="muted">共 {{ paging.total }} 条记录</span><el-pagination v-model:current-page="paging.page" v-model:page-size="paging.size" :total="paging.total" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" @current-change="load()" @size-change="load(true)"/></div></section>
  <el-dialog v-model="visible" :title="`${editingId ? '编辑' : '新增'}${config.singular}`" width="600px" :close-on-click-modal="false" :before-close="(done: () => void) => { if (!saving) done(); }" destroy-on-close><el-alert v-if="saveError" :title="saveError" type="error" :closable="false" show-icon class="section-alert"/><el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item v-for="field in config.fields" :key="field.key" :label="field.label" :prop="field.key"><el-input-number v-if="field.type === 'number'" :model-value="Number(form[field.key])" @update:model-value="form[field.key] = $event ?? 0" :min="0" :max="99999"/><el-select v-else-if="field.type === 'select'" v-model="form[field.key]" filterable :clearable="!field.required" :placeholder="`请选择${field.label}`" class="full-width"><el-option v-for="item in field.options" :key="item.value" :value="item.value" :label="item.label"/></el-select><el-date-picker v-else-if="field.type === 'datetime'" v-model="form[field.key]" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" format="YYYY-MM-DD HH:mm" :placeholder="`选择${field.label}（北京时间）`" class="full-width"/><el-input v-else v-model="form[field.key]" :type="field.type === 'textarea' ? 'textarea' : field.type === 'password' ? 'password' : 'text'" :rows="field.key === 'content' ? 7 : 3" :maxlength="field.max" :show-word-limit="field.type === 'textarea'" :show-password="field.type === 'password'" :autocomplete="field.type === 'password' ? 'new-password' : 'off'" :placeholder="`请输入${field.label}`"/><div v-if="field.note" class="field-note">{{ field.note }}</div></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存{{ config.singular }}</el-button></template></el-dialog>
</template>
