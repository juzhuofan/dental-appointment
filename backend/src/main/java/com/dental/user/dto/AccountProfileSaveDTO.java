package com.dental.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 当前微信账号的显示昵称。 */
public record AccountProfileSaveDTO(
        @NotBlank @Size(max = 80) String displayName) {
}
