package com.dental.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class DoctorLeaveReviewDTO {
    @NotNull
    private Boolean approved;
    @Size(max = 500)
    private String remark;
    private Boolean confirmCancelAppointments = false;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getConfirmCancelAppointments() { return confirmCancelAppointments; }
    public void setConfirmCancelAppointments(Boolean value) { this.confirmCancelAppointments = value; }
}
