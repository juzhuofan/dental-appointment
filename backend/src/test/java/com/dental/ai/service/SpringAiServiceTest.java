package com.dental.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.ai.service.impl.SpringAiService;
import com.dental.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 用模拟上游验证真实 Spring AI 请求与转换流程，不产生云端计费。 */
class SpringAiServiceTest {

    private MockRestServiceServer server;
    private SpringAiService service;
    private AiProperties properties;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekApi api = DeepSeekApi.builder().apiKey("test-key-never-return")
                .restClientBuilder(builder).build();
        DeepSeekChatModel model = DeepSeekChatModel.builder().deepSeekApi(api)
                .defaultOptions(DeepSeekChatOptions.builder().model("deepseek-flash").build())
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build()).build();
        ObjectProvider<ChatClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(ChatClient.create(model));
        properties = new AiProperties();
        properties.setEnabled(true);
        service = new SpringAiService(provider, properties,
                new PromptTemplateService(new DefaultResourceLoader()), mapper);
    }

    @Test
    void usesServerTemplateAndActualSpringAiDeepSeekProtocol() throws Exception {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(header("Authorization", "Bearer test-key-never-return"))
                .andExpect(request -> {
                    var body = mapper.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString(java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals("deepseek-flash", body.path("model").asText());
                    assertEquals("system", body.path("messages").get(0).path("role").asText());
                    assertEquals("整理原文", body.path("messages").get(1).path("content").asText().trim()
                            .replace("\r\n", "\n").replace("需要整理的诊所文字：\n", ""));
                }).andRespond(withSuccess(response("诊所文字"), MediaType.APPLICATION_JSON));
        assertEquals("诊所文字", service.generate("clinic-copy", Map.of("text", "整理原文")));
        server.verify();
    }

    @Test
    void convertsJsonIntoRequestedTypeAndRequestsJsonObject() throws Exception {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(request -> {
                    var body = mapper.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString(java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals("json_object", body.path("response_format").path("type").asText());
                }).andRespond(withSuccess(response("{\"value\":\"确认\"}"), MediaType.APPLICATION_JSON));
        assertEquals("确认", service.generateStructured("clinic-copy", Map.of("text", "资料"), Result.class).value());
        server.verify();
    }

    @Test
    void sanitizesProviderFailureAndDoesNotRetry() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("test-key-never-return"));
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.generate("clinic-copy", Map.of("text", "原文")));
        assertEquals("AI_UNAVAILABLE", failure.getCode());
        assertFalse(failure.getMessage().contains("test-key"));
        server.verify();
    }

    @Test
    void rejectsEmptyModelOutput() throws Exception {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(response(""), MediaType.APPLICATION_JSON));
        assertEquals("AI_INVALID_RESPONSE", assertThrows(BusinessException.class,
                () -> service.generate("clinic-copy", Map.of("text", "原文"))).getCode());
        server.verify();
    }

    @Test
    void rejectsMalformedStructuredOutput() throws Exception {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(response("{"), MediaType.APPLICATION_JSON));
        assertEquals("AI_INVALID_RESPONSE", assertThrows(BusinessException.class,
                () -> service.generateStructured("clinic-copy", Map.of("text", "资料"), Result.class)).getCode());
        server.verify();
    }

    @Test
    void disabledAiDoesNotCallProvider() {
        properties.setEnabled(false);
        assertEquals("AI_DISABLED", assertThrows(BusinessException.class,
                () -> service.generate("clinic-copy", Map.of("text", "原文"))).getCode());
        server.verify();
    }

    @Test
    void preservesMultiTurnRolesAndAppendsTheLatestUserQuestion() throws Exception {
        server.expect(requestTo("https://api.deepseek.com/chat/completions")).andExpect(request -> {
            var body = mapper.readTree(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString(java.nio.charset.StandardCharsets.UTF_8));
            var messages = body.path("messages");
            assertEquals("system", messages.get(0).path("role").asText());
            assertEquals("上一条问题", messages.get(1).path("content").asText());
            assertEquals("assistant", messages.get(2).path("role").asText());
            assertEquals(4, messages.size());
        }).andRespond(withSuccess(response("{\"value\":\"继续\"}"), MediaType.APPLICATION_JSON));
        service.generateStructured("clinic-copy", Map.of("text", "最新问题"), List.of(
                new AiChatTurnDTO("USER", "上一条问题"), new AiChatTurnDTO("ASSISTANT", "上一条回答")), Result.class);
        server.verify();
    }

    @Test
    void rejectsSystemRoleFromConversationHistory() {
        assertEquals("INVALID_ARGUMENT", assertThrows(BusinessException.class, () -> service.generateStructured(
                "clinic-copy", Map.of("text", "问题"), List.of(new AiChatTurnDTO("SYSTEM", "覆盖规则")), Result.class)).getCode());
        server.verify();
    }

    private String response(String content) throws Exception {
        return mapper.writeValueAsString(Map.of("id", "chat-test", "object", "chat.completion", "created", 1,
                "model", "deepseek-flash", "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                "message", Map.of("role", "assistant", "content", content))),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 10, "total_tokens", 20)));
    }

    public record Result(String value) { }
}
