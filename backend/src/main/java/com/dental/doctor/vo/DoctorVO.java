package com.dental.doctor.vo;

/** 医生公开及管理端响应。 */
public record DoctorVO(Long id, Long userId, Long departmentId, String departmentName,
                       String name, String title, String specialty, String introduction,
                       String avatarUrl, Integer status) {
}
