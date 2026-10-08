package com.dental.audit.vo;

import java.time.OffsetDateTime;

/** 审计日志响应，不暴露 IP 地址。 */
public record OperationLogVO(Long id, Long operatorUserId, String operatorName, String operatorRole,
                             String action, String targetType, String targetId, String summary,
                             OffsetDateTime createdAt) {
}
