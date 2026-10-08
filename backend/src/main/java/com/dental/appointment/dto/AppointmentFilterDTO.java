package com.dental.appointment.dto;

import java.time.LocalDate;

/** URL 查询筛选项。 */
public class AppointmentFilterDTO {

    private String status;
    private Long doctorId;
    private Long departmentId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String keyword;
    private Integer page = 1;
    private Integer size = 10;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDate dateFrom) { this.dateFrom = dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public void setDateTo(LocalDate dateTo) { this.dateTo = dateTo; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
}
