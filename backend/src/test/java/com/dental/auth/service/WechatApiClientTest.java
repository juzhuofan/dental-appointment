package com.dental.auth.service;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 模拟微信 HTTPS 响应，不使用真实凭证或访问微信服务。 */
class WechatApiClientTest {

    private static final String APP_ID = "wx0123456789abcdef";
    private WechatProperties properties;
    private WechatApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new WechatProperties();
        properties.setEnabled(true);
        properties.setAppId(APP_ID);
        properties.setAppSecret("test-app-secret");
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.weixin.qq.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WechatApiClient(properties, builder.build());
    }

    @Test
    void verifiesLoginCodeWithWechatAndReturnsOnlyOpenid() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("appid", APP_ID))
                .andExpect(queryParam("secret", "test-app-secret"))
                .andExpect(queryParam("js_code", "login-code"))
                .andExpect(queryParam("grant_type", "authorization_code"))
                .andRespond(withSuccess("{\"openid\":\"trusted-openid\",\"session_key\":\"not-exposed\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("trusted-openid", client.exchangeLoginCode("login-code"));
        server.verify();
    }

    @Test
    void acceptsTextPlainJsonLoginResponseThroughRealMessageConverters() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"openid\":\"plain-openid\",\"session_key\":\"not-exposed\"}",
                        MediaType.TEXT_PLAIN));

        assertEquals("plain-openid", client.exchangeLoginCode("login-code"));
        server.verify();
    }

    @Test
    void mapsTextPlainInvalidCodeToAuthorizationFailureInsteadOfTransportFailure() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid code test-app-secret\"}",
                        MediaType.TEXT_PLAIN));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code"));

        assertEquals("WECHAT_CODE_INVALID", exception.getCode());
        assertEquals(400, exception.getHttpStatus());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("test-app-secret"));
        assertFalse(exception.getMessage().contains("login-code"));
        server.verify();
    }

    @Test
    void acceptsJsonLoginResponseWithoutContentTypeHeader() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess().body("{\"openid\":\"untyped-openid\"}"));

        assertEquals("untyped-openid", client.exchangeLoginCode("login-code"));
        server.verify();
    }

    @Test
    void rejectsHtmlResponseWithoutLeakingCredentialsOrRawUpstreamContent() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess("<html>proxy failure test-app-secret login-code cached-token</html>",
                        MediaType.TEXT_HTML));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code"));

        assertEquals("WECHAT_RESPONSE_INVALID", exception.getCode());
        assertEquals(502, exception.getHttpStatus());
        assertEquals("微信服务响应不完整或身份校验失败", exception.getMessage());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("test-app-secret"));
        assertFalse(exception.getMessage().contains("login-code"));
        assertFalse(exception.getMessage().contains("cached-token"));
        assertFalse(exception.getMessage().contains("<html>"));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"40029,400,WECHAT_CODE_INVALID", "40163,400,WECHAT_CODE_INVALID",
            "45011,503,WECHAT_SERVICE_BUSY", "40125,503,WECHAT_CAPABILITY_UNAVAILABLE",
            "48001,503,WECHAT_CAPABILITY_UNAVAILABLE", "99999,502,WECHAT_API_ERROR"})
    void translatesWechatErrorsWithoutEchoingUpstreamContent(int error, int status, String code) {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess("{\"errcode\":" + error
                        + ",\"errmsg\":\"secret login-code should not leak\"}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code"));

        assertEquals(status, exception.getHttpStatus());
        assertEquals(code, exception.getCode());
        assertFalse(exception.getMessage().contains("secret"));
        assertNull(exception.getCause());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"openid\":\"\"}",
            "{\"openid\":\"not valid id\"}", "{\"errcode\":\"unexpected\"}"})
    void rejectsMalformedIdentityResponses(String json) {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertEquals("WECHAT_RESPONSE_INVALID", assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code")).getCode());
        server.verify();
    }

    @Test
    void rejectsDisabledLoginAndBlankCodeWithoutNetworkCalls() {
        properties.setEnabled(false);
        assertEquals(503, assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code")).getHttpStatus());
        properties.setEnabled(true);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode(" ")).getHttpStatus());
        server.verify();
    }

    @Test
    void mapsHttpFailureWithoutLeakingCredentialsFromRequestUrl() {
        server.expect(requestTo(startsWith("https://api.weixin.qq.com/sns/jscode2session?")))
                .andRespond(withServerError());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.exchangeLoginCode("login-code"));
        assertEquals(502, exception.getHttpStatus());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("test-app-secret"));
        server.verify();
    }

}
