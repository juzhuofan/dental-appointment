package com.dental.auth.controller;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.service.WechatAuthService;
import com.dental.auth.vo.LoginVO;
import com.dental.common.R;
import com.dental.config.WechatProperties;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 微信认证入口及不含凭证的能力配置。 */
@RestController
@RequestMapping("/api/v1/auth")
public class WechatAuthController {

    private final WechatAuthService authService;
    private final WechatProperties properties;

    public WechatAuthController(WechatAuthService authService, WechatProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/wechat-login")
    public R<LoginVO> login(@Valid @RequestBody R<WechatLoginDTO> request) {
        return R.ok(authService.login(request.data()));
    }

    @GetMapping("/wechat-config")
    public R<Map<String, Boolean>> configuration() {
        return R.ok(Map.of("enabled", properties.isEnabled(),
                "phoneNumberEnabled", properties.isEnabled() && properties.isPhoneNumberEnabled()));
    }
}
