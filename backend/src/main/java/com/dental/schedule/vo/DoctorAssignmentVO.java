package com.dental.schedule.vo;

/** 排班写入时使用的医生及科室状态投影。 */
public class DoctorAssignmentVO {

    private Long doctorId;
    private Long departmentId;
    private Integer doctorStatus;
    private Integer departmentStatus;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Integer getDoctorStatus() { return doctorStatus; }
    public void setDoctorStatus(Integer doctorStatus) { this.doctorStatus = doctorStatus; }
    public Integer getDepartmentStatus() { return departmentStatus; }
    public void setDepartmentStatus(Integer departmentStatus) { this.departmentStatus = departmentStatus; }
}
