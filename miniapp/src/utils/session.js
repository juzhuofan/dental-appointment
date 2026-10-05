const TOKEN_KEY = 'dental.token';
const USER_KEY = 'dental.user';
const DEVICE_KEY = 'dental.demo.deviceId';
const TAB_PAGES = ['/pages/home/index', '/pages/appointments/index', '/pages/me/index'];

export const getToken = () => uni.getStorageSync(TOKEN_KEY) || '';
export const getUser = () => uni.getStorageSync(USER_KEY) || null;
export const hasSession = () => Boolean(getToken());

export function saveSession(session) {
  uni.setStorageSync(TOKEN_KEY, session.token);
  uni.setStorageSync(USER_KEY, session.user);
}

export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY);
  uni.removeStorageSync(USER_KEY);
}

export function getDeviceId() {
  let deviceId = uni.getStorageSync(DEVICE_KEY);
  if (!deviceId) {
    deviceId = `demo-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
    uni.setStorageSync(DEVICE_KEY, deviceId);
  }
  return deviceId;
}

export function navigateAfterLogin(next = '/pages/me/index') {
  // 只允许本工程中的页面路径，避免把查询参数用作外部跳转地址。
  const path = next.startsWith('/pages/') ? next : '/pages/me/index';
  if (TAB_PAGES.includes(path.split('?')[0])) {
    uni.switchTab({ url: path.split('?')[0] });
  } else {
    uni.redirectTo({ url: path });
  }
}

export function ensureLogin(next = '/pages/me/index') {
  if (hasSession()) return true;
  uni.navigateTo({ url: `/pages/login/index?next=${encodeURIComponent(next)}` });
  return false;
}
