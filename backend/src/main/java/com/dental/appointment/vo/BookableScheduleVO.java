package com.dental.appointment.vo;

import java.time.LocalDateTime;

/** 预约创建时锁定号源后读取的完整数据。 */
public class BookableScheduleVO {

    private Long id;
    private Long doctorId;
    private Long departmentId;
    private String doctorName;
    private String departmentName;
    private Integer doctorStatus;
    private Integer departmentStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSlots;
    private Integer bookedSlots;
    private String status;
    private Integer cancelBeforeMinutes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Integer getDoctorStatus() { return doctorStatus; }
    public void setDoctorStatus(Integer doctorStatus) { this.doctorStatus = doctorStatus; }
    public Integer getDepartmentStatus() { return departmentStatus; }
    public void setDepartmentStatus(Integer departmentStatus) { this.departmentStatus = departmentStatus; }
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
}
