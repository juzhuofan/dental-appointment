package com.dental.service;

import com.dental.common.BusinessException;
import com.dental.common.DataValues;
import com.dental.persistence.BusinessMapper;
import com.dental.security.CurrentUser;
import com.dental.security.JwtService;
import com.dental.web.Requests;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private final StoreService store;
    private final BusinessMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final boolean demoEnabled;
    private final int tokenHours;

    public AuthService(
            StoreService store,
            BusinessMapper mapper,
            PasswordEncoder passwordEncoder,
            JwtService jwt,
            @Value("${app.demo-enabled}") boolean demoEnabled,
            @Value("${app.token-hours}") int tokenHours) {
        this.store = store;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.demoEnabled = demoEnabled;
        this.tokenHours = tokenHours;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(Requests.Login request) {
        var user = store.findOne("sys_user", DataValues.fields("username", request.username()));
        if (user != null) {
            user = store.require("sys_user", DataValues.number(user.get("id")), true);
        }
        if (user == null
                || DataValues.integer(user.get("status")) != 1
                || user.get("demoDeviceHash") != null
                || !passwordEncoder.matches(
                        request.password(), (String) user.get("passwordHash"))) {
            throw new BusinessException("LOGIN_FAILED", "账号或密码错误", 401);
        }
        return issue(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> demoLogin(Requests.DemoLogin request) {
        if (!demoEnabled) {
            throw new BusinessException("DEMO_DISABLED", "演示登录未开启", 403);
        }
        String hash = sha256(request.deviceId());
        mapper.upsertDemo(
                hash,
                "demo_" + hash.substring(0, 40),
                passwordEncoder.encode(UUID.randomUUID().toString()));
        var user = store.findOne("sys_user", DataValues.fields("demoDeviceHash", hash));
        if (user == null || DataValues.integer(user.get("status")) != 1) {
            throw new BusinessException("ACCOUNT_DISABLED", "演示账号已停用", 403);
        }
        long id = DataValues.number(user.get("id"));
        var roles = mapper.roles(id);
        if (roles.isEmpty()) {
            mapper.bindRole(id, "PATIENT");
        } else if (roles.size() != 1 || !roles.contains("PATIENT")) {
            throw BusinessException.forbidden();
        }
        mapper.upsertProfile(id, "演示患者" + id);
        return issue(user);
    }

    private Map<String, Object> issue(Map<String, Object> user) {
        long id = DataValues.number(user.get("id"));
        var roles = mapper.roles(id);
        if (roles.isEmpty()) {
            throw BusinessException.forbidden();
        }
        String tokenId = UUID.randomUUID().toString();
        Instant expiry = Instant.now().plusSeconds(tokenHours * 3600L);
        store.insert(
                "auth_token",
                DataValues.fields(
                        "tokenId",
                        tokenId,
                        "userId",
                        id,
                        "expiresAt",
                        LocalDateTime.ofInstant(expiry, ZoneOffset.UTC),
                        "revoked",
                        0));
        store.update("sys_user", id, DataValues.fields("lastLoginAt", DataValues.now()));
        return DataValues.fields(
                "token", jwt.issue(id, tokenId, expiry), "user", userView(user, roles));
    }

    public Map<String, Object> me() {
        var current = CurrentUser.get();
        return userView(store.require("sys_user", current.id(), false), current.roles());
    }

    private Map<String, Object> userView(Map<String, Object> row, java.util.List<String> roles) {
        long id = DataValues.number(row.get("id"));
        return DataValues.fields(
                "id",
                id,
                "username",
                row.get("username"),
                "displayName",
                row.get("displayName"),
                "roles",
                roles,
                "doctorId",
                mapper.doctorId(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout() {
        mapper.revokeToken(CurrentUser.get().tokenId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void password(Requests.Password request) {
        var current = CurrentUser.get();
        var user = store.require("sys_user", current.id(), true);
        if (user.get("demoDeviceHash") != null) {
            throw BusinessException.bad("演示患者使用点击登录，无需设置密码");
        }
        if (!passwordEncoder.matches(request.oldPassword(), (String) user.get("passwordHash"))) {
            throw new BusinessException("PASSWORD_INCORRECT", "原密码不正确", 400);
        }
        store.update(
                "sys_user",
                current.id(),
                DataValues.fields("passwordHash", passwordEncoder.encode(request.newPassword())));
        mapper.revokeUser(current.id());
        store.audit("PASSWORD_CHANGE", "sys_user", current.id(), "修改本人密码并使登录凭证失效");
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
