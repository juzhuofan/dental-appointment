package com.dental.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.user.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WechatAuthServiceTest {

    @Mock
    private WechatApiClient apiClient;
    @Mock
    private WechatAccountService accounts;
    private WechatAuthService service;
    private final LoginVO login = new LoginVO("business-token", null);

    @BeforeEach
    void setUp() {
        service = new WechatAuthService(apiClient, accounts);
        when(apiClient.exchangeLoginCode("login-code")).thenReturn("server-openid");
    }

    @Test
    void firstSilentLoginDoesNotCreateAccount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginDTO("login-code", null)));
        assertEquals("WECHAT_REGISTRATION_REQUIRED", exception.getCode());
        verify(accounts, never()).authenticate(anyString(), anyBoolean());
    }

    @Test
    void personalAccountRegistersOnlyAfterUserConfirmationWithoutInventedPhone() {
        when(accounts.authenticate("server-openid", true)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", true)));
        verify(accounts).authenticate("server-openid", true);
    }

    @Test
    void laterPersonalLoginUsesServerVerifiedIdentityWithoutNewPhoneAuthorization() {
        SysUser existing = enabledUser();
        when(accounts.findByOpenid("server-openid")).thenReturn(existing);
        when(accounts.authenticate("server-openid", false)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", false)));
    }

    @Test
    void rejectsDisabledUserBeforeIssuingToken() {
        SysUser existing = enabledUser();
        existing.setStatus(0);
        when(accounts.findByOpenid("server-openid")).thenReturn(existing);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginDTO("login-code", true))).getHttpStatus());
        verify(accounts, never()).authenticate(anyString(), anyBoolean());
    }

    private static SysUser enabledUser() {
        SysUser user = new SysUser();
        user.setId(10L);
        user.setStatus(1);
        user.setWechatOpenid("server-openid");
        return user;
    }
}
