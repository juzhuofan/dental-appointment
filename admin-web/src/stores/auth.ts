import { ref, computed } from 'vue';
import { defineStore } from 'pinia';
import { api, TOKEN_KEY } from '../api/http';
import type { User } from '../types';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null);
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false);
  const isDoctor = computed(() => user.value?.roles.includes('DOCTOR') ?? false);
  function clear(): void {
    user.value = null;
    token.value = '';
    localStorage.removeItem(TOKEN_KEY);
  }
  async function login(username: string, password: string): Promise<void> {
    const result = await api.post<{ token: string; user: User }>('/auth/login', { username, password });
    if (!result.user.roles.some((role) => role === 'ADMIN' || role === 'DOCTOR')) {
      // 患者账号不进入诊所工作台，并清理刚创建的服务端会话。
      localStorage.setItem(TOKEN_KEY, result.token);
      try { await api.post('/auth/logout', {}); } finally { clear(); }
      throw new Error('请使用管理员或医生账号登录工作台');
    }
    token.value = result.token;
    user.value = result.user;
    localStorage.setItem(TOKEN_KEY, result.token);
  }
  async function loadUser(): Promise<void> { user.value = await api.get<User>('/me'); }
  async function logout(): Promise<void> {
    try { await api.post('/auth/logout', {}); } finally { clear(); }
  }
  return { user, token, isAdmin, isDoctor, clear, login, loadUser, logout };
});
