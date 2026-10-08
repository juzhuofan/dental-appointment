package com.dental.file.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.dental.config.OssProperties;
import com.dental.file.vo.FileUploadVO;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;

/** 使用环回 HTTP 服务验证 Java 17 下真实 SDK 的 V4 签名、字节上传及响应解析。 */
class OssClientCompatibilityTest {

    @Test
    @Timeout(15)
    void realSdkUploadsOnlyToLocalServerAndHandlesSuccessfulResponse() throws IOException {
        byte[] payload = "local OSS compatibility probe".getBytes(StandardCharsets.UTF_8);
        // 此 SDK 方法使用 JAXB，能直接发现 Java 17 缺少 javax.xml.bind 的依赖问题。
        assertEquals(Base64.getEncoder().encodeToString(payload), BinaryUtil.toBase64String(payload));
        AtomicReference<ReceivedUpload> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                received.set(new ReceivedUpload(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("Content-Length"),
                        exchange.getRequestHeaders().getFirst("Content-Disposition"),
                        exchange.getRequestHeaders().getFirst("x-oss-forbid-overwrite"), body));
                exchange.getResponseHeaders().set("ETag", "\"01234567890123456789012345678901\"");
                exchange.getResponseHeaders().set("x-oss-request-id", "local-test-request-id");
                exchange.sendResponseHeaders(200, -1);
            }
        });
        server.start();
        OSS client = null;
        try {
            ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
            configuration.setSignatureVersion(SignVersion.V4);
            configuration.setConnectionTimeout(2000);
            configuration.setSocketTimeout(2000);
            configuration.setMaxErrorRetry(0);
            // 回环 IP Endpoint 会自动使用 path style：/{bucket}/{key}，不会进行外部 DNS 请求。
            // supportCname 只控制域名模式；IP Endpoint 的路径仍由 SDK 按 path style 生成。
            configuration.setSupportCname(true);
            client = OSSClientBuilder.create()
                    .endpoint("http://127.0.0.1:" + server.getAddress().getPort())
                    .credentialsProvider(CredentialsProviderFactory.newDefaultCredentialProvider(
                            "local-test-key", "local-test-secret"))
                    .clientConfiguration(configuration)
                    .region("cn-beijing")
                    .build();
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            beanFactory.registerSingleton("localOssClient", client);
            OssProperties properties = new OssProperties();
            properties.setEnabled(true);
            properties.setBucketName("dental-upload-test");
            properties.setPublicBaseUrl("https://files.example.test");
            FileUploadService service = new FileUploadService(properties, beanFactory.getBeanProvider(OSS.class));

            FileUploadVO result = service.upload(
                    new MockMultipartFile("file", "probe.txt", "text/plain", payload));

            ReceivedUpload upload = received.get();
            assertNotNull(upload);
            assertEquals("PUT", upload.method());
            assertEquals("/dental-upload-test/" + result.objectKey(), upload.path());
            assertTrue(upload.authorization().startsWith("OSS4-HMAC-SHA256 "));
            assertTrue(upload.authorization().contains("local-test-key/"));
            assertTrue(upload.authorization().contains("/cn-beijing/oss/aliyun_v4_request"));
            assertArrayEquals(payload, upload.body());
            assertEquals("text/plain", upload.contentType());
            assertEquals(Integer.toString(payload.length), upload.contentLength());
            assertTrue(upload.contentDisposition().startsWith("attachment; filename=\""));
            assertEquals("true", upload.forbidOverwrite());
            assertEquals("https://files.example.test/" + result.objectKey(), result.url());
        } finally {
            if (client != null) {
                client.shutdown();
            }
            server.stop(0);
        }
    }

    private record ReceivedUpload(String method, String path, String authorization, String contentType,
                                  String contentLength, String contentDisposition, String forbidOverwrite,
                                  byte[] body) {
    }
}
