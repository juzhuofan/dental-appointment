package com.dental.security;

import com.dental.common.BusinessException;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

public record CurrentUser(
        long id,
        String username,
        String displayName,
        List<String> roles,
        Long doctorId,
        String tokenId) {
    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }

    public boolean isDoctor() {
        return roles.contains("DOCTOR");
    }

    public boolean isPatient() {
        return roles.contains("PATIENT");
    }

    public static CurrentUser get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CurrentUser user)) {
            throw new BusinessException("UNAUTHENTICATED", "请先登录", 401);
        }
        return user;
    }
}
