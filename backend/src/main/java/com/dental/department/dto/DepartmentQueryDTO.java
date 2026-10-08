package com.dental.department.dto;

/** 科室查询条件，由服务层统一校验分页边界。 */
public record DepartmentQueryDTO(String keyword, Integer status, int page, int size) {
}
