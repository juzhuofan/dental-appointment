package com.dental.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.dental.config.OssProperties;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/** 私有 Bucket 展示链接只签署配置目录中的已有对象，不签任意外部地址。 */
@ExtendWith(MockitoExtension.class)
class PrivateReadUrlTest {

    private static final String BASE_URL = "https://dental-test.oss-cn-beijing.aliyuncs.com";
    private static final String OBJECT_KEY = "dental/uploads/2026/10/07/avatar.png";

    @Mock
    private OSS client;
    @Mock
    private ObjectProvider<OSS> clientProvider;

    private OssProperties properties;
    private FileUploadService service;

    @BeforeEach
    void setUp() {
        properties = new OssProperties();
        properties.setEnabled(true);
        properties.setEndpoint("https://oss-cn-beijing.aliyuncs.com");
        properties.setRegion("cn-beijing");
        properties.setBucketName("dental-test");
        service = new FileUploadService(properties, clientProvider);
    }

    @Test
    void signsOwnObjectWithFifteenMinuteExpiration() throws Exception {
        String signed = BASE_URL + "/" + OBJECT_KEY + "?Expires=123&Signature=test";
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(URI.create(signed).toURL());
        Instant before = Instant.now();

        String result = service.signedReadUrl(BASE_URL + "/" + OBJECT_KEY);

        Instant after = Instant.now();
        assertEquals(signed, result);
        ArgumentCaptor<GeneratePresignedUrlRequest> request =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(request.capture());
        assertEquals("dental-test", request.getValue().getBucketName());
        assertEquals(OBJECT_KEY, request.getValue().getKey());
        Instant expires = request.getValue().getExpiration().toInstant();
        assertTrue(expires.isAfter(before.plus(14, ChronoUnit.MINUTES)));
        assertTrue(expires.isBefore(after.plus(16, ChronoUnit.MINUTES)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "https://external.example.test/dental/uploads/avatar.png",
            "https://other-bucket.oss-cn-beijing.aliyuncs.com/dental/uploads/avatar.png",
            "https://dental-test.oss-cn-beijing.aliyuncs.com.evil.test/dental/uploads/avatar.png",
            "http://dental-test.oss-cn-beijing.aliyuncs.com/dental/uploads/avatar.png",
            "https://dental-test.oss-cn-beijing.aliyuncs.com/other/avatar.png",
            "https://dental-test.oss-cn-beijing.aliyuncs.com/dental/uploads-copy/avatar.png",
            "https://dental-test.oss-cn-beijing.aliyuncs.com/dental/uploads/../secret.png",
            "https://dental-test.oss-cn-beijing.aliyuncs.com/dental/uploads/avatar.png?Signature=existing",
            "https://dental-test.oss-cn-beijing.aliyuncs.com/dental/uploads/avatar.png#fragment"
    })
    void refusesExternalBucketUnapprovedPrefixAndAlreadySignedUrls(String storedUrl) {
        assertNull(service.signedReadUrl(storedUrl));
        verifyNoInteractions(client, clientProvider);
    }

    @Test
    void signingIsUnavailableWhenStorageDisabledOrClientAbsent() {
        properties.setEnabled(false);
        assertNull(service.signedReadUrl(BASE_URL + "/" + OBJECT_KEY));
        verifyNoInteractions(clientProvider);

        properties.setEnabled(true);
        assertNull(service.signedReadUrl(BASE_URL + "/" + OBJECT_KEY));
        verifyNoInteractions(client);
    }

    @Test
    void honorsExplicitOwnDomainAndSignsTheUnderlyingConfiguredBucket() throws Exception {
        properties.setPublicBaseUrl("https://files.example.test/");
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(URI.create(BASE_URL + "/" + OBJECT_KEY + "?Signature=test").toURL());

        service.signedReadUrl("https://files.example.test/" + OBJECT_KEY);

        ArgumentCaptor<GeneratePresignedUrlRequest> request =
                ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(client).generatePresignedUrl(request.capture());
        assertEquals("dental-test", request.getValue().getBucketName());
        assertEquals(OBJECT_KEY, request.getValue().getKey());
    }
}
