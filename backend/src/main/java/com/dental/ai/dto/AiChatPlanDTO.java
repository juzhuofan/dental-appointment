package com.dental.ai.dto;

import java.time.LocalDate;
import java.util.List;

/** 模型仅生成受限的查询/导诊计划，事实文本与健康建议由服务器组装。 */
public record AiChatPlanDTO(String topic, Boolean catalogMatched, List<String> clinicFields, List<Long> departmentIds,
        List<Long> doctorIds, String purpose, LocalDate dateFrom, LocalDate dateTo,
        String timeOfDay, List<Integer> weekdays, List<String> questions, String guidance) { }
