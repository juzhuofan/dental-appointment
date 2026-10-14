package com.dental.ai.config;

import com.dental.common.TimeUtil;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

/** 独立 Spring AI 客户端，不改变微信、OSS 或数据库客户端。 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean("aiClock")
    public Clock aiClock() {
        return Clock.system(TimeUtil.CLINIC_ZONE);
    }

    @Bean("dentalAiChatClient")
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    public ChatClient dentalAiChatClient(AiProperties properties) {
        properties.validateConfiguration();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        DeepSeekApi api = DeepSeekApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        DeepSeekChatModel model = DeepSeekChatModel.builder()
                .deepSeekApi(api)
                .defaultOptions(DeepSeekChatOptions.builder()
                        .model(properties.getModel())
                        .temperature(properties.getTemperature())
                        .maxTokens(properties.getMaxTokens())
                        .internalToolExecutionEnabled(false)
                        .build())
                // 单次请求，避免上游故障引发长时间重试或重复计费。
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
        return ChatClient.create(model);
    }
}
