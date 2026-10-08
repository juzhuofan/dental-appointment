package com.dental.auth.service;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.config.WechatProperties;
import com.dental.user.entity.SysUser;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 微信调用发生在数据库事务之外，首次开户需要用户主动确认。 */
@Service
public class WechatAuthService {

    private final WechatApiClient apiClient;
    private final WechatAccountService accountService;
    private final WechatProperties properties;

    public WechatAuthService(WechatApiClient apiClient, WechatAccountService accountService,
            WechatProperties properties) {
        this.apiClient = apiClient;
        this.accountService = accountService;
        this.properties = properties;
    }

    public LoginVO login(WechatLoginDTO request) {
        String openid = apiClient.exchangeLoginCode(request.loginCode());
        SysUser existing = accountService.findByOpenid(openid);
        if (existing != null && !Objects.equals(existing.getStatus(), 1)) {
            throw BusinessException.forbidden("微信账号已停用");
        }
        boolean register = Boolean.TRUE.equals(request.register());
        if (existing == null && !register) {
            throw WechatAccountService.registrationRequired();
        }
        String trustedPhone = null;
        if (properties.isPhoneNumberEnabled()
                && (existing == null || !StringUtils.hasText(existing.getPhone()))) {
            if (!StringUtils.hasText(request.phoneCode())) {
                throw BusinessException.conflict("WECHAT_PHONE_REQUIRED", "请授权微信手机号后登录");
            }
            trustedPhone = apiClient.exchangePhoneCode(request.phoneCode());
        }
        return accountService.authenticate(openid, trustedPhone, register);
    }
}
