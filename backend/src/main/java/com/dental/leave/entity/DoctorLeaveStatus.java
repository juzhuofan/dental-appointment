package com.dental.leave.entity;

import com.dental.common.BusinessException;

public enum DoctorLeaveStatus {
    PENDING, APPROVED, REJECTED, WITHDRAWN;

    public static DoctorLeaveStatus parse(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw BusinessException.bad("请假状态无效");
        }
    }
}
