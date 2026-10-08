package com.dental.auth.controller;

import com.dental.auth.dto.DemoLoginDTO;
import com.dental.auth.dto.LoginDTO;
import com.dental.auth.service.AuthService;
import com.dental.auth.vo.LoginVO;
import com.dental.common.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 登录与注销 HTTP 入口。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody R<LoginDTO> request) {
        return R.ok(authService.login(request.data()));
    }

    @PostMapping("/demo-login")
    public R<LoginVO> demoLogin(@Valid @RequestBody R<DemoLoginDTO> request) {
        return R.ok(authService.demoLogin(request.data()));
    }

    @PostMapping("/logout")
    public R<Map<String, Object>> logout(@Valid @RequestBody R<Map<String, Object>> request) {
        authService.logout();
        return R.ok(Map.of());
    }
}
