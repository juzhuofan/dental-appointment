<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { Check, Refresh, FirstAidKit } from '@element-plus/icons-vue';
import { api } from '../api/http';
import type { Clinic } from '../types';
import { errorText } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
const form = reactive<Clinic>({ name: '', phone: '', address: '', openingHours: '', introduction: '', cancelBeforeMinutes: 120 });
const formRef = ref<FormInstance>();
const busy = ref(false);
const saving = ref(false);
const error = ref('');
const rules: FormRules = {
  name: [{ required: true, message: '请填写诊所名称', trigger: 'blur' }],
  phone: [{ required: true, message: '请填写联系电话', trigger: 'blur' }],
  address: [{ required: true, message: '请填写诊所地址', trigger: 'blur' }],
  openingHours: [{ required: true, message: '请填写营业时间', trigger: 'blur' }],
  introduction: [{ required: true, whitespace: true, message: '请填写诊所介绍', trigger: 'blur' }],
  cancelBeforeMinutes: [{ required: true, type: 'number', min: 0, max: 10080, message: '提前取消时间范围为 0–10080 分钟', trigger: 'change' }],
};
async function load(): Promise<void> {
  busy.value = true; error.value = '';
  try { Object.assign(form, await api.get<Clinic>('/admin/system-config')); } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
async function save(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return;
  saving.value = true; error.value = '';
  try { await api.put('/admin/system-config', { ...form }); ElMessage.success('诊所设置已保存'); }
  catch (cause) { error.value = errorText(cause); } finally { saving.value = false; }
}
onMounted(load);
</script>
<template>
  <PageHeading title="诊所设置" description="维护小程序展示的诊所信息与预约取消规则。"><el-button :icon="Refresh" :disabled="saving" :loading="busy" @click="load">重新加载</el-button></PageHeading>
  <div class="settings-grid"><section class="panel settings-form" v-loading="busy"><div class="panel-heading"><div><h2>诊所基础信息</h2><p>保存后同步到患者小程序首页</p></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert"/><el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save"><el-form-item label="诊所名称" prop="name"><el-input v-model="form.name" maxlength="100" placeholder="请输入诊所名称"/></el-form-item><div class="form-grid"><el-form-item label="联系电话" prop="phone"><el-input v-model="form.phone" maxlength="30" placeholder="请输入诊所联系电话"/></el-form-item><el-form-item label="营业时间" prop="openingHours"><el-input v-model="form.openingHours" maxlength="100" placeholder="例如 周一至周日 09:00–18:00"/></el-form-item></div><el-form-item label="诊所地址" prop="address"><el-input v-model="form.address" maxlength="300" placeholder="请输入详细地址"/></el-form-item><el-form-item label="诊所介绍" prop="introduction"><el-input v-model="form.introduction" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="向患者介绍诊所和服务特色"/></el-form-item><div class="section-divider"></div><h3>预约取消规则</h3><el-form-item label="患者需提前多少分钟取消预约" prop="cancelBeforeMinutes"><el-input-number v-model="form.cancelBeforeMinutes" :min="0" :max="10080" :step="30"/><div class="field-note">默认 120 分钟；用于新建排班的默认值，已有排班的取消提前量按其自身设置执行。</div></el-form-item><el-button type="primary" native-type="submit" :icon="Check" :loading="saving" :disabled="busy">保存诊所设置</el-button></el-form></section><aside class="settings-preview"><div class="preview-heading"><span class="eyebrow">PATIENT VIEW</span><h3>患者端信息预览</h3></div><div class="preview-clinic"><div class="preview-icon"><el-icon><FirstAidKit /></el-icon></div><h2>{{ form.name || '诊所名称' }}</h2><p>{{ form.introduction || '完善诊所介绍，让患者更了解您的诊所。' }}</p><div class="preview-info"><span>营业时间</span><strong>{{ form.openingHours || '待填写' }}</strong><span>联系电话</span><strong>{{ form.phone || '待填写' }}</strong><span>诊所地址</span><strong>{{ form.address || '待填写' }}</strong></div></div><div class="soft-note">诊所介绍仅展示服务信息；患者预约以发布的医生排班为准。</div></aside></div>
</template>
