package com.dental.department.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.department.entity.SystemConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/** 诊所配置数据访问。 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfigEntity> {
}
