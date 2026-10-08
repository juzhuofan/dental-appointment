package com.dental.schedule.entity;

import com.dental.common.BusinessException;

/** 排班有限状态集合。 */
public enum ScheduleStatus {
    DRAFT,
    PUBLISHED,
    CLOSED,
    CANCELLED;

    public static ScheduleStatus parse(String value) {
        if (value == null) {
            throw BusinessException.bad("排班状态不能为空");
        }
        try {
            return ScheduleStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.bad("无效的排班状态");
        }
    }
}
