package com.dental.department.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 管理员保存科室请求。 */
public record DepartmentSaveDTO(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 1000) String description,
        @NotNull Integer sortOrder,
        @NotNull @Min(0) @Max(1) Integer status) {
}
