package com.dental.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.service.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 微信账户的短数据库事务；唯一索引保证同一微信身份并发开户不重复。 */
@Service
public class WechatAccountService {

    private static final String ROLE_PATIENT = "PATIENT";
    private static final int ACCOUNT_ENABLED = 1;
    private static final int NOT_DELETED = 0;
    private static final int HASH_USERNAME_LENGTH = 61;

    private final SysUserMapper userMapper;
    private final UserService userService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    public WechatAccountService(SysUserMapper userMapper, UserService userService,
            AuthService authService, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.authService = authService;
        this.passwordEncoder = passwordEncoder;
    }

    public SysUser findByOpenid(String openid) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getWechatOpenid, openid));
        requireExactIdentity(user, openid);
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public LoginVO authenticate(String openid, String trustedPhone, boolean register) {
        SysUser user = findByOpenid(openid);
        boolean created = false;
        if (user == null) {
            if (!register) {
                throw registrationRequired();
            }
            user = createUser(openid, trustedPhone);
            try {
                // 不先锁不存在的 openid，避免并发首次开户形成 gap-lock 死锁。
                userMapper.insert(user);
                created = true;
            } catch (DuplicateKeyException exception) {
                user = lockByOpenid(openid);
                if (user == null) {
                    throw accountConflict();
                }
            }
        } else {
            user = lockByOpenid(openid);
        }
        requireEnabledPatient(user, created);
        if (StringUtils.hasText(trustedPhone) && StringUtils.hasText(user.getPhone())
                && !trustedPhone.equals(user.getPhone())) {
            throw accountConflict();
        }
        if (StringUtils.hasText(trustedPhone) && !StringUtils.hasText(user.getPhone())) {
            SysUser phoneOwner = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getPhone, trustedPhone));
            if (phoneOwner != null && !Objects.equals(phoneOwner.getId(), user.getId())) {
                throw accountConflict();
            }
            user.setPhone(trustedPhone);
            user.setUpdatedAt(utcNow());
            try {
                userMapper.updateById(user);
            } catch (DuplicateKeyException exception) {
                throw accountConflict();
            }
        }
        if (created) {
            userService.initializeWechatPatient(user);
        } else if (StringUtils.hasText(trustedPhone)) {
            // 只有角色已确认的患者才能同步服务端授权的手机号到患者档案。
            userService.initializeWechatPatient(user);
        }
        return authService.issueWechat(user);
    }

    private SysUser createUser(String openid, String trustedPhone) {
        if (StringUtils.hasText(trustedPhone)
                && userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getPhone, trustedPhone)) > 0) {
            // 手机号不能作为合并已有患者或管理员账号的依据。
            throw accountConflict();
        }
        SysUser user = new SysUser();
        user.setUsername("wx_" + sha256(openid).substring(0, HASH_USERNAME_LENGTH));
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setWechatOpenid(openid);
        user.setPhone(StringUtils.hasText(trustedPhone) ? trustedPhone : null);
        user.setDisplayName("微信用户");
        user.setStatus(ACCOUNT_ENABLED);
        user.setDeleted(NOT_DELETED);
        user.setCreatedAt(utcNow());
        user.setUpdatedAt(user.getCreatedAt());
        return user;
    }

    private SysUser lockByOpenid(String openid) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getWechatOpenid, openid).last("FOR UPDATE"));
        requireExactIdentity(user, openid);
        return user;
    }

    private void requireEnabledPatient(SysUser user, boolean created) {
        if (user == null || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)) {
            throw BusinessException.forbidden("微信账号已失效或停用");
        }
        if (!created && !userService.rolesOf(user.getId()).equals(List.of(ROLE_PATIENT))) {
            throw BusinessException.forbidden("微信账号角色不正确，请联系管理员");
        }
    }

    private static void requireExactIdentity(SysUser user, String openid) {
        if (user != null && !openid.equals(user.getWechatOpenid())) {
            throw accountConflict();
        }
    }

    public static BusinessException registrationRequired() {
        return BusinessException.conflict("WECHAT_REGISTRATION_REQUIRED", "请确认微信登录后创建账户");
    }

    private static BusinessException accountConflict() {
        return BusinessException.conflict("WECHAT_ACCOUNT_CONFLICT",
                "微信身份或手机号已被其他账号使用，请联系管理员");
    }

    private static String sha256(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
