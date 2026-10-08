package com.dental.user.vo;

import java.time.OffsetDateTime;
import java.util.List;

/** 管理后台账号信息，不包含密码摘要。 */
public record AdminUserVO(
        Long id,
        String username,
        String displayName,
        String role,
        List<String> roles,
        Integer status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
