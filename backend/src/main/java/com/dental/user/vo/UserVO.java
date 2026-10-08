package com.dental.user.vo;

import java.util.List;

/** 当前登录人公开信息。 */
public record UserVO(Long id, String username, String displayName, List<String> roles, Long doctorId,
                     String phone, String avatarUrl, String avatarDisplayUrl,
                     boolean wechatBound, boolean profileCompleted) {
}
