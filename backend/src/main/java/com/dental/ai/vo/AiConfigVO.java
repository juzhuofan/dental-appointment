package com.dental.ai.vo;

/** 公开能力标志，不返回模型密钥或客户端配置。 */
public record AiConfigVO(boolean enabled, int maxMessageLength) {
}
