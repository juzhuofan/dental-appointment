package com.dental.file.controller;

import com.dental.common.R;
import com.dental.file.service.FileUploadService;
import com.dental.file.vo.FileUploadVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 登录用户通用文件上传入口；认证沿用 Spring Security 的 Bearer token。 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    public FileUploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Operation(summary = "上传文件到阿里云 OSS", description = "multipart 字段为 file，返回无签名文件链接")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<FileUploadVO> upload(@RequestPart("file") MultipartFile file) {
        return R.ok(fileUploadService.upload(file));
    }
}
