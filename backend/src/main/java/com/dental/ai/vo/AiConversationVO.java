package com.dental.ai.vo;

import java.time.OffsetDateTime;

public record AiConversationVO(Long id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
