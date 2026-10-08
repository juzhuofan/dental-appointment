package com.dental.appointment.entity;

import com.dental.common.BusinessException;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    public static AppointmentStatus parse(String value) {
        if (value == null) {
            throw BusinessException.bad("预约状态不能为空");
        }
        try {
            return AppointmentStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.bad("无效的预约状态");
        }
    }

    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }
}
