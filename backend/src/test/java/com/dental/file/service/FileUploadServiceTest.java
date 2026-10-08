package com.dental.file.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.dental.common.BusinessException;
import com.dental.config.OssProperties;
import com.dental.file.vo.FileUploadVO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** 不访问云资源，验证实际上传请求、资源释放和可保存的稳定链接。 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

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
        properties.setBucketName("dental-upload-test");
        properties.setAccessKeyId("test-access-key");
        properties.setAccessKeySecret("test-access-secret");
        service = new FileUploadService(properties, clientProvider);
    }

    @Test
    void uploadsActualBytesWithUniqueKeyAndUnsignedUrlAndClosesStream() throws IOException {
        byte[] bytes = "fake image bytes".getBytes(StandardCharsets.UTF_8);
        TrackingInputStream stream = new TrackingInputStream(bytes);
        MultipartFile file = multipart("C:\\fakepath\\牙齿照片.PNG", bytes.length, stream);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            assertFalse(stream.closed);
            assertArrayEquals(bytes, request.getInputStream().readAllBytes());
            return new PutObjectResult();
        });

        FileUploadVO result = service.upload(file);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(requestCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertEquals("dental-upload-test", request.getBucketName());
        assertEquals(result.objectKey(), request.getKey());
        assertTrue(result.objectKey().matches("dental/uploads/\\d{4}/\\d{2}/\\d{2}/[a-f0-9]{32}\\.png"));
        assertEquals("https://dental-upload-test.oss-cn-hangzhou.aliyuncs.com/"
                + result.objectKey(), result.url());
        assertFalse(result.url().contains("?"));
        assertEquals("牙齿照片.PNG", result.originalFilename());
        assertEquals(bytes.length, result.size());
        assertEquals("image/png", result.contentType());
        assertEquals(bytes.length, request.getMetadata().getContentLength());
        assertEquals("image/png", request.getMetadata().getContentType());
        assertNull(request.getMetadata().getContentDisposition());
        assertEquals("true", request.getHeaders().get("x-oss-forbid-overwrite"));
        assertTrue(stream.closed);
        verifyNoMoreInteractions(client);
    }

    @Test
    void documentIsDownloadableAndRepeatedFilenamesDoNotOverwrite() {
        properties.setPublicBaseUrl("https://files.example.test/dental///");
        when(clientProvider.getIfAvailable()).thenReturn(client);
        MockMultipartFile file = new MockMultipartFile("file", "病例.pdf", "text/html", new byte[]{1, 2});

        FileUploadVO first = service.upload(file);
        FileUploadVO second = service.upload(file);

        assertNotEquals(first.objectKey(), second.objectKey());
        assertEquals("https://files.example.test/dental/" + first.objectKey(), first.url());
        assertEquals("application/pdf", first.contentType());
        ArgumentCaptor<PutObjectRequest> requests = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client, org.mockito.Mockito.times(2)).putObject(requests.capture());
        for (PutObjectRequest request : requests.getAllValues()) {
            assertTrue(request.getMetadata().getContentDisposition().startsWith("attachment; filename=\""));
            assertFalse(request.getMetadata().getContentDisposition().contains("病例"));
            assertEquals("true", request.getHeaders().get("x-oss-forbid-overwrite"));
        }
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void rejectsEmptyAndUnsupportedFilesBeforeAccessingOss(MultipartFile file) {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(file));
        assertEquals(400, exception.getHttpStatus());
        assertEquals("INVALID_ARGUMENT", exception.getCode());
        verifyNoInteractions(client, clientProvider);
    }

    private static Stream<Arguments> invalidFiles() {
        return Stream.of(
                Arguments.of((MultipartFile) null),
                Arguments.of(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0])),
                Arguments.of(new MockMultipartFile("file", "", "image/jpeg", new byte[]{1})),
                Arguments.of(new MockMultipartFile("file", "README", "text/plain", new byte[]{1})),
                Arguments.of(new MockMultipartFile("file", "payload.exe", "image/jpeg", new byte[]{1})),
                Arguments.of(new MockMultipartFile("file", "photo.svg", "image/svg+xml", new byte[]{1})));
    }

    @Test
    void enforcesConfiguredLimitAndAcceptsTheBoundary() {
        properties.setMaxFileSize(DataSize.ofBytes(2));
        MockMultipartFile tooLarge = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[3]);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(tooLarge));
        assertEquals(413, exception.getHttpStatus());
        assertEquals("FILE_TOO_LARGE", exception.getCode());
        verifyNoInteractions(client, clientProvider);

        when(clientProvider.getIfAvailable()).thenReturn(client);
        FileUploadVO result = service.upload(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[2]));
        assertEquals(2, result.size());
        verify(client).putObject(any(PutObjectRequest.class));
    }

    @Test
    void disabledStorageOrMissingClientReturnsUnavailableWithoutUploading() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});
        properties.setEnabled(false);

        BusinessException disabled = assertThrows(BusinessException.class, () -> service.upload(file));
        assertEquals(503, disabled.getHttpStatus());
        assertEquals("FILE_STORAGE_NOT_CONFIGURED", disabled.getCode());

        properties.setEnabled(true);
        BusinessException absentClient = assertThrows(BusinessException.class, () -> service.upload(file));
        assertEquals(503, absentClient.getHttpStatus());
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @MethodSource("sdkFailures")
    void translatesSdkFailureWithoutExposingDetailsAndAlwaysClosesStream(RuntimeException sdkFailure)
            throws IOException {
        TrackingInputStream stream = new TrackingInputStream(new byte[]{1});
        MultipartFile file = multipart("photo.jpg", 1, stream);
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(sdkFailure);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(502, exception.getHttpStatus());
        assertEquals("FILE_UPLOAD_FAILED", exception.getCode());
        assertFalse(exception.getMessage().contains("sensitive"));
        assertTrue(stream.closed);
    }

    private static Stream<Arguments> sdkFailures() {
        return Stream.of(Arguments.of(new OSSException("sensitive upstream response")),
                Arguments.of(new ClientException("sensitive client detail")));
    }

    @Test
    void translatesLocalStreamReadFailureWithoutCallingOss() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("report.pdf");
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenThrow(new IOException("sensitive local pathname"));
        when(clientProvider.getIfAvailable()).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.upload(file));

        assertEquals(502, exception.getHttpStatus());
        assertEquals("FILE_UPLOAD_FAILED", exception.getCode());
        assertFalse(exception.getMessage().contains("sensitive"));
        verifyNoInteractions(client);
    }

    private static MultipartFile multipart(String originalFilename, long size, InputStream stream)
            throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(originalFilename);
        when(file.getSize()).thenReturn(size);
        when(file.getInputStream()).thenReturn(stream);
        return file;
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
