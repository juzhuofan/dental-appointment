package com.dental.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<R<Void>> business(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(R.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> invalidBody(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().isEmpty()
                ? "请求参数不正确"
                : exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> fileTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(R.error("FILE_TOO_LARGE", "上传文件超过允许的大小"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<R<Void>> missingFile(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", "请选择上传文件，表单字段名为 file"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<R<Void>> invalidMultipart(MultipartException exception) {
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", "文件上传表单格式不正确"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<R<Void>> unsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(415).body(R.error("UNSUPPORTED_MEDIA_TYPE", "请求 Content-Type 不受支持"));
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<R<Void>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(R.error("INVALID_ARGUMENT", "请求参数或格式不正确"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> denied(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(R.error("FORBIDDEN", "没有权限执行此操作"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> conflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(R.error("DATA_CONFLICT", "数据重复或关联关系冲突"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<R<Void>> notFound(NoResourceFoundException exception) {
        return ResponseEntity.status(404).body(R.error("NOT_FOUND", "接口不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> unexpected(Exception exception) {
        LOGGER.error("Unhandled request error", exception);
        return ResponseEntity.internalServerError().body(R.error("INTERNAL_ERROR", "服务暂时不可用，请稍后重试"));
    }
}
