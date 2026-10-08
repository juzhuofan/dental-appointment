package com.dental.schedule.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

import java.time.LocalDateTime;

/** 医生一个可预约时段。时间以 UTC 存储。 */
@TableName("doctor_schedule")
public class ScheduleEntity extends BaseEntity {

    private Long doctorId;
    private Long departmentId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSlots;
    private Integer bookedSlots;
    private String status;
    private Integer cancelBeforeMinutes;
    private Integer version;
    private Long createdBy;

    @TableField(exist = false)
    private String doctorName;

    @TableField(exist = false)
    private String departmentName;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
    public Integer getBookedSlots() { return bookedSlots; }
    public void setBookedSlots(Integer bookedSlots) { this.bookedSlots = bookedSlots; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCancelBeforeMinutes() { return cancelBeforeMinutes; }
    public void setCancelBeforeMinutes(Integer cancelBeforeMinutes) { this.cancelBeforeMinutes = cancelBeforeMinutes; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
}
