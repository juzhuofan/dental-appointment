package com.dental.security;

import com.dental.common.DataValues;
import com.dental.common.R;
import com.dental.persistence.BusinessMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.Map;

@Component
public class TokenFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final BusinessMapper mapper;
    private final ObjectMapper json;

    public TokenFilter(JwtService jwt, BusinessMapper mapper, ObjectMapper json) {
        this.jwt = jwt;
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.startsWith("Bearer ")) {
                error(response, 401, "UNAUTHENTICATED", "登录凭证无效");
                return;
            }
            try {
                var claims = jwt.parse(header.substring(7));
                long userId = Long.parseLong(claims.getSubject());
                var sessions =
                        mapper.list(
                                "auth_token",
                                DataValues.fields("tokenId", claims.getId()),
                                null,
                                false,
                                0,
                                1);
                Map<String, Object> user = mapper.find("sys_user", userId, false);
                if (sessions.isEmpty()
                        || user == null
                        || DataValues.integer(user.get("status")) != 1) {
                    error(response, 401, "UNAUTHENTICATED", "登录已失效，请重新登录");
                    return;
                }
                var session = sessions.get(0);
                if (DataValues.number(session.get("userId")) != userId
                        || DataValues.integer(session.get("revoked")) != 0
                        || !DataValues.time(session.get("expiresAt")).isAfter(DataValues.now())) {
                    error(response, 401, "UNAUTHENTICATED", "登录已失效，请重新登录");
                    return;
                }
                var roles = mapper.roles(userId);
                if (roles.isEmpty()) {
                    error(response, 401, "UNAUTHENTICATED", "账号没有可用角色");
                    return;
                }
                var principal =
                        new CurrentUser(
                                userId,
                                (String) user.get("username"),
                                (String) user.get("displayName"),
                                roles,
                                mapper.doctorId(userId),
                                claims.getId());
                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                roles.stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                        .toList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                error(response, 401, "UNAUTHENTICATED", "登录凭证无效或已过期");
                return;
            } catch (DataAccessException exception) {
                error(response, 503, "SERVICE_UNAVAILABLE", "认证服务暂时不可用");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void error(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(), R.error(code, message));
    }
}
