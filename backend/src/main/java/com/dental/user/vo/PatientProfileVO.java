package com.dental.user.vo;

import java.time.LocalDate;

/** 就诊人资料；管理端列表会对手机号脱敏。 */
public record PatientProfileVO(
        Long id,
        Long userId,
        boolean isDefault,
        String realName,
        String phone,
        Integer gender,
        LocalDate birthDate,
        String remark) {
}
