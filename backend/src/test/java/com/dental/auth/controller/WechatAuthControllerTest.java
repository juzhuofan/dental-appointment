package com.dental.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.service.WechatAuthService;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.common.GlobalExceptionHandler;
import com.dental.config.WechatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WechatAuthControllerTest {

    private WechatAuthService authService;
    private MockMvc mvc;
    private WechatProperties properties;

    @BeforeEach
    void setUp() {
        authService = mock(WechatAuthService.class);
        properties = new WechatProperties();
        properties.setEnabled(true);
        properties.setAppId("wx0123456789abcdef");
        properties.setAppSecret("must-not-return");
        mvc = MockMvcBuilders.standaloneSetup(new WechatAuthController(authService, properties))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void publishesOnlyCapabilityFlagsInUnifiedResponse() throws Exception {
        mvc.perform(get("/api/v1/auth/wechat-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.phoneNumberEnabled").value(false))
                .andExpect(jsonPath("$.data.appSecret").doesNotExist())
                .andExpect(jsonPath("$.data.appId").doesNotExist());
    }

    @Test
    void rejectsUnwrappedAndBlankLoginCodeBeforeAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/wechat-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginCode\":\"code\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/wechat-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"loginCode\":\"\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        verifyNoInteractions(authService);
    }

    @Test
    void returnsBusinessSessionWithoutWechatCredentials() throws Exception {
        when(authService.login(any(WechatLoginDTO.class))).thenReturn(new LoginVO("jwt-session", null));
        mvc.perform(post("/api/v1/auth/wechat-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"loginCode\":\"code\",\"register\":true}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.token").value("jwt-session"))
                .andExpect(jsonPath("$.data.session_key").doesNotExist())
                .andExpect(jsonPath("$.data.openid").doesNotExist());
    }

    @Test
    void forwardsRegistrationRequiredToLoginDialogAsConflict() throws Exception {
        when(authService.login(any())).thenThrow(BusinessException.conflict(
                "WECHAT_REGISTRATION_REQUIRED", "请确认微信登录后创建账户"));
        mvc.perform(post("/api/v1/auth/wechat-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"loginCode\":\"code\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WECHAT_REGISTRATION_REQUIRED"));
    }
}
