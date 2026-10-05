package com.dental.common;

import java.util.Arrays;
import java.util.List;

/** 预约状态机唯一来源；终态不会再次释放号源。 */
public enum AppointmentState {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;
    public static final String TARGET_PATTERN = "CONFIRMED|CANCELLED|COMPLETED|NO_SHOW";

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    public boolean allows(AppointmentState target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == COMPLETED || target == NO_SHOW || target == CANCELLED;
            default -> false;
        };
    }

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
