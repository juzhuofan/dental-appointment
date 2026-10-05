package com.dental.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> business(BusinessException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(R.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<R<Void>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", "请求格式或参数不正确"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> forbidden() {
        return ResponseEntity.status(403).body(R.error("FORBIDDEN", "没有权限执行此操作"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> conflict() {
        return ResponseEntity.status(409).body(R.error("DATA_CONFLICT", "数据重复或关联约束冲突"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> notFound() {
        return ResponseEntity.status(404).body(R.error("NOT_FOUND", "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> unexpected(Exception exception) {
        // 不记录异常正文：数据库异常正文可能包含 SQL 或患者数据。
        var types = new java.util.StringJoiner(" -> ");
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 8; depth++) {
            types.add(current.getClass().getSimpleName());
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        LOGGER.error("Request failed with exception types {}", types);
        return ResponseEntity.internalServerError()
                .body(R.error("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
