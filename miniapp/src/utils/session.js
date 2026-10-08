import { reactive } from 'vue';

const TOKEN_KEY = 'dental.token';
const USER_KEY = 'dental.user';
const TAB_PAGES = ['/pages/home/index', '/pages/appointments/index', '/pages/me/index'];

export const authState = reactive({
  ready: false,
  needsBootstrap: false,
  status: 'idle',
  user: null,
  config: { enabled: false, phoneNumberEnabled: false },
  dialogVisible: false,
  privacyRequired: false,
  privacyContractName: '',
  error: '',
  busy: false,
  justLoggedIn: false
});

export const getToken = () => uni.getStorageSync(TOKEN_KEY) || '';
export const getUser = () => authState.user || uni.getStorageSync(USER_KEY) || null;
export const isWechatProfileComplete = user => user?.wechatBound === true && user?.profileCompleted === true;
export const hasSession = () => authState.status === 'authenticated' && Boolean(getToken())
  && isWechatProfileComplete(getUser());

export function saveUser(user) {
  uni.setStorageSync(USER_KEY, user);
  authState.user = user;
  authState.status = isWechatProfileComplete(user) ? 'authenticated' : 'incomplete';
}

export function saveSession(session) {
  uni.setStorageSync(TOKEN_KEY, session.token);
  saveUser(session.user);
  authState.justLoggedIn = true;
}

export function clearSession({ expired = false } = {}) {
  uni.removeStorageSync(TOKEN_KEY);
  uni.removeStorageSync(USER_KEY);
  authState.user = null;
  authState.status = 'guest';
  authState.needsBootstrap = expired;
  authState.justLoggedIn = false;
}

export function navigateAfterLogin(next = '/pages/me/index') {
  const path = typeof next === 'string' && next.startsWith('/pages/') ? next : '/pages/me/index';
  if (TAB_PAGES.includes(path.split('?')[0])) {
    uni.switchTab({ url: path.split('?')[0] });
  } else {
    uni.redirectTo({ url: path });
  }
}
