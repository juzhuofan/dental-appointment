import { API_BASE_URL, api, createNetworkError } from './request';
import { authState, clearSession, getToken, getUser, hasSession, saveSession, saveUser } from './session';
import { getPrivacyRequirement, getWechatLoginCode, installPrivacyListener, isWechatMiniProgram } from './wechat';

let bootstrapPromise = null;
let navigatingToLogin = false;
let lastValidatedAt = 0;
const FOREGROUND_VALIDATION_INTERVAL = 5 * 60 * 1000;

export function openLoginDialog() {
  authState.dialogVisible = true;
}

export function dismissLoginDialog() {
  authState.dialogVisible = false;
}

export async function refreshPrivacyRequirement() {
  const privacy = await getPrivacyRequirement();
  authState.privacyRequired = privacy.needAuthorization === true;
  authState.privacyContractName = privacy.privacyContractName || '微信隐私保护指引';
  return privacy;
}

/** 启动只检查业务会话；未登录时由用户点击微信登录，不自动取得微信 code。 */
export function bootstrapAuth({ retry = false } = {}) {
  if (bootstrapPromise && ((!retry && !authState.needsBootstrap) || !authState.ready)) return bootstrapPromise;
  const bootstrapToken = getToken();
  authState.ready = false;
  authState.needsBootstrap = false;
  authState.status = 'checking';
  authState.error = '';
  if (!getToken()) openLoginDialog();
  installPrivacyListener(() => {
    authState.privacyRequired = true;
    if (!hasSession()) openLoginDialog();
  });
  bootstrapPromise = (async () => {
    try {
      authState.config = await api.wechatConfig();
      if (!isWechatMiniProgram()) throw new Error('请在微信小程序中使用微信登录，网页端可浏览诊所信息');
      if (!authState.config.enabled) throw new Error('诊所暂未启用微信登录，请稍后再试');
      const token = getToken();
      if (token) {
        try {
          const user = await api.me({ redirectOn401: false });
          // A newer login or logout owns the current session, even if this request finishes later.
          if (token !== getToken()) return hasSession() ? getUser() : null;
          if (user.wechatBound === true) {
            saveUser(user);
            lastValidatedAt = Date.now();
            authState.dialogVisible = !hasSession();
            return user;
          }
          clearSession();
        } catch (error) {
          if (error.staleSession || (getToken() && token !== getToken())) return hasSession() ? getUser() : null;
          if (error.status === 401) clearSession();
          else throw error;
        }
      }
      clearSession();
      openLoginDialog();
      return null;
    } catch (error) {
      if (bootstrapToken !== getToken()) return hasSession() ? getUser() : null;
      authState.status = 'guest';
      authState.error = error.message || '登录状态确认失败，请重试';
      openLoginDialog();
      return null;
    } finally {
      authState.ready = true;
      authState.needsBootstrap = false;
    }
  })();
  return bootstrapPromise;
}

/** 重新进入前台时，按间隔刷新 /me 和头像展示链接；手动退出不立即自动登录。 */
export function resumeAuth() {
  if (!authState.ready || authState.needsBootstrap) return bootstrapAuth();
  if (hasSession() && Date.now() - lastValidatedAt >= FOREGROUND_VALIDATION_INTERVAL) {
    return bootstrapAuth({ retry: true });
  }
  return bootstrapPromise || bootstrapAuth();
}

export function uploadAvatar(filePath) {
  if (!filePath || !getToken()) return Promise.reject(new Error('请先完成微信身份验证并选择头像'));
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/me/avatar`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${token}` },
      timeout: 60000,
      success(response) {
        if (token !== getToken()) {
          const error = new Error('登录状态已变化，请重新打开资料编辑后重试');
          error.staleSession = true;
          reject(error);
          return;
        }
        let body;
        try { body = typeof response.data === 'string' ? JSON.parse(response.data) : response.data; }
        catch { reject(new Error('头像上传响应格式不正确，请重试')); return; }
        if (response.statusCode >= 200 && response.statusCode < 300 && body?.code === 'OK') {
          saveUser(body.data);
          resolve(body.data);
        } else {
          const error = new Error(body?.message || '头像上传失败，请重试');
          error.status = response.statusCode;
          error.code = body?.code;
          if (response.statusCode === 401) clearSession({ expired: true });
          reject(error);
        }
      },
      fail(error) { reject(createNetworkError(error, `${API_BASE_URL}/me/avatar`)); }
    });
  });
}

/** 微信身份验证成功即完成登录；昵称和头像由用户在个人中心自选。 */
export async function loginWithWechat() {
  await bootstrapAuth();
  if (authState.busy) return null;
  if (!isWechatMiniProgram()) throw new Error('请在微信小程序中使用微信登录');
  if (!authState.config.enabled) throw new Error('诊所暂未启用微信登录');
  if (authState.privacyRequired) throw new Error('请先同意微信隐私保护指引');
  authState.busy = true;
  authState.error = '';
  try {
    saveSession(await api.wechatLogin({ loginCode: await getWechatLoginCode(), register: true }));
    if (!hasSession()) throw new Error('微信身份验证尚未完成，请重试');
    lastValidatedAt = Date.now();
    dismissLoginDialog();
    return getUser();
  } catch (error) {
    authState.error = error.message || '微信登录未完成，请重试';
    throw error;
  } finally {
    authState.busy = false;
  }
}

export async function ensureLogin(next = '/pages/me/index') {
  await bootstrapAuth();
  if (hasSession()) return true;
  openLoginDialog();
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
  if (pages[pages.length - 1]?.route !== 'pages/login/index' && !navigatingToLogin) {
    navigatingToLogin = true;
    uni.navigateTo({
      url: `/pages/login/index?next=${encodeURIComponent(next)}`,
      complete() { navigatingToLogin = false; }
    });
  }
  return false;
}
