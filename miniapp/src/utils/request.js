import { clearSession, getToken } from './session';

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080/api/v1').replace(/\/$/, '');
let redirecting = false;

/** 保留平台网络原因和目标地址，便于排查真机网络；不记录请求头或登录凭据。 */
export function createNetworkError(detail, url) {
  const errMsg = detail?.errMsg || detail?.message || '网络请求失败';
  let hint = '请确认手机与电脑连接同一 Wi-Fi、后端已启动且端口允许访问';
  if (/\/\/(localhost|127\.0\.0\.1)(:|\/)/i.test(url)) {
    hint = '此地址指向当前设备本身，真机需改为电脑的局域网地址并连接同一 Wi-Fi';
  } else if (/domain|合法域名|not in.*list/i.test(errMsg)) {
    hint = '请检查小程序服务器域名配置，真机调试需开启开发工具的“不校验合法域名”';
  } else if (/timeout|超时/i.test(errMsg)) {
    hint = '请求超时，请检查网络、服务端口和电脑防火墙';
  }
  const error = new Error(`暂时连接不到诊所服务（${API_BASE_URL}）。${hint}。微信返回：${errMsg}`);
  error.code = detail?.errCode || detail?.code || 'NETWORK_ERROR';
  error.errMsg = errMsg;
  error.url = url;
  return error;
}

export function notifyError(error) {
  if (error?.status === 401) return;
  uni.showToast({ title: error?.message || '操作未完成，请稍后重试', icon: 'none', duration: 2500 });
}

export function request(path, options = {}) {
  const { method = 'GET', data, query, auth = true, redirectOn401 = true, headers = {} } = options;
  const search = Object.entries(query || {})
    .filter(([, value]) => value !== '' && value !== undefined && value !== null)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}${search ? `?${search}` : ''}`,
      method,
      data: method === 'GET' ? undefined : { data: data ?? {} },
      header: { 'Content-Type': 'application/json', ...(auth && token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
      timeout: 15000,
      success(response) {
        const body = response.data;
        if (response.statusCode === 401 && auth) {
          clearSession({ expired: true });
          if (redirectOn401 && !redirecting) {
            redirecting = true;
            uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
            const pages = getCurrentPages();
            const current = pages[pages.length - 1];
            const params = Object.entries(current?.options || current?.$page?.options || {})
              .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&');
            const next = current?.route ? `/${current.route}${params ? `?${params}` : ''}` : '/pages/me/index';
            if (current?.route !== 'pages/login/index') {
              uni.navigateTo({ url: `/pages/login/index?next=${encodeURIComponent(next)}`, complete: () => { redirecting = false; } });
            } else redirecting = false;
          }
        }
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 'OK') {
          resolve(body.data);
        } else {
          const error = new Error(body?.message || `请求失败（${response.statusCode}）`);
          error.code = body?.code;
          error.status = response.statusCode;
          error.traceId = body?.traceId;
          reject(error);
        }
      },
      fail(error) {
        reject(createNetworkError(error, `${API_BASE_URL}${path}`));
      }
    });
  });
}

export const api = {
  clinic: () => request('/clinic', { auth: false }),
  departments: () => request('/departments', { auth: false, query: { page: 1, size: 100 } }),
  doctors: (query = {}) => request('/doctors', { auth: false, query: { page: 1, size: 100, ...query } }),
  doctor: id => request(`/doctors/${id}`, { auth: false }),
  schedules: (query = {}) => request('/schedules', { auth: false, query: { page: 1, size: 100, ...query } }),
  schedule: id => request(`/schedules/${id}`, { auth: false }),
  notices: () => request('/notices', { auth: false, query: { page: 1, size: 100 } }),
  wechatConfig: () => request('/auth/wechat-config', { auth: false, redirectOn401: false }),
  wechatLogin: data => request('/auth/wechat-login', { method: 'POST', auth: false, redirectOn401: false, data }),
  me: (options = {}) => request('/me', options),
  saveAccountProfile: data => request('/me/account-profile', { method: 'PUT', data }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  profile: () => request('/me/patient-profile'),
  saveProfile: data => request('/me/patient-profile', { method: 'PUT', data }),
  profiles: () => request('/me/patient-profiles'),
  createProfile: data => request('/me/patient-profiles', { method: 'POST', data }),
  updateProfile: (id, data) => request(`/me/patient-profiles/${id}`, { method: 'PUT', data }),
  setDefaultProfile: id => request(`/me/patient-profiles/${id}/default`, { method: 'PUT' }),
  deleteProfile: id => request(`/me/patient-profiles/${id}`, { method: 'DELETE' }),
  appointments: (query = {}) => request('/appointments/me', { query: { page: 1, size: 100, ...query } }),
  appointment: id => request(`/appointments/${id}`),
  createAppointment: (data, idempotencyKey) => request('/appointments', { method: 'POST', data, headers: { 'Idempotency-Key': idempotencyKey } }),
  cancelAppointment: (id, reason) => request(`/appointments/${id}/cancel`, { method: 'POST', data: { reason } })
};
