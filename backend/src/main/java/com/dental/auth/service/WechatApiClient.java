package com.dental.auth.service;

import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 仅信任微信服务端返回的身份，不接受客户端传入的 OpenID。 */
@Service
public class WechatApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatApiClient.class);
    private static final int MAX_RESPONSE_CHARACTERS = 65536;

    private static final Set<Integer> INVALID_CODE_ERRORS = Set.of(40029, 40163, 40226);
    private static final Set<Integer> BUSY_ERRORS = Set.of(-1, 45009, 45011, 45050);
    private static final Set<Integer> CONFIGURATION_ERRORS = Set.of(
            40013, 40125, 40164, 48001, 48002, 48004, 48005, 50001);
    private static final Set<Integer> TOKEN_ERRORS = Set.of(40001, 40014, 42001);
    private final WechatProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public WechatApiClient(WechatProperties properties,
            @Qualifier("wechatRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public WechatApiClient(WechatProperties properties, RestClient restClient) {
        this(properties, restClient, new ObjectMapper());
    }

    public String exchangeLoginCode(String loginCode) {
        requireAvailable();
        requireCode(loginCode);
        JsonNode result;
        try {
            result = readJsonResponse(restClient.get().uri(uri -> uri.path("/sns/jscode2session")
                    .queryParam("appid", properties.getAppId())
                    .queryParam("secret", properties.getAppSecret())
                    .queryParam("js_code", loginCode)
                    .queryParam("grant_type", "authorization_code").build())
                    .retrieve());
        } catch (RestClientException exception) {
            throw transportFailure("code2Session", exception);
        }
        validateResponse(result);
        String openid = result.path("openid").asText("");
        if (!openid.matches("[A-Za-z0-9_-]{1,128}")) {
            throw invalidResponse();
        }
        // session_key 不返回客户端、不保存到业务数据库，也不参与令牌生成。
        return openid;
    }

    /** 微信 code2Session 会以 text/plain 返回 JSON，不能依赖 JSON 媒体类型转换器。 */
    private JsonNode readJsonResponse(RestClient.ResponseSpec response) {
        String body = response.body(String.class);
        if (!StringUtils.hasText(body) || body.length() > MAX_RESPONSE_CHARACTERS) {
            throw invalidResponse();
        }
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw invalidResponse();
        }
    }

    private void requireAvailable() {
        if (!properties.isEnabled()) {
            throw new BusinessException("WECHAT_LOGIN_DISABLED",
                    "微信登录尚未启用", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static void requireCode(String code) {
        if (!StringUtils.hasText(code) || code.length() > 512) {
            throw BusinessException.bad("微信授权凭证不能为空且长度不得超过512字符");
        }
    }

    private static void validateResponse(JsonNode result) {
        if (result == null || !result.isObject()) {
            throw invalidResponse();
        }
        if (result.has("errcode") && !result.path("errcode").isIntegralNumber()) {
            throw invalidResponse();
        }
        int errorCode = result.path("errcode").asInt(0);
        if (errorCode == 0) {
            return;
        }
        if (INVALID_CODE_ERRORS.contains(errorCode)) {
            throw new BusinessException("WECHAT_CODE_INVALID",
                    "微信授权凭证已失效，请重新登录授权", HttpStatus.BAD_REQUEST);
        }
        if (BUSY_ERRORS.contains(errorCode)) {
            throw new BusinessException("WECHAT_SERVICE_BUSY",
                    "微信服务繁忙，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (CONFIGURATION_ERRORS.contains(errorCode) || TOKEN_ERRORS.contains(errorCode)) {
            throw new BusinessException("WECHAT_CAPABILITY_UNAVAILABLE",
                    "微信登录配置或接口权限不可用，请联系管理员", HttpStatus.SERVICE_UNAVAILABLE);
        }
        throw new BusinessException("WECHAT_API_ERROR",
                "微信授权失败，请重新操作", HttpStatus.BAD_GATEWAY);
    }

    private static BusinessException transportFailure(String operation, RestClientException exception) {
        Throwable cause = exception.getCause();
        LOGGER.warn("WeChat request failed: operation={}, exceptionType={}, causeType={}",
                operation, exception.getClass().getSimpleName(),
                cause == null ? "none" : cause.getClass().getSimpleName());
        // 不附带可能包含 AppSecret 或 code 的上游 URL/响应异常。
        return new BusinessException("WECHAT_API_UNAVAILABLE",
                "暂时无法连接微信服务，请稍后重试", HttpStatus.BAD_GATEWAY);
    }

    private static BusinessException invalidResponse() {
        return new BusinessException("WECHAT_RESPONSE_INVALID",
                "微信服务响应不完整或身份校验失败", HttpStatus.BAD_GATEWAY);
    }

}
