package com.dental.audit.dto;

/** 操作日志查询条件。 */
public record OperationLogQueryDTO(String action, String keyword, int page, int size) {
}
