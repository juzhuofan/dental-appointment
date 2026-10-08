package com.dental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 账号密码登录请求。 */
public record LoginDTO(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 100) String password) {
}
