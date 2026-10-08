package com.dental.security;

import com.dental.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** 从 Spring Security 上下文取得已验证的当前用户。 */
public record CurrentUser(
        Long id,
        String username,
        String displayName,
        List<String> roles,
        Long doctorId,
        String tokenId) {

    public CurrentUser {
        roles = List.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public static CurrentUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser current)) {
            throw BusinessException.unauthorized("请先登录");
        }
        return current;
    }

    public static CurrentUser requireRole(String role) {
        CurrentUser current = require();
        if (!current.hasRole(role)) {
            throw BusinessException.forbidden("当前账号没有操作权限");
        }
        return current;
    }
}
