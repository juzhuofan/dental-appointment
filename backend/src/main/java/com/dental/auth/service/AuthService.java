package com.dental.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dental.audit.service.OperationLogService;
import com.dental.auth.dto.DemoLoginDTO;
import com.dental.auth.dto.LoginDTO;
import com.dental.auth.entity.AuthToken;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.security.CurrentUser;
import com.dental.security.JwtService;
import com.dental.user.dto.PasswordChangeDTO;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.service.UserService;
import com.dental.user.vo.UserVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 账号认证、演示登录和令牌生命周期。 */
@Service
public class AuthService {
    private static final int ACCOUNT_ENABLED = 1;
    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int NOT_DELETED = 0;
    private static final int TOKEN_ACTIVE = 0;
    private static final int TOKEN_REVOKED = 1;
    private static final int DEVICE_HASH_USERNAME_LENGTH = 40;
    private static final String ROLE_PATIENT = "PATIENT";

    private final SysUserMapper userMapper;
    private final AuthTokenMapper tokenMapper;
    private final UserService userService;
    private final OperationLogService audit;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final boolean demoLoginEnabled;
    private final int accessTokenMinutes;

    public AuthService(
            SysUserMapper userMapper,
            AuthTokenMapper tokenMapper,
            UserService userService,
            OperationLogService audit,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.demo-login.enabled:false}") boolean demoLoginEnabled,
            @Value("${app.jwt.access-token-minutes:480}") int accessTokenMinutes) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.userService = userService;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.demoLoginEnabled = demoLoginEnabled;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username().trim()));
        if (user == null || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)
                || user.getDemoDeviceHash() != null
                || user.getWechatOpenid() != null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("账号或密码错误");
        }
        return issue(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO demoLogin(DemoLoginDTO request) {
        if (!demoLoginEnabled) {
            throw BusinessException.forbidden("演示登录未开启");
        }
        String deviceHash = sha256(request.deviceId());
        SysUser user = findDemoUser(deviceHash);
        if (user == null) {
            user = new SysUser();
            user.setUsername("demo_" + deviceHash.substring(0, DEVICE_HASH_USERNAME_LENGTH));
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setDemoDeviceHash(deviceHash);
            user.setDisplayName("演示患者");
            user.setStatus(ACCOUNT_ENABLED);
            user.setDeleted(NOT_DELETED);
            user.setCreatedAt(utcNow());
            user.setUpdatedAt(user.getCreatedAt());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException exception) {
                user = findDemoUser(deviceHash);
                if (user == null) {
                    throw BusinessException.conflict("DEMO_LOGIN_CONFLICT", "演示账号创建冲突，请重试");
                }
            }
        }
        user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, user.getId()).last("FOR UPDATE"));
        if (user == null || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)) {
            throw BusinessException.forbidden("演示账号已停用");
        }
        userService.initializeDemoPatient(user);
        return issue(user);
    }

    /** 仅供已完成微信身份和患者角色校验的服务签发本系统凭证。 */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO issueWechat(SysUser user) {
        if (user == null || user.getWechatOpenid() == null
                || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)
                || !userService.rolesOf(user.getId()).equals(List.of(ROLE_PATIENT))) {
            throw BusinessException.forbidden("微信患者账号不可用");
        }
        return issue(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout() {
        CurrentUser current = CurrentUser.require();
        tokenMapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getTokenId, current.tokenId())
                .eq(AuthToken::getUserId, current.id())
                .eq(AuthToken::getRevoked, TOKEN_ACTIVE)
                .set(AuthToken::getRevoked, TOKEN_REVOKED)
                .set(AuthToken::getUpdatedAt, utcNow()));
        audit.record("LOGOUT", "sys_user", String.valueOf(current.id()), "主动退出登录");
    }

    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeDTO request) {
        CurrentUser current = CurrentUser.require();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getId, current.id()).last("FOR UPDATE"));
        if (user == null || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)) {
            throw BusinessException.unauthorized("账号已失效");
        }
        if (user.getDemoDeviceHash() != null || user.getWechatOpenid() != null) {
            throw BusinessException.bad("此账号使用微信身份登录，无需设置密码");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.bad("原密码不正确");
        }
        if (request.newPassword().length() < MINIMUM_PASSWORD_LENGTH) {
            throw BusinessException.bad("新密码至少8位");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(utcNow());
        userMapper.updateById(user);
        tokenMapper.update(null, new LambdaUpdateWrapper<AuthToken>()
                .eq(AuthToken::getUserId, user.getId())
                .eq(AuthToken::getRevoked, TOKEN_ACTIVE)
                .set(AuthToken::getRevoked, TOKEN_REVOKED)
                .set(AuthToken::getUpdatedAt, utcNow()));
        audit.record("PASSWORD_CHANGE", "sys_user", String.valueOf(user.getId()),
                "修改本人密码并使登录凭证失效");
    }

    private LoginVO issue(SysUser user) {
        List<String> roles = userService.rolesOf(user.getId());
        if (roles.isEmpty()) {
            throw BusinessException.forbidden("账号没有可用角色");
        }
        if (user.getDemoDeviceHash() != null && !roles.equals(List.of(ROLE_PATIENT))) {
            throw BusinessException.forbidden("演示账号角色不正确");
        }
        Instant expiresAt = Instant.now().plus(accessTokenMinutes, ChronoUnit.MINUTES);
        String tokenId = UUID.randomUUID().toString();
        AuthToken session = new AuthToken();
        session.setTokenId(tokenId);
        session.setUserId(user.getId());
        session.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        session.setRevoked(TOKEN_ACTIVE);
        session.setDeleted(NOT_DELETED);
        session.setCreatedAt(utcNow());
        session.setUpdatedAt(session.getCreatedAt());
        tokenMapper.insert(session);
        user.setLastLoginAt(utcNow());
        user.setUpdatedAt(user.getLastLoginAt());
        userMapper.updateById(user);
        UserVO userView = userService.toUserVO(user);
        audit.recordAs(user.getId(), user.getDisplayName(), roles.get(0),
                "LOGIN", "sys_user", String.valueOf(user.getId()), "账号登录成功");
        return new LoginVO(jwtService.issue(user.getId(), tokenId, expiresAt), userView);
    }

    private SysUser findDemoUser(String deviceHash) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDemoDeviceHash, deviceHash).last("FOR UPDATE"));
    }

    private static String sha256(String source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
