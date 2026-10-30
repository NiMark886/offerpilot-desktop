package com.offerpilot.user;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import com.offerpilot.user.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void issuesAndRevokesRefreshToken() {
        User user = createUser(userMapper);

        String token = refreshTokenService.issue(user);
        assertThat(jwtService.isType(jwtService.parse(token), "refresh")).isTrue();

        Long userId = refreshTokenService.verifyAndRevoke(token);
        assertThat(userId).isEqualTo(user.getId());

        assertThatThrownBy(() -> refreshTokenService.verifyAndRevoke(token))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("刷新令牌");
    }

    private User createUser(UserMapper mapper) {
        User user = new User();
        user.setEmail("refresh-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Refresh Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        mapper.insert(user);
        return user;
    }
}
