package com.dental.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
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
    private WechatProperties properties;
    private WechatAuthService service;
    private final LoginVO login = new LoginVO("business-token", null);

    @BeforeEach
    void setUp() {
        properties = new WechatProperties();
        properties.setEnabled(true);
        service = new WechatAuthService(apiClient, accounts, properties);
        when(apiClient.exchangeLoginCode("login-code")).thenReturn("server-openid");
    }

    @Test
    void firstSilentLoginDoesNotCreateAccount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginDTO("login-code", null, null)));
        assertEquals("WECHAT_REGISTRATION_REQUIRED", exception.getCode());
        verify(accounts, never()).authenticate(anyString(), any(), anyBoolean());
        verify(apiClient, never()).exchangePhoneCode(any());
    }

    @Test
    void personalAccountRegistersOnlyAfterUserConfirmationWithoutInventedPhone() {
        when(accounts.authenticate("server-openid", null, true)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", null, true)));
        verify(accounts).authenticate(eq("server-openid"), isNull(), eq(true));
        verify(apiClient, never()).exchangePhoneCode(any());
    }

    @Test
    void laterPersonalLoginUsesServerVerifiedIdentityWithoutNewPhoneAuthorization() {
        SysUser existing = enabledUser(null);
        when(accounts.findByOpenid("server-openid")).thenReturn(existing);
        when(accounts.authenticate("server-openid", null, false)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", null, false)));
        verify(apiClient, never()).exchangePhoneCode(any());
    }

    @Test
    void phoneEnabledRegistrationRequiresSeparatePhoneCode() {
        properties.setPhoneNumberEnabled(true);
        assertEquals("WECHAT_PHONE_REQUIRED", assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginDTO("login-code", null, true))).getCode());
        verify(accounts, never()).authenticate(anyString(), any(), anyBoolean());
    }

    @Test
    void storesOnlyWechatReturnedPhoneInsteadOfClientClaim() {
        properties.setPhoneNumberEnabled(true);
        when(apiClient.exchangePhoneCode("phone-code")).thenReturn("13812345678");
        when(accounts.authenticate("server-openid", "13812345678", true)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", "phone-code", true)));
        verify(accounts).authenticate("server-openid", "13812345678", true);
    }

    @Test
    void knownVerifiedPhoneDoesNotConsumeUnneededAuthorizationCode() {
        properties.setPhoneNumberEnabled(true);
        when(accounts.findByOpenid("server-openid")).thenReturn(enabledUser("13812345678"));
        when(accounts.authenticate("server-openid", null, false)).thenReturn(login);
        assertSame(login, service.login(new WechatLoginDTO("login-code", "unused-code", false)));
        verify(apiClient, never()).exchangePhoneCode(any());
    }

    @Test
    void rejectsDisabledUserBeforePhoneAuthorization() {
        SysUser existing = enabledUser(null);
        existing.setStatus(0);
        when(accounts.findByOpenid("server-openid")).thenReturn(existing);
        assertEquals(403, assertThrows(BusinessException.class,
                () -> service.login(new WechatLoginDTO("login-code", "phone-code", true))).getHttpStatus());
        verify(accounts, never()).authenticate(anyString(), any(), anyBoolean());
        verify(apiClient, never()).exchangePhoneCode(any());
    }

    private static SysUser enabledUser(String phone) {
        SysUser user = new SysUser();
        user.setId(10L);
        user.setStatus(1);
        user.setPhone(phone);
        user.setWechatOpenid("server-openid");
        return user;
    }
}
