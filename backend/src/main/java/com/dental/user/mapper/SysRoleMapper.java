package com.dental.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/** 角色持久化。 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
}
