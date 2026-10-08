package com.dental.department.vo;

/** 患者端和管理端共用的诊所配置响应。 */
public record ClinicVO(String name, String phone, String address, String openingHours,
                       String introduction, Integer cancelBeforeMinutes) {
}
