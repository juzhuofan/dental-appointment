package com.dental.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 本人密码修改请求。 */
public record PasswordChangeDTO(
        @NotBlank @Size(max = 100) String oldPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
