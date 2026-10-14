package com.dental.ai.vo;

public record AiChatSendVO(AiConversationVO conversation, AiChatMessageVO userMessage,
        AiChatMessageVO assistantMessage) { }
