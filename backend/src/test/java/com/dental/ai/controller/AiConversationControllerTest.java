package com.dental.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dental.ai.service.AiConversationService;
import com.dental.ai.vo.AiConversationVO;
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

class AiConversationControllerTest {

    private final AiConversationService service = mock(AiConversationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiConversationController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void historyRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/ai/conversations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void validatesWrappedMessageAndRequestIdBeforePaidCall() throws Exception {
        authenticate();
        for (String body : List.of("{\"content\":\"你好\"}", "{\"data\":{\"requestId\":\"bad\",\"content\":\"你好\"}}",
                "{\"data\":{\"requestId\":\"chat-123456\",\"content\":\"" + "字".repeat(301) + "\"}}")) {
            mvc.perform(post("/api/v1/ai/conversations/8/messages").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void createUsesAuthenticatedUserInsteadOfSubmittedUserId() throws Exception {
        authenticate();
        when(service.create(18)).thenReturn(new AiConversationVO(8L, "新会话", null, null));
        mvc.perform(post("/api/v1/ai/conversations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"userId\":999}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(8));
    }

    @Test
    void propagatesNotFoundWithoutExposingOtherAccountDetails() throws Exception {
        authenticate();
        when(service.send(eq(18L), eq(99L), any())).thenThrow(com.dental.common.BusinessException.missing("会话不存在或已删除"));
        mvc.perform(post("/api/v1/ai/conversations/99/messages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":{\"requestId\":\"chat-123456\",\"content\":\"你好\"}}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void authenticate() {
        CurrentUser user = new CurrentUser(18L, "test", "测试", List.of("PATIENT"), null, "test-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
