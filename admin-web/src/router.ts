import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from './stores/auth';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true, title: '登录' } },
    {
      path: '/', component: () => import('./layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', component: () => import('./views/DashboardView.vue'), meta: { title: '工作台' } },
        { path: 'appointments', component: () => import('./views/AppointmentsView.vue'), meta: { title: '预约管理' } },
        { path: 'schedules', component: () => import('./views/SchedulesView.vue'), meta: { title: '医生排班' } },
        ...['departments', 'doctors', 'notices', 'users'].map((resource, index) => ({
          path: resource, component: () => import('./views/ManagementView.vue'), props: { resource },
          meta: { title: ['科室管理', '医生档案', '诊所公告', '账号管理'][index], adminOnly: true },
        })),
        { path: 'patients', component: () => import('./views/ReadOnlyView.vue'), props: { resource: 'patients' }, meta: { title: '患者资料', adminOnly: true } },
        { path: 'logs', component: () => import('./views/ReadOnlyView.vue'), props: { resource: 'operation-logs' }, meta: { title: '操作日志', adminOnly: true } },
        { path: 'settings', component: () => import('./views/SettingsView.vue'), meta: { title: '诊所设置', adminOnly: true } },
        { path: 'password', component: () => import('./views/PasswordView.vue'), meta: { title: '修改密码' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
});

router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || '工作台')} · 微笑口腔`;
  if (to.meta.public) return true;
  const auth = useAuthStore();
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } };
  if (!auth.user) {
    try { await auth.loadUser(); } catch { auth.clear(); return '/login'; }
  }
  if (!auth.isAdmin && !auth.isDoctor) { auth.clear(); return '/login'; }
  if (to.meta.adminOnly && !auth.isAdmin) return '/dashboard';
  return true;
});
