package com.dental.ai.vo;

import java.util.List;

public record AiMessagePageVO(List<AiChatMessageVO> records, boolean hasMore, Long nextBeforeId) { }
