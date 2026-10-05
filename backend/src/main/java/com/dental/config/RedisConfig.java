package com.dental.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.protocol.ProtocolVersion;

import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Redis 3.2.100 不支持 HELLO，显式使用 RESP2。 */
@Configuration
public class RedisConfig {
    @Bean
    public LettuceClientConfigurationBuilderCustomizer redisProtocol() {
        return builder ->
                builder.clientOptions(
                        ClientOptions.builder().protocolVersion(ProtocolVersion.RESP2).build());
    }
}
