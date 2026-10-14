package com.dental.auth.service;

import com.dental.auth.dto.WechatLoginDTO;
import com.dental.auth.vo.LoginVO;
import com.dental.common.BusinessException;
import com.dental.user.entity.SysUser;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 微信调用发生在数据库事务之外，首次开户需要用户主动确认。 */
@Service
public class WechatAuthService {

    private final WechatApiClient apiClient;
    private final WechatAccountService accountService;

    public WechatAuthService(WechatApiClient apiClient, WechatAccountService accountService) {
        this.apiClient = apiClient;
        this.accountService = accountService;
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
        return accountService.authenticate(openid, register);
    }
}
