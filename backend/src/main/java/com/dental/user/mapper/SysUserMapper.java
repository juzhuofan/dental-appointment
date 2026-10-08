package com.dental.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 账号持久化。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    Long findActiveDoctorId(@Param("userId") Long userId);
}
