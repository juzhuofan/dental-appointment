package com.dental.department.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 科室持久化对象。 */
@TableName("department")
public class DepartmentEntity extends BaseEntity {

    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    private Integer sortOrder;
    private Integer status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
