package com.dental.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 账号角色关联持久化。 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
    SysUserRole lockIncludingDeleted(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int restore(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
