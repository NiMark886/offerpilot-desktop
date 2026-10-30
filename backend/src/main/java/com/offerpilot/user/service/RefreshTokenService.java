package com.offerpilot.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.RefreshToken;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.RefreshTokenMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenMapper refreshTokenMapper, JwtService jwtService) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.jwtService = jwtService;
    }

    public String issue(User user) {
        String jti = UUID.randomUUID().toString();
        String token = jwtService.createRefreshToken(user, jti);

        RefreshToken record = new RefreshToken();
        record.setUserId(user.getId());
        record.setJti(jti);
        record.setTokenHash(sha256(token));
        record.setExpiresAt(LocalDateTime.now().plus(jwtService.refreshTokenTtl()));
        record.setRevoked(0);
        refreshTokenMapper.insert(record);

        return token;
    }

    public Long verifyAndRevoke(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        if (!jwtService.isType(claims, "refresh")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌类型不正确");
        }

        String jti = claims.getId();
        RefreshToken record = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getJti, jti));

        if (record == null
                || record.getRevoked() == null
                || record.getRevoked() == 1
                || record.getExpiresAt() == null
                || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        String actualHash = sha256(token);
        if (!MessageDigest.isEqual(
                record.getTokenHash().getBytes(StandardCharsets.UTF_8),
                actualHash.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        record.setRevoked(1);
        refreshTokenMapper.updateById(record);

        return record.getUserId();
    }

    public void revoke(String token) {
        try {
            verifyAndRevoke(token);
        } catch (RuntimeException ignored) {
            // 注销是幂等操作：令牌无效时也返回成功，避免泄露令牌状态
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
