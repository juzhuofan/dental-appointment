package com.dental.ai.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI 客户端配置。凭证不参与 toString 或接口响应。 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    private boolean enabled;
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-flash";
    private double temperature = 0.1;
    private int maxTokens = 1024;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(25);
    private int maxConcurrentCalls = 3;
    private int requestsPerMinute = 10;

    public void validateConfiguration() {
        if (!"https://api.deepseek.com".equals(baseUrl)) {
            throw new IllegalArgumentException("app.ai.base-url 必须使用 DeepSeek 官方 HTTPS 地址");
        }
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new IllegalArgumentException("启用 AI 时必须填写 API Key 和模型名称");
        }
        if (temperature < 0 || temperature > 2 || maxTokens < 128 || maxTokens > 4096
                || maxConcurrentCalls < 1 || maxConcurrentCalls > 10
                || requestsPerMinute < 1 || requestsPerMinute > 60) {
            throw new IllegalArgumentException("AI 配置的生成参数或并发上限不正确");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || connectTimeout.compareTo(Duration.ofSeconds(10)) > 0
                || readTimeout == null || readTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || readTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("AI 连接超时应为 1 至 10 秒，读取超时应为 1 至 30 秒");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxConcurrentCalls() { return maxConcurrentCalls; }
    public void setMaxConcurrentCalls(int maxConcurrentCalls) { this.maxConcurrentCalls = maxConcurrentCalls; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
}
