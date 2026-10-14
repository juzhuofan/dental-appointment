package com.dental.ai.dto;

/** 后端从当前账号的数据库会话读取的上下文，不接受客户端提供历史角色。 */
public record AiChatTurnDTO(String role, String content) { }
