package com.dental.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;

/** 管理端创建或编辑排班的输入。 */
public class ScheduleSaveDTO {

    @NotNull
    @Positive
    private Long doctorId;

    @NotNull
    private OffsetDateTime startTime;

    @NotNull
    private OffsetDateTime endTime;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer totalSlots;

    @NotNull
    private String status;

    @Min(0)
    @Max(10080)
    private Integer cancelBeforeMinutes;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public Integer getTotalSlots() { return totalSlots; }
    public void setTotalSlots(Integer totalSlots) { this.totalSlots = totalSlots; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCancelBeforeMinutes() { return cancelBeforeMinutes; }
    public void setCancelBeforeMinutes(Integer cancelBeforeMinutes) { this.cancelBeforeMinutes = cancelBeforeMinutes; }
}
