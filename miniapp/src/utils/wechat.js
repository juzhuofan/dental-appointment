let privacyListenerInstalled = false;
let privacyCallback = null;
let pendingPrivacyResolve = null;

export function isWechatMiniProgram() {
  let supported = false;
  // #ifdef MP-WEIXIN
  supported = typeof wx !== 'undefined' && typeof uni.login === 'function';
  // #endif
  return supported;
}

/** 只能由真实微信运行环境取得 code；H5 不提供模拟身份。 */
export function getWechatLoginCode() {
  if (!isWechatMiniProgram()) return Promise.reject(new Error('请在微信小程序中使用微信登录'));
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      timeout: 10000,
      success(result) {
        if (result.code) resolve(result.code);
        else reject(new Error('微信未返回登录凭证，请重试'));
      },
      fail(detail) {
        const error = new Error(`微信登录未完成，请检查微信网络后重试（${detail?.errMsg || '微信未返回登录凭证'}）`);
        error.code = detail?.errCode || detail?.code || 'WECHAT_LOGIN_FAILED';
        error.errMsg = detail?.errMsg || '';
        reject(error);
      }
    });
    // #endif
  });
}

/** 微信要求授权时保留平台 resolve，直到用户点击原生同意按钮。 */
export function installPrivacyListener(onRequired) {
  privacyCallback = onRequired;
  // #ifdef MP-WEIXIN
  if (isWechatMiniProgram() && typeof wx.onNeedPrivacyAuthorization === 'function' && !privacyListenerInstalled) {
    wx.onNeedPrivacyAuthorization(resolve => {
      pendingPrivacyResolve = resolve;
      privacyCallback?.();
    });
    privacyListenerInstalled = true;
  }
  // #endif
}

export function getPrivacyRequirement() {
  if (!isWechatMiniProgram()) return Promise.resolve({ needAuthorization: false });
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    if (typeof wx.getPrivacySetting !== 'function') {
      reject(new Error('微信基础库版本过低，请升级微信后重试'));
      return;
    }
    wx.getPrivacySetting({
      success: resolve,
      fail() { reject(new Error('无法读取微信隐私授权状态，请重试')); }
    });
    // #endif
  });
}

/** 此方法只在 agreePrivacyAuthorization 的真实事件回调中调用。 */
export function completePrivacyAuthorization(buttonId) {
  if (pendingPrivacyResolve) {
    pendingPrivacyResolve({ event: 'agree', buttonId });
    pendingPrivacyResolve = null;
  }
}

export function openPrivacyContract() {
  // #ifdef MP-WEIXIN
  if (isWechatMiniProgram() && typeof wx.openPrivacyContract === 'function') {
    wx.openPrivacyContract({ fail: () => uni.showToast({ title: '隐私保护指引暂时无法打开', icon: 'none' }) });
  }
  // #endif
}
