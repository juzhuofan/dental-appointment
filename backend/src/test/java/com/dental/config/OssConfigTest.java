package com.dental.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aliyun.oss.ClientConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSClientBuilder.OSSClientBuilderImpl;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

/** 验证配置绑定、条件创建及客户端销毁；不会请求真实 OSS Bucket。 */
class OssConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OssConfig.class);

    @Test
    void unconfiguredStorageDoesNotCreateOssClientAndPreservesDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(OssProperties.class).doesNotHaveBean(OSS.class);
            OssProperties properties = context.getBean(OssProperties.class);
            assertFalse(properties.isEnabled());
            assertEquals(DataSize.ofMegabytes(10), properties.getMaxFileSize());
            assertThat(properties.getAllowedExtensions()).contains("jpg", "png", "pdf", "docx", "zip");
        });
    }

    @Test
    void enablingIncompleteConfigurationFailsBeforeBuildingClient() {
        try (MockedStatic<OSSClientBuilder> factory = mockStatic(OSSClientBuilder.class)) {
            contextRunner.withPropertyValues("app.oss.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                assertThat(context.getStartupFailure()).hasStackTraceContaining("app.oss.bucket-name");
            });
            factory.verifyNoInteractions();
        }
    }

    @Test
    void enabledStorageCreatesSingletonV4ClientAndShutsItDownWithContext() {
        OSS client = mock(OSS.class);
        OSSClientBuilderImpl builder = mock(OSSClientBuilderImpl.class, RETURNS_SELF);
        when(builder.build()).thenReturn(client);
        try (MockedStatic<OSSClientBuilder> factory = mockStatic(OSSClientBuilder.class)) {
            factory.when(OSSClientBuilder::create).thenReturn(builder);
            contextRunner.withPropertyValues(
                    "app.oss.enabled=true",
                    "app.oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com",
                    "app.oss.region=cn-hangzhou",
                    "app.oss.bucket-name=dental-upload-test",
                    "app.oss.access-key-id=test-key",
                    "app.oss.access-key-secret=test-secret",
                    "app.oss.max-file-size=2MB").run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(OSS.class);
                        assertThat(context.getBean(OSS.class)).isSameAs(client);
                        assertEquals(DataSize.ofMegabytes(2), context.getBean(OssProperties.class).getMaxFileSize());
                        verifyNoInteractions(client);
                    });
        }
        ArgumentCaptor<ClientConfiguration> configuration = ArgumentCaptor.forClass(ClientConfiguration.class);
        verify(builder).clientConfiguration(configuration.capture());
        assertEquals(SignVersion.V4, configuration.getValue().getSignatureVersion());
        assertThat(configuration.getValue().getConnectionTimeout()).isGreaterThan(0);
        assertThat(configuration.getValue().getSocketTimeout()).isGreaterThan(0);
        verify(builder).endpoint("https://oss-cn-hangzhou.aliyuncs.com");
        verify(builder).region("cn-hangzhou");
        ArgumentCaptor<CredentialsProvider> credentials = ArgumentCaptor.forClass(CredentialsProvider.class);
        verify(builder).credentialsProvider(credentials.capture());
        assertEquals("test-key", credentials.getValue().getCredentials().getAccessKeyId());
        verify(client).shutdown();
    }

    @Test
    void stableUrlUsesExternalEndpointOrConfiguredDomainWithoutSignature() {
        OssProperties properties = validProperties();
        properties.setEndpoint("https://oss-cn-hangzhou-internal.aliyuncs.com");
        assertEquals("https://dental-upload-test.oss-cn-hangzhou.aliyuncs.com", properties.resolvePublicBaseUrl());

        properties.setPublicBaseUrl("https://files.example.test/uploads///");
        assertEquals("https://files.example.test/uploads", properties.resolvePublicBaseUrl());

        properties.setPublicBaseUrl("https://files.example.test?Signature=temporary");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                properties::validateConfiguration);
        assertThat(exception.getMessage()).contains("app.oss.public-base-url").doesNotContain("test-secret");
    }

    @Test
    void rejectsUnsafePrefixAndZeroFileLimit() {
        OssProperties properties = validProperties();
        properties.setObjectPrefix("dental/../uploads");
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);

        properties.setObjectPrefix("dental/uploads");
        properties.setMaxFileSize(DataSize.ofBytes(0));
        assertThrows(IllegalArgumentException.class, properties::validateConfiguration);
    }

    private static OssProperties validProperties() {
        OssProperties properties = new OssProperties();
        properties.setBucketName("dental-upload-test");
        properties.setAccessKeyId("test-key");
        properties.setAccessKeySecret("test-secret");
        return properties;
    }
}
