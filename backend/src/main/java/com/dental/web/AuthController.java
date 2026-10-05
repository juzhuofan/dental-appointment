package com.dental.web;

import com.dental.common.R;
import com.dental.service.AuthService;
import com.dental.service.UserService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@Valid @RequestBody R<Requests.Login> request) {
        return R.ok(auth.login(request.data()));
    }

    @PostMapping("/auth/demo-login")
    public R<Map<String, Object>> demoLogin(@Valid @RequestBody R<Requests.DemoLogin> request) {
        return R.ok(auth.demoLogin(request.data()));
    }

    @PostMapping("/auth/logout")
    public R<Map<String, Object>> logout(@Valid @RequestBody R<Map<String, Object>> request) {
        auth.logout();
        return R.ok(Map.of());
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(auth.me());
    }

    @PostMapping("/me/password")
    public R<Map<String, Object>> password(@Valid @RequestBody R<Requests.Password> request) {
        auth.password(request.data());
        return R.ok(Map.of());
    }

    @GetMapping("/me/patient-profile")
    @PreAuthorize("hasRole('PATIENT')")
    public R<Map<String, Object>> profile() {
        return R.ok(users.profile());
    }

    @PutMapping("/me/patient-profile")
    @PreAuthorize("hasRole('PATIENT')")
    public R<Map<String, Object>> profile(@Valid @RequestBody R<Requests.Profile> request) {
        return R.ok(users.saveProfile(request.data()));
    }
}
