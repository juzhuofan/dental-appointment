package com.dental.notice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** 管理员保存公告请求。 */
public record NoticeSaveDTO(
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 10000) String content,
        @NotNull @Min(0) @Max(2) Integer status,
        OffsetDateTime publishAt,
        OffsetDateTime expireAt) {
}
