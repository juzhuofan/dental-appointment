package com.dental.file.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dental.common.BusinessException;
import com.dental.common.GlobalExceptionHandler;
import com.dental.config.SecurityConfig;
import com.dental.file.service.FileUploadService;
import com.dental.file.vo.FileUploadVO;
import com.dental.security.TokenFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

/** 使用真实 SecurityConfig 和统一异常处理验证上传 HTTP 契约，SDK 与认证存储不联网。 */
@WebMvcTest(controllers = FileUploadController.class,
        properties = {"app.cors-origins=http://localhost:5173", "app.oss.enabled=false"},
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class FileUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private FileUploadService fileUploadService;
    @MockitoBean
    private TokenFilter tokenFilter;

    @BeforeEach
    void passThroughTokenFilter() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(tokenFilter).doFilter(any(), any(), any());
    }

    @Test
    void anonymousUploadReturnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload").file(image()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").isString());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void authenticatedUploadReturnsUrlAndMetadataInsideR() throws Exception {
        when(fileUploadService.upload(any(MultipartFile.class))).thenReturn(new FileUploadVO(
                "https://files.example.test/uploads/photo.png", "uploads/photo.png", "photo.png", 2L,
                "image/png"));

        mockMvc.perform(multipart("/api/v1/files/upload").file(image())
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.url").value("https://files.example.test/uploads/photo.png"))
                .andExpect(jsonPath("$.data.objectKey").value("uploads/photo.png"))
                .andExpect(jsonPath("$.data.originalFilename").value("photo.png"))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void missingFilePartReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(multipart("/api/v1/files/upload").with(user("patient").roles("PATIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.traceId").isString());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void jsonUploadReturnsUnsupportedMediaTypeEnvelopeWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/v1/files/upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.traceId").isString());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void ossFailureReturnsBadGatewayEnvelope() throws Exception {
        when(fileUploadService.upload(any(MultipartFile.class))).thenThrow(new BusinessException(
                "FILE_UPLOAD_FAILED", "文件上传失败，请稍后重试", HttpStatus.BAD_GATEWAY));

        mockMvc.perform(multipart("/api/v1/files/upload").file(image())
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FILE_UPLOAD_FAILED"))
                .andExpect(jsonPath("$.message").value("文件上传失败，请稍后重试"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test
    void multipartSizeExceptionUsesSamePayloadTooLargeEnvelope() throws Exception {
        when(fileUploadService.upload(any(MultipartFile.class))).thenThrow(
                new MaxUploadSizeExceededException(10 * 1024 * 1024L));

        mockMvc.perform(multipart("/api/v1/files/upload").file(image())
                        .with(user("patient").roles("PATIENT")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2});
    }
}
