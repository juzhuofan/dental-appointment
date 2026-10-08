package com.dental.department.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 诊所公开资料与取消规则保存请求。 */
public record ClinicDTO(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String phone,
        @NotBlank @Size(max = 300) String address,
        @NotBlank @Size(max = 100) String openingHours,
        @NotBlank @Size(max = 2000) String introduction,
        @NotNull @Min(0) @Max(10080) Integer cancelBeforeMinutes) {
}
