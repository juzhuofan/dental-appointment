package com.dental.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 默认就诊人资料保存请求。 */
public record PatientProfileSaveDTO(
        @NotBlank @Size(max = 80) String realName,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}", message = "请输入11位手机号") String phone,
        @Min(0) @Max(2) Integer gender,
        @PastOrPresent LocalDate birthDate,
        @Size(max = 255) String remark) {
}
