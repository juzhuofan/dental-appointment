package com.dental.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AppointmentCreateDTO {

    @NotNull
    @Positive
    private Long scheduleId;

    @NotNull
    @Positive
    private Long patientProfileId;

    @Size(max = 500)
    private String chiefComplaint;

    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public Long getPatientProfileId() { return patientProfileId; }
    public void setPatientProfileId(Long patientProfileId) { this.patientProfileId = patientProfileId; }
    public String getChiefComplaint() { return chiefComplaint; }
    public void setChiefComplaint(String chiefComplaint) { this.chiefComplaint = chiefComplaint; }
}
