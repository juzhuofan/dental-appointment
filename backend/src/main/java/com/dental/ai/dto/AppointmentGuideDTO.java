package com.dental.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 小程序传入用户需求，以及当前页面选中的医生或科室上下文。
 */
public record AppointmentGuideDTO(
    @NotBlank(message = "请描述预约需求") @Size(max = 300, message = "预约需求最多 300 字") String message,
    @Positive Long departmentId,
    @Positive Long doctorId) {
}
