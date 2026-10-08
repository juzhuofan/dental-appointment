package com.dental.schedule.dto;

import jakarta.validation.constraints.NotBlank;

public class ScheduleStatusDTO {

    @NotBlank
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
