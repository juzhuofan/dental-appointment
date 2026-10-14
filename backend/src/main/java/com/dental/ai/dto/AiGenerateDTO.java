package com.dental.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** 通用文本接口仅向管理员开放；普通业务优先通过 AiService 调用。 */
public record AiGenerateDTO(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,63}") String templateId,
        @NotNull @Size(max = 16) Map<@NotBlank String, @NotNull @Size(max = 6000) String> variables) {
}
