package com.dental.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.audit.entity.OperationLogEntity;
import org.apache.ibatis.annotations.Mapper;

/** 操作日志数据访问。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {
}
