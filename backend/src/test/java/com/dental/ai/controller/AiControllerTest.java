package com.dental.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dental.ai.config.AiProperties;
import com.dental.ai.service.AiRateLimitService;
import com.dental.ai.service.AiService;
import com.dental.ai.service.AppointmentGuideService;
import com.dental.ai.vo.AppointmentGuideVO;
import com.dental.common.GlobalExceptionHandler;
import com.dental.security.CurrentUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiControllerTest {

    private final AiService ai = mock(AiService.class);
    private final AppointmentGuideService guide = mock(AppointmentGuideService.class);
    private final AiRateLimitService limiter = mock(AiRateLimitService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey("must-not-return");
        mvc = MockMvcBuilders.standaloneSetup(new AiController(ai, guide, limiter, properties))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void exposesOnlyCapabilityFlags() throws Exception {
        mvc.perform(get("/api/v1/ai/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    void requiresLoginAndAdminForGenericGeneration() throws Exception {
        mvc.perform(post("/api/v1/ai/guide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"message\":\"周末洗牙\"}}"))
                .andExpect(status().isUnauthorized());
        authenticate("PATIENT");
        mvc.perform(post("/api/v1/ai/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"templateId\":\"clinic-copy\",\"variables\":{\"text\":\"诊所\"}}}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ai, guide, limiter);
    }

    @Test
    void validatesWrappedBodyAndMessageLengthBeforePaidCall() throws Exception {
        authenticate("PATIENT");
        for (String body : List.of("{\"message\":\"检查\"}", "{\"data\":{\"message\":\"\"}}",
                "{\"data\":{\"message\":\"" + "字".repeat(301) + "\"}}")) {
            mvc.perform(post("/api/v1/ai/guide").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(guide, limiter);
    }

    @Test
    void returnsCandidatesInRAndChecksQuota() throws Exception {
        authenticate("PATIENT");
        when(guide.guide(any())).thenReturn(new AppointmentGuideVO("暂无号源", false, "检查", null, null,
                "ANY", List.of(), List.of(), false));
        mvc.perform(post("/api/v1/ai/guide").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"message\":\"检查\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.candidates").isArray());
        verify(limiter).check(18L);
    }

    private void authenticate(String role) {
        CurrentUser user = new CurrentUser(18L, "test", "测试账号", List.of(role), null, "test-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
