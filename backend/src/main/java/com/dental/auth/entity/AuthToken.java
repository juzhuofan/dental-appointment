package com.dental.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

import java.time.LocalDateTime;

/** 已签发的访问令牌及其撤销状态。 */
@TableName("auth_token")
public class AuthToken extends BaseEntity {
    private String tokenId;
    private Long userId;
    private LocalDateTime expiresAt;
    private Integer revoked;

    public String getTokenId() {
        return tokenId;
    }
    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    public Integer getRevoked() {
        return revoked;
    }
    public void setRevoked(Integer revoked) {
        this.revoked = revoked;
    }
}
