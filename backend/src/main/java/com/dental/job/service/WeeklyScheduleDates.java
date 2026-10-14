package com.dental.job.service;

import com.dental.common.TimeUtil;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

public final class WeeklyScheduleDates {
    private WeeklyScheduleDates() { }
    public static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    /** Most recent Sunday dispatch cycle; a delayed Monday execution still targets that Monday. */
    public static LocalDate targetForTrigger(Instant trigger) {
        return trigger.atZone(TimeUtil.CLINIC_ZONE).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusDays(1);
    }
    public static LocalDateTime utcStart(LocalDate day) {
        return TimeUtil.toUtc(day.atStartOfDay(TimeUtil.CLINIC_ZONE).toOffsetDateTime());
    }
}
