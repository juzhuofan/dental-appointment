package com.dental.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public class DoctorLeaveApplyDTO {
    @NotNull
    private OffsetDateTime startTime;
    @NotNull
    private OffsetDateTime endTime;
    @NotBlank
    @Size(max = 500)
    private String reason;

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
