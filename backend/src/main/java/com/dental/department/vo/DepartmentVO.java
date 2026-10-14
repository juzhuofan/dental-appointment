package com.dental.department.vo;

/** 科室响应。 */
public record DepartmentVO(Long id, String name, String description, String avatarUrl,
                           String avatarDisplayUrl, Integer sortOrder, Integer status) {
}
