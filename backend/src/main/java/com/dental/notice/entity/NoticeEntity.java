package com.dental.notice.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;
import java.time.LocalDateTime;

/** 诊所公告持久化对象。 */
@TableName("clinic_notice")
public class NoticeEntity extends BaseEntity {

    private String title;
    private String content;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireAt;
    private Long createdBy;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
