package com.dental.leave.dto;

public class DoctorLeaveFilterDTO {
    private Integer page = 1;
    private Integer size = 20;
    private String status;
    private Long doctorId;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
}
