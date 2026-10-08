package com.dental.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 数据库幂等凭据保证重试后只生成一张预约单。 */
@TableName("appointment_idempotency")
public class AppointmentIdempotencyEntity extends BaseEntity {

    private Long userId;
    private String requestKey;
    private String requestHash;
    private Long appointmentId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
}
