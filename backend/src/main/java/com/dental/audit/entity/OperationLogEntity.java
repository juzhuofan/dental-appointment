package com.dental.audit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 只追加的操作日志持久化对象。 */
@TableName("operation_log")
public class OperationLogEntity extends BaseEntity {

    private Long operatorUserId;
    private String operatorName;
    private String operatorRole;
    private String action;
    private String targetType;
    private String targetId;
    private String summary;
    private String ipAddress;

    public Long getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(Long operatorUserId) { this.operatorUserId = operatorUserId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
