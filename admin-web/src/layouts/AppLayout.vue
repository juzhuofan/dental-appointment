<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Calendar, Grid, Tickets, OfficeBuilding, User, UserFilled, Bell, Document, Setting, Lock, SwitchButton, ArrowDown, FirstAidKit, Menu } from '@element-plus/icons-vue';
import { useAuthStore } from '../stores/auth';
const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const sidebarOpen = ref(false);
const navigationGroups = computed(() => [
  { label: '工作概览', items: [{ path: '/dashboard', title: '工作台', icon: Grid }] },
  { label: '诊疗运营', items: [
    { path: '/appointments', title: auth.isDoctor ? '我的接诊' : '预约管理', icon: Tickets },
    { path: '/schedules', title: auth.isDoctor ? '我的排班' : '医生排班', icon: Calendar },
    { path: '/doctor-leaves', title: '请假管理', icon: Calendar },
  ] },
  ...(auth.isAdmin ? [
    { label: '诊所资料', items: [
      { path: '/departments', title: '科室管理', icon: OfficeBuilding },
      { path: '/doctors', title: '医生档案', icon: FirstAidKit },
      { path: '/patients', title: '患者资料', icon: UserFilled },
      { path: '/notices', title: '诊所公告', icon: Bell },
    ] },
    { label: '系统管理', items: [
      { path: '/users', title: '账号管理', icon: User },
      { path: '/logs', title: '操作日志', icon: Document },
      { path: '/settings', title: '诊所设置', icon: Setting },
    ] },
  ] : []),
]);
const date = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());
watch(() => route.path, () => { sidebarOpen.value = false; });
async function command(value: string): Promise<void> {
  if (value === 'password') { await router.push('/password'); return; }
  try { await auth.logout(); } catch { /* 即使服务离线，本地会话仍已清除。 */ }
  await router.replace('/login');
}
</script>
<template>
  <div class="app-shell" @keyup.esc="sidebarOpen = false">
    <a class="skip-link" href="#workspace">跳转到页面内容</a>
    <div v-if="sidebarOpen" class="sidebar-scrim" @click="sidebarOpen = false" />
    <aside id="admin-sidebar" class="sidebar" :class="{ 'sidebar--open': sidebarOpen }">
      <router-link to="/dashboard" class="brand" @click="sidebarOpen = false"><span class="brand-mark"><el-icon><FirstAidKit /></el-icon></span><span>微笑口腔<small>CLINIC CONSOLE</small></span></router-link>
      <nav aria-label="管理菜单"><div v-for="group in navigationGroups" :key="group.label" class="nav-group"><p class="nav-caption">{{ group.label }}</p><router-link v-for="item in group.items" :key="item.path" :to="item.path" class="nav-item" :class="{ active: route.path === item.path }" :aria-current="route.path === item.path ? 'page' : undefined" @click="sidebarOpen = false"><el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span></router-link></div></nav>
      <div class="sidebar-footer"><span class="status-dot"></span><div>让每一次就诊更有序<small>预约 · 接诊 · 诊所管理</small></div></div>
    </aside>
    <div class="main-shell">
      <header class="topbar"><div class="topbar-leading"><button class="menu-button" type="button" aria-label="打开管理菜单" aria-controls="admin-sidebar" :aria-expanded="sidebarOpen" @click="sidebarOpen = true"><el-icon><Menu /></el-icon></button><div class="breadcrumb"><span class="breadcrumb-home">诊所管理</span><span class="breadcrumb-divider">/</span><strong>{{ route.meta.title }}</strong></div></div><div class="topbar-right"><span class="header-date">{{ date }}</span><el-dropdown @command="command"><button class="profile-button" type="button"><span class="avatar"><img v-if="auth.user?.avatarDisplayUrl || auth.user?.avatarUrl" :src="auth.user.avatarDisplayUrl || auth.user.avatarUrl || ''" alt="账号头像"/><template v-else>{{ auth.user?.displayName?.slice(0, 1) || '医' }}</template></span><span>{{ auth.user?.displayName }}<small>{{ auth.isAdmin ? '诊所管理员' : '接诊医生' }}</small></span><el-icon><ArrowDown /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item command="password" :icon="Lock">修改密码</el-dropdown-item><el-dropdown-item command="logout" divided :icon="SwitchButton">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></header>
      <main id="workspace" class="workspace"><router-view :key="route.path" /></main>
      <footer class="app-footer">微笑 · 口腔门诊预约系统 <span>时间统一展示为北京时间</span></footer>
    </div>
  </div>
</template>
