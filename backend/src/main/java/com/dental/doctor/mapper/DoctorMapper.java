package com.dental.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.doctor.entity.DoctorEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 医生数据访问。 */
@Mapper
public interface DoctorMapper extends BaseMapper<DoctorEntity> {

    DoctorEntity lockById(@Param("id") long id);

    List<DoctorEntity> lockByDepartment(@Param("departmentId") long departmentId);
}
