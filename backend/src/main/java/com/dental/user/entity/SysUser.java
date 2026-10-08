package com.dental.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

import java.time.LocalDateTime;

/** 登录账号。 */
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    private String passwordHash;
    private String phone;
    private String wechatOpenid;
    private String demoDeviceHash;
    private String displayName;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginAt;

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPasswordHash() {
        return passwordHash;
    }
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public String getWechatOpenid() {
        return wechatOpenid;
    }
    public void setWechatOpenid(String wechatOpenid) {
        this.wechatOpenid = wechatOpenid;
    }
    public String getDemoDeviceHash() {
        return demoDeviceHash;
    }
    public void setDemoDeviceHash(String demoDeviceHash) {
        this.demoDeviceHash = demoDeviceHash;
    }
    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
    }
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
