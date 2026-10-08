package com.dental.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** 仅签发和解析短期访问令牌，撤销状态始终以数据库为准。 */
@Service
public class JwtService {
    private static final String ISSUER = "dental-appointment";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET 必须至少为32字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String issue(Long userId, String tokenId, Instant expiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId)
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String compactToken) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(compactToken)
                .getPayload();
    }
}
