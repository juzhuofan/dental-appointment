package com.dental.ai.service;

import com.dental.common.BusinessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/** 仅渲染服务器随工程发布的模板，禁止客户端指定文件路径或系统提示词。 */
@Service
public class PromptTemplateService {

    private static final int MAX_VARIABLES = 16;
    private static final int MAX_VARIABLE_LENGTH = 24000;
    private final ResourceLoader resourceLoader;
    private final Map<String, Templates> cache = new ConcurrentHashMap<>();

    public PromptTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RenderedPrompt render(String templateId, Map<String, Object> variables) {
        if (templateId == null || !templateId.matches("[a-z][a-z0-9-]{0,63}")) {
            throw BusinessException.bad("提示模板名称不正确");
        }
        if (variables == null || variables.size() > MAX_VARIABLES
                || variables.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getValue().toString().length() > MAX_VARIABLE_LENGTH)) {
            throw BusinessException.bad("提示模板参数缺失或超过长度限制");
        }
        Templates templates = cache.computeIfAbsent(templateId, this::load);
        try {
            // 每次创建独立模板，避免并发请求共享可变渲染状态。
            return new RenderedPrompt(new PromptTemplate(templates.system()).render(variables),
                    new PromptTemplate(templates.user()).render(variables));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw BusinessException.bad("提示模板参数不完整，请核对模板变量");
        }
    }

    private Templates load(String templateId) {
        Resource system = resourceLoader.getResource("classpath:prompts/ai/" + templateId + "/system.st");
        Resource user = resourceLoader.getResource("classpath:prompts/ai/" + templateId + "/user.st");
        if (!system.exists() || !user.exists()) {
            throw BusinessException.missing("提示模板不存在");
        }
        try {
            // Windows 默认字符集可能是 GBK，提示文件始终以 UTF-8 读取。
            return new Templates(system.getContentAsString(StandardCharsets.UTF_8),
                    user.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("提示模板读取失败");
        }
    }

    private record Templates(String system, String user) { }

    public record RenderedPrompt(String system, String user) { }
}
