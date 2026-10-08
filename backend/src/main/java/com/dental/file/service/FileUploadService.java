package com.dental.file.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.dental.common.BusinessException;
import com.dental.config.OssProperties;
import com.dental.file.vo.FileUploadVO;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 通用 OSS 文件上传服务。业务模块注入本服务，调用 upload(file).url() 保存链接。 */
@Service
public class FileUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUploadService.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DIRECTORY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int READ_URL_VALID_MINUTES = 15;

    private final OssProperties properties;
    private final ObjectProvider<OSS> clientProvider;

    public FileUploadService(OssProperties properties, ObjectProvider<OSS> clientProvider) {
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    /**
     * 校验文件后流式上传，返回稳定链接。服务不写数据库、不改变 Bucket 或 Object ACL。
     *
     * @param file 调用方提供的文件
     * @return 可直接存入数据库的链接与文件信息
     */
    public FileUploadVO upload(MultipartFile file) {
        String originalFilename = validateFile(file);
        OSS client = clientProvider.getIfAvailable();
        if (!properties.isEnabled() || client == null) {
            throw new BusinessException("FILE_STORAGE_NOT_CONFIGURED", "OSS 文件上传尚未配置或启用",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        String extension = StringUtils.getFilenameExtension(originalFilename).toLowerCase(Locale.ROOT);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectKey = properties.getObjectPrefix() + "/"
                + LocalDate.now(CLINIC_ZONE).format(DIRECTORY_DATE) + "/" + filename;
        String contentType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM).toString();
        String url = properties.resolvePublicBaseUrl() + "/" + objectKey;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(contentType);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            metadata.setContentDisposition("attachment; filename=\"" + filename + "\"");
        }
        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(properties.getBucketName(), objectKey, inputStream);
            request.setMetadata(metadata);
            request.addHeader("x-oss-forbid-overwrite", "true");
            client.putObject(request);
            return new FileUploadVO(url, objectKey, originalFilename, file.getSize(), contentType);
        } catch (OSSException exception) {
            LOGGER.warn("OSS upload rejected: code={}, requestId={}",
                    exception.getErrorCode(), exception.getRequestId());
            throw uploadFailed();
        } catch (ClientException | IOException exception) {
            LOGGER.warn("OSS upload failed: exceptionType={}", exception.getClass().getSimpleName());
            throw uploadFailed();
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.bad("上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSize().toBytes()) {
            throw new BusinessException("FILE_TOO_LARGE", "上传文件超过允许的大小",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw BusinessException.bad("上传文件名不能为空");
        }
        filename = StringUtils.getFilename(filename.replace('\\', '/'));
        if (!StringUtils.hasText(filename) || filename.length() > MAX_FILENAME_LENGTH) {
            throw BusinessException.bad("上传文件名不正确或过长");
        }
        String extension = StringUtils.getFilenameExtension(filename);
        if (!StringUtils.hasText(extension)
                || !properties.getAllowedExtensions().contains(extension.toLowerCase(Locale.ROOT))) {
            throw BusinessException.bad("不支持此文件类型");
        }
        return filename;
    }

    /** 私有头像短期展示地址；数据库仍保存原始无签名链接，仅签署当前 Bucket 下的对象。 */
    public String signedReadUrl(String storedUrl) {
        if (!properties.isEnabled() || !StringUtils.hasText(storedUrl)) {
            return null;
        }
        String prefix = properties.resolvePublicBaseUrl() + "/" + properties.getObjectPrefix() + "/";
        if (!storedUrl.startsWith(prefix)) {
            return null;
        }
        String objectKey = storedUrl.substring(properties.resolvePublicBaseUrl().length() + 1);
        if (objectKey.contains("..") || objectKey.contains("?") || objectKey.contains("#")) {
            return null;
        }
        OSS client = clientProvider.getIfAvailable();
        if (client == null) {
            return null;
        }
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(properties.getBucketName(), objectKey);
        request.setExpiration(Date.from(Instant.now().plus(READ_URL_VALID_MINUTES, ChronoUnit.MINUTES)));
        return client.generatePresignedUrl(request).toExternalForm();
    }

    private static BusinessException uploadFailed() {
        return new BusinessException("FILE_UPLOAD_FAILED", "文件上传失败，请稍后重试",
                HttpStatus.BAD_GATEWAY);
    }
}
