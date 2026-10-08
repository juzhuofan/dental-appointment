package com.dental.doctor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员保存医生档案请求。 */
public record DoctorSaveDTO(
        Long userId,
        @NotNull Long departmentId,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 80) String title,
        @Size(max = 500) String specialty,
        @Size(max = 5000) String introduction,
        @Size(max = 500) @Pattern(regexp = "^https?://.*$", message = "头像地址必须使用 http 或 https") String avatarUrl,
        @NotNull @Min(0) @Max(1) Integer status) {
}
