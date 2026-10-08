package com.dental.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class TimeUtil {

    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime toUtc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static OffsetDateTime toClinic(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(CLINIC_ZONE).toOffsetDateTime();
    }

    public static LocalDate clinicToday() {
        return LocalDate.now(CLINIC_ZONE);
    }
}
