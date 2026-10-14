package com.dental.ai.dto;

import java.time.LocalDate;
import java.util.List;

/** 模型生成的候选条件，必须经过业务服务验证后才能查询真实号源。 */
public record AppointmentIntentDTO(
        Boolean supported,
        String purpose,
        List<Long> departmentIds,
        List<Long> doctorIds,
        LocalDate dateFrom,
        LocalDate dateTo,
        String timeOfDay,
        List<Integer> weekdays,
        String clarification) {
}
