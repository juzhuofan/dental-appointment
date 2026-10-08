package com.dental.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OSS SDK V1 单例客户端，使用 V4 签名并在 Spring 容器关闭时释放连接池。 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    private static final int SOCKET_TIMEOUT_MILLIS = 15000;
    private static final int MAX_ERROR_RETRY = 2;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.oss", name = "enabled", havingValue = "true")
    public OSS ossClient(OssProperties properties) {
        properties.validateConfiguration();
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        configuration.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        configuration.setSocketTimeout(SOCKET_TIMEOUT_MILLIS);
        configuration.setMaxErrorRetry(MAX_ERROR_RETRY);
        return OSSClientBuilder.create()
                .endpoint(properties.getEndpoint())
                .credentialsProvider(CredentialsProviderFactory.newDefaultCredentialProvider(
                        properties.getAccessKeyId(), properties.getAccessKeySecret()))
                .clientConfiguration(configuration)
                .region(properties.getRegion())
                .build();
    }
}
