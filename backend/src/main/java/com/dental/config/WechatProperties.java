package com.dental.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 微信小程序服务端配置；凭证只在服务端使用，不参与日志输出。 */
@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {

    private boolean enabled;
    private boolean phoneNumberEnabled;
    private String appId = "";
    private String appSecret = "";

    public void validateConfiguration() {
        if (!StringUtils.hasText(appId) || !appId.matches("wx[a-zA-Z0-9]{16}")) {
            throw new IllegalArgumentException("app.wechat.app-id 必须是有效的小程序 AppID");
        }
        if (!StringUtils.hasText(appSecret)) {
            throw new IllegalArgumentException("app.wechat.app-secret 不能为空");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPhoneNumberEnabled() {
        return phoneNumberEnabled;
    }

    public void setPhoneNumberEnabled(boolean phoneNumberEnabled) {
        this.phoneNumberEnabled = phoneNumberEnabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }
}
