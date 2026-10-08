package com.dental.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class WechatAccountServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private AuthService authService;
    @Mock
    private PasswordEncoder passwordEncoder;
    private WechatAccountService accounts;
    private final LoginVO login = new LoginVO("business-token", null);

    @BeforeEach
    void setUp() {
        accounts = new WechatAccountService(userMapper, userService, authService, passwordEncoder);
    }

    @Test
    void refusesSilentRegistrationWithoutWritingAnyAccount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", null, false));
        assertEquals("WECHAT_REGISTRATION_REQUIRED", exception.getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(userService, never()).initializeWechatPatient(any());
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void createsConsentedPatientWithHashedUsernameAndEmptyPhoneAndTimestamps() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser created = invocation.getArgument(0);
            created.setId(11L);
            return 1;
        });
        when(authService.issueWechat(any())).thenReturn(login);

        assertSame(login, accounts.authenticate("wechat-real-openid", null, true));

        ArgumentCaptor<SysUser> captured = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captured.capture());
        SysUser user = captured.getValue();
        assertEquals("wechat-real-openid", user.getWechatOpenid());
        assertEquals(64, user.getUsername().length());
        assertNotEquals("wechat-real-openid", user.getUsername());
        assertNull(user.getPhone());
        assertNull(user.getDemoDeviceHash());
        assertEquals("encoded-random-password", user.getPasswordHash());
        assertEquals(0, user.getDeleted());
        assertNotNull(user.getCreatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        verify(userService).initializeWechatPatient(user);
    }

    @Test
    void storesWechatVerifiedPhoneWhenRegistering() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(authService.issueWechat(any())).thenReturn(login);
        assertSame(login, accounts.authenticate("openid", "13812345678", true));
        ArgumentCaptor<SysUser> captured = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captured.capture());
        assertEquals("13812345678", captured.getValue().getPhone());
        verify(userService).initializeWechatPatient(captured.getValue());
    }

    @Test
    void refusesPhoneAlreadyUsedByAnyOtherAccountWithoutMergingIt() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.authenticate("new-openid", "13812345678", true)).getCode());
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void reloginRequiresExistingPatientRoleAndDoesNotCreateOrRebindUser() {
        SysUser existing = existingUser("openid", 1);
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userService.rolesOf(10L)).thenReturn(List.of("PATIENT"));
        when(authService.issueWechat(existing)).thenReturn(login);
        assertSame(login, accounts.authenticate("openid", null, false));
        verify(userMapper, never()).insert(any(SysUser.class));
        verify(userService, never()).initializeWechatPatient(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "DOCTOR", "NONE", "PATIENT_ADMIN"})
    void refusesExistingNonPatientOrUnassignedRolesInsteadOfRepairingThem(String role) {
        SysUser existing = existingUser("openid", 1);
        when(userMapper.selectOne(any())).thenReturn(existing);
        List<String> roles = switch (role) {
            case "NONE" -> List.of();
            case "PATIENT_ADMIN" -> List.of("PATIENT", "ADMIN");
            default -> List.of(role);
        };
        when(userService.rolesOf(10L)).thenReturn(roles);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", null, true)).getHttpStatus());
        verify(userService, never()).initializeWechatPatient(any());
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void rejectsDisabledAccountWithoutTokensOrRoleChanges() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("openid", 0));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", null, true)).getHttpStatus());
        verify(authService, never()).issueWechat(any());
        verify(userService, never()).initializeWechatPatient(any());
    }

    @Test
    void duplicateConcurrentOpenidRegistrationUsesWinningPatientAccount() {
        SysUser winner = existingUser("openid", 1);
        when(userMapper.selectOne(any())).thenReturn(null, winner);
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(userService.rolesOf(10L)).thenReturn(List.of("PATIENT"));
        when(authService.issueWechat(winner)).thenReturn(login);

        assertSame(login, accounts.authenticate("openid", null, true));

        verify(userService, never()).initializeWechatPatient(any());
        verify(authService).issueWechat(winner);
    }

    @Test
    void refusesUnrelatedUniqueConflictWithoutClaimingAnyExistingUser() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(userMapper.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", null, true)).getCode());
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void rejectsCaseInsensitiveDatabaseMismatchToPreserveWechatIdentity() {
        when(userMapper.selectOne(any())).thenReturn(existingUser("OpenId", 1));
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.findByOpenid("openid")).getCode());
    }

    @Test
    void phoneConflictDuringBindingDoesNotAlterAccount() {
        SysUser existing = existingUser("openid", 1);
        SysUser phoneOwner = existingUser("different-openid", 1);
        phoneOwner.setId(20L);
        when(userMapper.selectOne(any())).thenReturn(existing, existing, phoneOwner);
        when(userService.rolesOf(10L)).thenReturn(List.of("PATIENT"));
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", "13812345678", false)).getCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void translatesConcurrentPhoneUniqueViolationAndDoesNotIssueToken() {
        SysUser existing = existingUser("openid", 1);
        when(userMapper.selectOne(any())).thenReturn(existing, existing, null);
        when(userService.rolesOf(10L)).thenReturn(List.of("PATIENT"));
        when(userMapper.updateById(any(SysUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate-phone"));
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", "13812345678", false)).getCode());
        verify(userService, never()).initializeWechatPatient(any());
        verify(authService, never()).issueWechat(any());
    }

    @Test
    void refusesDifferentVerifiedPhoneOnSameIdentityInsteadOfReplacingExistingBinding() {
        SysUser existing = existingUser("openid", 1);
        existing.setPhone("13912345678");
        when(userMapper.selectOne(any())).thenReturn(existing);
        when(userService.rolesOf(10L)).thenReturn(List.of("PATIENT"));
        assertEquals("WECHAT_ACCOUNT_CONFLICT", assertThrows(BusinessException.class,
                () -> accounts.authenticate("openid", "13812345678", false)).getCode());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(authService, never()).issueWechat(any());
    }

    private static SysUser existingUser(String openid, int status) {
        SysUser user = new SysUser();
        user.setId(10L);
        user.setWechatOpenid(openid);
        user.setStatus(status);
        return user;
    }
}
