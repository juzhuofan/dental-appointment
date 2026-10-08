package com.dental.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.auth.entity.AuthToken;
import com.dental.auth.mapper.AuthTokenMapper;
import com.dental.common.R;
import com.dental.user.entity.SysUser;
import com.dental.user.mapper.SysUserMapper;
import com.dental.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/** Bearer 认证：校验 JWT、数据库令牌状态、账号及实时角色。 */
@Component
public class TokenFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int ACCOUNT_ENABLED = 1;
    private static final int TOKEN_ACTIVE = 0;

    private final JwtService jwtService;
    private final AuthTokenMapper tokenMapper;
    private final SysUserMapper userMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public TokenFilter(
            JwtService jwtService,
            AuthTokenMapper tokenMapper,
            SysUserMapper userMapper,
            UserService userService,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.tokenMapper = tokenMapper;
        this.userMapper = userMapper;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)
                || authorization.length() == BEARER_PREFIX.length()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录凭证无效");
            return;
        }
        try {
            Claims claims = jwtService.parse(authorization.substring(BEARER_PREFIX.length()));
            Long userId = Long.valueOf(claims.getSubject());
            AuthToken session = tokenMapper.selectOne(new LambdaQueryWrapper<AuthToken>()
                    .eq(AuthToken::getTokenId, claims.getId()));
            SysUser user = userMapper.selectById(userId);
            if (session == null || user == null || !Objects.equals(session.getUserId(), userId)
                    || !Objects.equals(session.getRevoked(), TOKEN_ACTIVE)
                    || !Objects.equals(user.getStatus(), ACCOUNT_ENABLED)
                    || !session.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录");
                return;
            }
            List<String> roles = userService.rolesOf(userId);
            if (roles.isEmpty()) {
                reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "账号没有可用角色");
                return;
            }
            CurrentUser principal = new CurrentUser(userId, user.getUsername(), user.getDisplayName(),
                    roles, userMapper.findActiveDoctorId(userId), claims.getId());
            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (JwtException | IllegalArgumentException exception) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "登录凭证无效或已过期");
            return;
        } catch (DataAccessException exception) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "认证服务暂时不可用");
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletResponse response, int httpStatus, String code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), R.error(code, message));
    }
}
