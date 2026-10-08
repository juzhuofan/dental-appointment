package com.dental.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dental.audit.service.OperationLogService;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
import com.dental.file.service.FileUploadService;
import com.dental.user.entity.PatientProfile;
import com.dental.user.entity.SysRole;
import com.dental.user.entity.SysUser;
import com.dental.user.entity.SysUserRole;
import com.dental.user.mapper.PatientProfileMapper;
import com.dental.user.mapper.SysRoleMapper;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.mapper.SysUserRoleMapper;
import com.dental.user.vo.UserVO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 个人主体微信账号的资料完成判断与真实就诊信息初始化，不生成虚假联系方式。 */
@ExtendWith(MockitoExtension.class)
class UserWechatProfileTest {

    private static final Long USER_ID = 41L;
    private static final String AVATAR_URL = "https://dental-test.oss-cn-beijing.aliyuncs.com/"
            + "dental/uploads/2026/10/07/avatar.png";

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private PatientProfileMapper profileMapper;
    @Mock
    private AuthTokenMapper tokenMapper;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private OperationLogService audit;
    @Mock
    private FileUploadService uploadService;

    private WechatProperties properties;
    private UserService service;

    @BeforeEach
    void setUp() {
        properties = new WechatProperties();
        properties.setPhoneNumberEnabled(false);
        service = new UserService(userMapper, roleMapper, userRoleMapper, profileMapper,
                tokenMapper, encoder, audit, List.of(), uploadService, properties);
    }

    @Test
    void personalModeCompletesProfileWithoutPhoneAndSeparatesStoredAndDisplayUrls() {
        SysUser user = wechatUser();
        user.setAvatarUrl(AVATAR_URL);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        String signed = AVATAR_URL + "?Expires=123&Signature=test";
        when(uploadService.signedReadUrl(AVATAR_URL)).thenReturn(signed);

        UserVO result = service.toUserVO(user);

        assertTrue(result.wechatBound());
        assertTrue(result.profileCompleted());
        assertNull(result.phone());
        assertEquals(AVATAR_URL, result.avatarUrl());
        assertEquals(signed, result.avatarDisplayUrl());
        assertEquals(AVATAR_URL, user.getAvatarUrl());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void certifiedPhoneModeRequiresPhoneEvenWhenAvatarExists() {
        properties.setPhoneNumberEnabled(true);
        SysUser user = wechatUser();
        user.setAvatarUrl(AVATAR_URL);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        assertFalse(service.toUserVO(user).profileCompleted());
        user.setPhone("13900000001");
        assertTrue(service.toUserVO(user).profileCompleted());
    }

    @Test
    void firstWechatLoginWithoutAvatarIsAuthenticatedInPersonalMode() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        UserVO result = service.toUserVO(wechatUser());

        assertTrue(result.wechatBound());
        assertTrue(result.profileCompleted());
        assertNull(result.avatarUrl());
        assertNull(result.avatarDisplayUrl());
        verifyNoInteractions(uploadService);
    }

    @Test
    void certifiedPhoneModeCompletesProfileWithTrustedPhoneAndNoAvatar() {
        properties.setPhoneNumberEnabled(true);
        SysUser user = wechatUser();
        user.setPhone("13900000001");
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        UserVO result = service.toUserVO(user);

        assertTrue(result.wechatBound());
        assertTrue(result.profileCompleted());
        assertEquals("13900000001", result.phone());
        assertNull(result.avatarUrl());
        assertNull(result.avatarDisplayUrl());
        verifyNoInteractions(uploadService);
    }

    @Test
    void unboundAccountCannotBeMarkedAsWechatProfileComplete() {
        SysUser user = wechatUser();
        user.setWechatOpenid(null);
        user.setAvatarUrl(AVATAR_URL);
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        UserVO result = service.toUserVO(user);

        assertFalse(result.wechatBound());
        assertFalse(result.profileCompleted());
    }

    @Test
    void initializesFirstWechatPatientWithEmptyNameAndPhoneAndTimestamps() {
        stubPatientRole();
        SysUser user = wechatUser();

        service.initializeWechatPatient(user);

        ArgumentCaptor<PatientProfile> inserted = ArgumentCaptor.forClass(PatientProfile.class);
        verify(profileMapper).insert(inserted.capture());
        PatientProfile profile = inserted.getValue();
        assertEquals(USER_ID, profile.getUserId());
        assertEquals("", profile.getRealName());
        assertEquals("", profile.getPhone());
        assertEquals(0, profile.getGender());
        assertEquals(0, profile.getDeleted());
        assertNotNull(profile.getCreatedAt());
        assertEquals(profile.getCreatedAt(), profile.getUpdatedAt());
    }

    @Test
    void repeatedLoginPreservesExistingRealPatientDetails() {
        stubPatientRole();
        PatientProfile existing = new PatientProfile();
        existing.setId(9L);
        existing.setUserId(USER_ID);
        existing.setRealName("真实就诊人");
        existing.setPhone("13900000001");
        existing.setDeleted(0);
        existing.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(profileMapper.lockIncludingDeleted(USER_ID)).thenReturn(existing);

        service.initializeWechatPatient(wechatUser());

        assertEquals("真实就诊人", existing.getRealName());
        assertEquals("13900000001", existing.getPhone());
        assertEquals(LocalDateTime.of(2025, 1, 1, 0, 0), existing.getUpdatedAt());
        verify(profileMapper, never()).insert(any(PatientProfile.class));
        verify(profileMapper, never()).updateById(any(PatientProfile.class));
    }

    @Test
    void rejectsDeletedProfileInsteadOfSilentlyRestoringIt() {
        stubPatientRole();
        PatientProfile deleted = new PatientProfile();
        deleted.setId(9L);
        deleted.setDeleted(1);
        when(profileMapper.lockIncludingDeleted(USER_ID)).thenReturn(deleted);

        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.initializeWechatPatient(wechatUser()));

        assertEquals(403, failure.getHttpStatus());
        verify(profileMapper, never()).restore(any(), any());
        verify(profileMapper, never()).insert(any(PatientProfile.class));
    }

    private void stubPatientRole() {
        SysUserRole link = new SysUserRole();
        link.setUserId(USER_ID);
        link.setRoleId(3L);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("PATIENT");
        when(userRoleMapper.selectList(any())).thenReturn(List.of(link));
        when(roleMapper.selectByIds(any())).thenReturn(List.of(role));
    }

    private static SysUser wechatUser() {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("wechat-test");
        user.setDisplayName("微信用户");
        user.setWechatOpenid("openid-test-only");
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }
}
