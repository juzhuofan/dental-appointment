package com.dental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 本地演示登录请求。 */
public record DemoLoginDTO(@NotBlank @Size(min = 8, max = 100) String deviceId) {
}
