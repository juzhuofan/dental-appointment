package com.dental.schedule.vo;

import java.time.OffsetDateTime;

/** 前端看到的排班及实时余量。 */
public record ScheduleVO(
        Long id,
        Long doctorId,
        String doctorName,
        Long departmentId,
        String departmentName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Integer totalSlots,
        Integer bookedSlots,
        Integer remainingSlots,
        String status,
        Integer cancelBeforeMinutes) {
}
