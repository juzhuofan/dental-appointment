package com.dental.appointment.vo;

import java.time.OffsetDateTime;

public record AppointmentVO(
        Long id,
        String appointmentNo,
        Long patientUserId,
        Long patientProfileId,
        Long scheduleId,
        Long doctorId,
        Long departmentId,
        String patientName,
        String patientPhone,
        String doctorName,
        String departmentName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        String chiefComplaint,
        String status,
        Integer cancelBeforeMinutes,
        String cancelReason,
        OffsetDateTime cancelledAt,
        OffsetDateTime createdAt) {
}
