package com.dental.appointment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

import java.time.LocalDateTime;

/** 预约记录及就诊时数据快照。 */
@TableName("appointment")
public class AppointmentEntity extends BaseEntity {

    private String appointmentNo;
    private Long patientUserId;
    private Long patientProfileId;
    private Long scheduleId;
    private Long doctorId;
    private Long departmentId;
    private String patientNameSnapshot;
    private String patientPhoneSnapshot;
    private String doctorNameSnapshot;
    private String departmentNameSnapshot;
    private LocalDateTime startTimeSnapshot;
    private LocalDateTime endTimeSnapshot;
    private String chiefComplaint;
    private String status;
    private String appointmentActiveKey;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private Long handledBy;
    private LocalDateTime handledAt;

    @TableField(exist = false)
    private Integer cancelBeforeMinutes;

    public String getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }
    public Long getPatientUserId() { return patientUserId; }
    public void setPatientUserId(Long patientUserId) { this.patientUserId = patientUserId; }
    public Long getPatientProfileId() { return patientProfileId; }
    public void setPatientProfileId(Long patientProfileId) { this.patientProfileId = patientProfileId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getPatientNameSnapshot() { return patientNameSnapshot; }
    public void setPatientNameSnapshot(String patientNameSnapshot) { this.patientNameSnapshot = patientNameSnapshot; }
    public String getPatientPhoneSnapshot() { return patientPhoneSnapshot; }
    public void setPatientPhoneSnapshot(String patientPhoneSnapshot) { this.patientPhoneSnapshot = patientPhoneSnapshot; }
    public String getDoctorNameSnapshot() { return doctorNameSnapshot; }
    public void setDoctorNameSnapshot(String doctorNameSnapshot) { this.doctorNameSnapshot = doctorNameSnapshot; }
    public String getDepartmentNameSnapshot() { return departmentNameSnapshot; }
    public void setDepartmentNameSnapshot(String departmentNameSnapshot) { this.departmentNameSnapshot = departmentNameSnapshot; }
    public LocalDateTime getStartTimeSnapshot() { return startTimeSnapshot; }
    public void setStartTimeSnapshot(LocalDateTime startTimeSnapshot) { this.startTimeSnapshot = startTimeSnapshot; }
    public LocalDateTime getEndTimeSnapshot() { return endTimeSnapshot; }
    public void setEndTimeSnapshot(LocalDateTime endTimeSnapshot) { this.endTimeSnapshot = endTimeSnapshot; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAppointmentActiveKey() { return appointmentActiveKey; }
    public void setAppointmentActiveKey(String appointmentActiveKey) { this.appointmentActiveKey = appointmentActiveKey; }
    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Long getHandledBy() { return handledBy; }
    public void setHandledBy(Long handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public Integer getCancelBeforeMinutes() { return cancelBeforeMinutes; }
    public void setCancelBeforeMinutes(Integer cancelBeforeMinutes) { this.cancelBeforeMinutes = cancelBeforeMinutes; }
}
