package com.dental.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 微信固定接口地址与请求超时，禁止跟随重定向传递服务端凭证。 */
@Configuration
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfig {

    @Bean("wechatRestClient")
    public RestClient wechatRestClient(RestClient.Builder builder, WechatProperties properties) {
        if (properties.isEnabled()) {
            properties.validateConfiguration();
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return builder.clone().baseUrl("https://api.weixin.qq.com")
                .requestFactory(requestFactory).build();
    }
}
