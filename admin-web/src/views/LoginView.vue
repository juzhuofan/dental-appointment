<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { FirstAidKit, User, Lock, ArrowRight, CircleCheckFilled } from '@element-plus/icons-vue';
import type { FormInstance, FormRules } from 'element-plus';
import { useAuthStore } from '../stores/auth';
import { errorText } from '../utils/format';
const form = reactive({ username: '', password: '' });
const formRef = ref<FormInstance>();
const rules: FormRules = { username: [{ required: true, message: '请输入登录账号', trigger: 'blur' }], password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }] };
const busy = ref(false);
const error = ref('');
const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
async function login(): Promise<void> {
  if (busy.value || !await formRef.value?.validate().catch(() => false)) return;
  busy.value = true;
  error.value = '';
  try {
    await auth.login(form.username.trim(), form.password);
    const redirect = String(route.query.redirect || '/dashboard');
    await router.replace(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/dashboard');
  } catch (cause) { error.value = errorText(cause); } finally { busy.value = false; }
}
</script>
<template>
  <div class="login-page">
    <section class="login-story"><div class="login-brand"><el-icon><FirstAidKit /></el-icon> 微笑口腔</div><div class="login-story-content"><span class="eyebrow">A BETTER CLINIC DAY</span><h1>有序预约，<br />安心每一次就诊。</h1><p>把预约、排班与接诊连接起来，<br />让诊所的每一天井然有序。</p><div class="tooth-art" aria-hidden="true"><svg viewBox="0 0 260 280"><circle cx="130" cy="135" r="105" fill="#fff" opacity=".06"/><circle cx="130" cy="135" r="83" fill="none" stroke="#a7d8cf" stroke-dasharray="4 8" opacity=".5"/><path d="M81 64c18-17 31-1 49-1s35-16 53 1c29 28 3 71-4 98-5 21-9 46-24 46-12 0-11-60-25-60s-13 60-25 60c-15 0-19-25-24-46-7-27-33-70 0-98Z" fill="#effbf7"/><path d="M92 72c12-8 20 0 37 2" fill="none" stroke="#a6d8d2" stroke-width="8" stroke-linecap="round"/><path d="M183 37v29M168 51h30" stroke="#f4c8a2" stroke-width="5" stroke-linecap="round"/></svg></div><div class="login-features"><span><el-icon><CircleCheckFilled /></el-icon>清晰的号源管理</span><span><el-icon><CircleCheckFilled /></el-icon>统一的接诊流程</span></div></div><span class="login-copyright">DENTAL APPOINTMENT · 诊所管理平台</span></section>
    <section class="login-form-area"><div class="login-form-card"><span class="eyebrow">WELCOME BACK</span><h2>登录诊所工作台</h2><p class="muted">欢迎回来，请使用管理员或医生账号登录。</p><el-alert v-if="error" :title="error" type="error" :closable="false" show-icon class="form-alert"/><el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @submit.prevent="login"><el-form-item label="登录账号" prop="username"><el-input v-model="form.username" :prefix-icon="User" placeholder="请输入账号" autocomplete="username" maxlength="64"/></el-form-item><el-form-item label="登录密码" prop="password"><el-input v-model="form.password" type="password" show-password :prefix-icon="Lock" placeholder="请输入密码" autocomplete="current-password" maxlength="72"/></el-form-item><el-button class="login-submit" type="primary" native-type="submit" :loading="busy">登录工作台 <el-icon><ArrowRight /></el-icon></el-button></el-form><p class="login-note">本平台供诊所管理员与医生使用。患者请通过微信小程序预约就诊。</p></div></section>
  </div>
</template>
