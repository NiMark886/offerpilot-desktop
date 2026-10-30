package com.offerpilot.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.exception.ErrorCode;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import com.offerpilot.user.web.dto.LoginRequest;
import com.offerpilot.user.web.dto.RefreshRequest;
import com.offerpilot.user.web.dto.RegisterRequest;
import com.offerpilot.user.web.dto.TokenResponse;
import com.offerpilot.user.web.dto.UserView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserMapper userMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Long existing = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setRole("USER");
        user.setStatus("ACTIVE");

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "邮箱已被注册");
        }

        return issueTokens(user);
    }

    public TokenResponse login(LoginRequest request) {
        String account = request.account().trim();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, account.toLowerCase(Locale.ROOT))
                .or()
                .eq(User::getPhone, account)
                .last("LIMIT 1"));

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenService.verifyAndRevoke(request.refreshToken());
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }
        return issueTokens(user);
    }

    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    public UserView me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return UserView.from(user);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new TokenResponse(
                accessToken,
                refreshToken,
                jwtService.accessTokenTtl().toSeconds(),
                "Bearer",
                UserView.from(user));
    }
}
