package com.dental.doctor.dto;

/** 医生查询条件。 */
public record DoctorQueryDTO(Long departmentId, String keyword, Integer status, int page, int size) {
}
