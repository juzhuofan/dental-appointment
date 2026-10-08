package com.dental.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.department.entity.DepartmentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 科室数据访问。 */
@Mapper
public interface DepartmentMapper extends BaseMapper<DepartmentEntity> {

    DepartmentEntity lockById(@Param("id") long id);
}
