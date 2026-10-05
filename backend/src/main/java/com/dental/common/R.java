package com.dental.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** 所有 JSON 请求和响应的统一包装。客户端的 code/traceId 不参与业务判定。 */
public record R<T>(
        String code,
        String message,
        @Valid @NotNull(message = "data 不能为空") T data,
        String traceId) {
    public static <T> R<T> ok(T data) {
        return new R<>("OK", "success", data, UUID.randomUUID().toString());
    }

    public static R<Void> error(String code, String message) {
        return new R<>(code, message, null, UUID.randomUUID().toString());
    }
}
