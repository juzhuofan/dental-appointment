<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { Lock } from '@element-plus/icons-vue';
import { api } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { errorText } from '../utils/format';
import PageHeading from '../components/PageHeading.vue';
const auth = useAuthStore();
const router = useRouter();
const form = reactive({ oldPassword: '', newPassword: '', confirmation: '' });
const formRef = ref<FormInstance>();
const busy = ref(false);
const error = ref('');
const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { validator: (_rule, value: string, callback) => {
    if (value.length < 8 || new TextEncoder().encode(value).length > 72) callback(new Error('新密码至少 8 位，UTF-8 长度最多 72 字节'));
    else if (value === form.oldPassword) callback(new Error('新密码不能与当前密码相同')); else callback();
  }, trigger: 'blur' }],
  confirmation: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }, { validator: (_rule, value: string, callback) => value === form.newPassword ? callback() : callback(new Error('两次输入的新密码不一致')), trigger: 'blur' }],
};
async function save(): Promise<void> {
  if (!await formRef.value?.validate().catch(() => false)) return;
  busy.value = true; error.value = '';
  try {
    await api.post('/me/password', { oldPassword: form.oldPassword, newPassword: form.newPassword });
    ElMessage.success('密码已更新，请重新登录'); auth.clear(); await router.replace('/login');
  } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
</script>
<template>
  <PageHeading title="修改密码" description="使用新的登录密码保护您的诊所工作账号。"/>
  <section class="panel password-panel"><div class="password-intro"><span class="metric-icon mint"><el-icon><Lock /></el-icon></span><div><h2>更新登录密码</h2><p>修改成功后，请使用新密码重新登录。</p></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="section-alert"/><el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save"><el-form-item label="当前密码" prop="oldPassword"><el-input v-model="form.oldPassword" type="password" show-password autocomplete="current-password" placeholder="请输入当前密码" maxlength="72"/></el-form-item><el-form-item label="新密码" prop="newPassword"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" placeholder="请输入至少 8 位的新密码" maxlength="72"/></el-form-item><el-form-item label="确认新密码" prop="confirmation"><el-input v-model="form.confirmation" type="password" show-password autocomplete="new-password" placeholder="请再次输入新密码" maxlength="72"/></el-form-item><el-button type="primary" native-type="submit" :loading="busy">更新密码</el-button></el-form></section>
</template>
