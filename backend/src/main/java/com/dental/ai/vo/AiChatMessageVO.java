package com.dental.ai.vo;

import java.time.OffsetDateTime;

public record AiChatMessageVO(Long id, String requestId, String role, String content,
        AiChatAnswerVO answer, OffsetDateTime createdAt) { }
