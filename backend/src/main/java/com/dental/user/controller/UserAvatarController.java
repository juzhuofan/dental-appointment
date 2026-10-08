package com.dental.user.controller;

import com.dental.common.R;
import com.dental.user.service.UserAvatarService;
import com.dental.user.vo.UserVO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 本人头像由服务端上传至 OSS 并写入账号，客户端不能提交任意外部头像地址。 */
@RestController
@RequestMapping("/api/v1/me")
public class UserAvatarController {

    private final UserAvatarService avatarService;

    public UserAvatarController(UserAvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<UserVO> avatar(@RequestPart("file") MultipartFile file) {
        return R.ok(avatarService.upload(file));
    }
}
