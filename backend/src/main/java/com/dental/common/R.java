package com.dental.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** JSON 请求和响应使用的统一包装。请求只信任 data 字段。 */
public class R<T> {

    private String code;
    private String message;
    @Valid
    @NotNull(message = "data 不能为空")
    private T data;
    private String traceId;

    public R() {
    }

    public R(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> R<T> ok(T data) {
        return new R<>("OK", "success", data, UUID.randomUUID().toString());
    }

    public static <T> R<T> error(String code, String message) {
        return new R<>(code, message, null, UUID.randomUUID().toString());
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public T data() { return data; }
}
