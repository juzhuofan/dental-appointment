package com.dental.common;

public class BusinessException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static BusinessException bad(String message) {
        return new BusinessException("INVALID_ARGUMENT", message, 400);
    }

    public static BusinessException forbidden() {
        return new BusinessException("FORBIDDEN", "没有权限执行此操作", 403);
    }

    public static BusinessException missing() {
        return new BusinessException("NOT_FOUND", "记录不存在或已删除", 404);
    }

    public static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, 409);
    }
}
