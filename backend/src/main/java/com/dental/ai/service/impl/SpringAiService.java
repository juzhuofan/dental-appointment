package com.dental.ai.service.impl;

import com.dental.ai.config.AiProperties;
import com.dental.ai.dto.AiChatTurnDTO;
import com.dental.ai.service.AiService;
import com.dental.ai.service.PromptTemplateService;
import com.dental.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.ResponseFormat;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 模型调用、结构化转换与故障处理统一封装。业务模块无需了解 DeepSeek SDK。 */
@Service
public class SpringAiService implements AiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiService.class);
    private final ObjectProvider<ChatClient> clientProvider;
    private final AiProperties properties;
    private final PromptTemplateService templates;
    private final ObjectMapper objectMapper;
    private final Semaphore concurrentCalls;

    public SpringAiService(@Qualifier("dentalAiChatClient") ObjectProvider<ChatClient> clientProvider,
            AiProperties properties, PromptTemplateService templates, ObjectMapper objectMapper) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.templates = templates;
        this.objectMapper = objectMapper;
        this.concurrentCalls = new Semaphore(Math.max(1, properties.getMaxConcurrentCalls()));
    }

    @Override
    public String generate(String templateId, Map<String, Object> variables) {
        return call(templateId, variables, List.of(), "", false);
    }

    @Override
    public <T> T generateStructured(String templateId, Map<String, Object> variables, Class<T> resultType) {
        return generateStructured(templateId, variables, List.of(), resultType);
    }

    @Override
    public <T> T generateStructured(String templateId, Map<String, Object> variables,
            List<AiChatTurnDTO> history, Class<T> resultType) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(resultType, objectMapper.copy());
        String content = call(templateId, variables, history, converter.getFormat(), true);
        try {
            // 使用同一 JSON Schema 指导输出；自行解析，避免转换器把失败的原文写入日志。
            T result = objectMapper.readValue(content, resultType);
            if (result == null) {
                throw invalidResponse();
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (JsonProcessingException | RuntimeException exception) {
            throw invalidResponse();
        }
    }

    private String call(String templateId, Map<String, Object> variables,
            List<AiChatTurnDTO> history, String format, boolean json) {
        ChatClient client = clientProvider.getIfAvailable();
        if (!properties.isEnabled() || client == null) {
            throw new BusinessException("AI_DISABLED", "智能预约暂未开放，请使用科室和医生列表预约",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        PromptTemplateService.RenderedPrompt prompt = templates.render(templateId, variables);
        List<Message> messages = historyMessages(history);
        if (!concurrentCalls.tryAcquire()) {
            throw new BusinessException("AI_BUSY", "智能助手正在处理其他请求，请稍后重试",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        try {
            ChatClient.ChatClientRequestSpec request = client.prompt()
                    .system(prompt.system() + (format.isBlank() ? "" : "\n" + format))
                    .messages(messages)
                    .user(prompt.user());
            if (json) {
                request.options(DeepSeekChatOptions.builder().responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT).build()).build());
            }
            String content = request.call().content();
            if (content == null || content.isBlank()) {
                throw invalidResponse();
            }
            return content;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 不记录上游响应、提示内容、请求头或异常堆栈，避免凭证和用户信息泄露。
            LOGGER.warn("AI request failed: type={}", exception.getClass().getSimpleName());
            throw new BusinessException("AI_UNAVAILABLE", "智能助手暂时无法连接，请稍后重试或手动预约",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            concurrentCalls.release();
        }
    }

    private BusinessException invalidResponse() {
        return new BusinessException("AI_INVALID_RESPONSE", "暂未能准确理解需求，请换一种说法或手动预约",
                HttpStatus.BAD_GATEWAY);
    }

    private List<Message> historyMessages(List<AiChatTurnDTO> history) {
        if (history == null || history.size() > 20 || history.stream().anyMatch(turn -> turn == null
                || turn.content() == null || turn.content().length() > 2500
                || (!"USER".equals(turn.role()) && !"ASSISTANT".equals(turn.role())))) {
            throw BusinessException.bad("聊天上下文格式不正确");
        }
        return history.stream().<Message>map(turn -> "USER".equals(turn.role())
                ? new UserMessage(turn.content()) : new AssistantMessage(turn.content())).toList();
    }
}
