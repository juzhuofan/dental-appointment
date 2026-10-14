package com.dental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** loginCode 来自 wx.login。 */
public record WechatLoginDTO(
        @NotBlank @Size(max = 512) String loginCode,
        Boolean register) {
}
