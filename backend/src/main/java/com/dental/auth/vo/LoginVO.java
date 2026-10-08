package com.dental.auth.vo;

import com.dental.user.vo.UserVO;

/** 登录结果。 */
public record LoginVO(String token, UserVO user) {
}
