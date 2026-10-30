package com.offerpilot.common.security;

import com.offerpilot.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-ttl}") Duration accessTokenTtl,
                      @Value("${app.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String createAccessToken(User user) {
        return createToken(user, "access", accessTokenTtl, UUID.randomUUID().toString());
    }

    public String createRefreshToken(User user, String jti) {
        return createToken(user, "refresh", refreshTokenTtl, jti);
    }

    private String createToken(User user, String type, Duration ttl, String jti) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim("type", type)
                .claim("role", user.getRole())
                .claim("nickname", user.getNickname())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isType(Claims claims, String type) {
        return type.equals(claims.get("type", String.class));
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String role(Claims claims) {
        return claims.get("role", String.class);
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }
}
