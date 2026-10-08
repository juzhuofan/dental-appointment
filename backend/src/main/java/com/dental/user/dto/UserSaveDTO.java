package com.dental.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 管理员维护账号请求。 */
public record UserSaveDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.-]{3,64}") String username,
        @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 80) String displayName,
        @NotBlank @Pattern(regexp = "ADMIN|DOCTOR|PATIENT") String role,
        @NotNull @Min(0) @Max(1) Integer status) {
}
