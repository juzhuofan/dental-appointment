package com.dental.doctor.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dental.common.BaseEntity;

/** 医生档案持久化对象。 */
@TableName("doctor")
public class DoctorEntity extends BaseEntity {

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long userId;
    private Long departmentId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String specialty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String introduction;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;
    private Integer status;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
