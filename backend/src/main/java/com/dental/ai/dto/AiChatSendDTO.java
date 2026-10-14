package com.dental.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** requestId 为客户端稳定的重试标识，同一次发送重试必须保持内容不变。 */
public record AiChatSendDTO(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{8,64}", message = "发送标识格式不正确") String requestId,
        @NotBlank(message = "请输入聊天内容") @Size(max = 300, message = "每条消息最多 300 字") String content) { }
