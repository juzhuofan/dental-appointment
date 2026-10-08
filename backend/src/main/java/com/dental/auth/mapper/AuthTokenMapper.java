package com.dental.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dental.auth.entity.AuthToken;
import org.apache.ibatis.annotations.Mapper;

/** 访问令牌持久化。 */
@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthToken> {
}
