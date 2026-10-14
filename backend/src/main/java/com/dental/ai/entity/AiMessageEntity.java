package com.dental.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 消息与当时的回答卡片快照，按会话和请求 ID 幂等保存。 */
@TableName("ai_message")
public class AiMessageEntity extends BaseEntity {

    private Long conversationId;
    private String requestId;
    private String role;
    private String content;
    private String metadataJson;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
