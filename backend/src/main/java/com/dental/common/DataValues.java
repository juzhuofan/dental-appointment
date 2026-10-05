package com.dental.common;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** 数据库存 UTC；界面日期始终按诊所时区计算。 */
public final class DataValues {
    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

    private DataValues() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime utc(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static LocalDateTime time(Object value) {
        if (value instanceof LocalDateTime local) {
            return local;
        }
        if (value instanceof Timestamp stamp) {
            return stamp.toLocalDateTime();
        }
        throw new IllegalArgumentException("Invalid database timestamp");
    }

    public static long number(Object value) {
        return ((Number) value).longValue();
    }

    public static int integer(Object value) {
        return ((Number) value).intValue();
    }

    public static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            fields.put((String) pairs[index], pairs[index + 1]);
        }
        return fields;
    }

    public static Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> output = new LinkedHashMap<>();
        row.forEach(
                (key, value) -> {
                    if (value instanceof LocalDateTime || value instanceof Timestamp) {
                        output.put(
                                key,
                                time(value)
                                        .atOffset(ZoneOffset.UTC)
                                        .atZoneSameInstant(CLINIC_ZONE)
                                        .toOffsetDateTime());
                    } else if (value instanceof java.sql.Date date) {
                        output.put(key, date.toLocalDate());
                    } else {
                        output.put(key, value);
                    }
                });
        return output;
    }

    public static void dateFilters(
            Map<String, Object> filter, String field, String from, String to) {
        if (from != null && !from.isBlank()) {
            filter.put(
                    field + "Ge",
                    LocalDate.parse(from)
                            .atStartOfDay(CLINIC_ZONE)
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime());
        }
        if (to != null && !to.isBlank()) {
            filter.put(
                    field + "Lt",
                    LocalDate.parse(to)
                            .plusDays(1)
                            .atStartOfDay(CLINIC_ZONE)
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime());
        }
    }

    public static String maskPhone(String phone) {
        return phone != null && phone.length() >= 7
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
                : "***";
    }
}
