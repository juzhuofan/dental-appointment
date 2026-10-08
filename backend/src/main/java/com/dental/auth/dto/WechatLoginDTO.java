package com.dental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** loginCode 来自 wx.login；phoneCode 是独立的手机号授权凭证。 */
public record WechatLoginDTO(
        @NotBlank @Size(max = 512) String loginCode,
        @Size(max = 512) String phoneCode,
        Boolean register) {
}
