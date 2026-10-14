package com.dental.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 账号私有聊天会话。 */
@TableName("ai_conversation")
public class AiConversationEntity extends BaseEntity {

    private Long userId;
    private String title;
    private Integer version;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
