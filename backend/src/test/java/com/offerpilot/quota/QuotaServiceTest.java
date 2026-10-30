package com.offerpilot.quota;

import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuotaServiceTest {

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void doesNotLimitSessionsByDefault() {
        SecurityContextHolder.clearContext();
        User user = createUser();

        for (int i = 0; i < 5; i++) {
            quotaService.checkAndConsumeSession(user.getId());
        }

        assertThat(quotaService.me(user.getId()).plan()).isEqualTo("UNLIMITED");
        assertThat(quotaService.me(user.getId()).remainingSessions()).isEqualTo(-1);
    }

    private User createUser() {
        User user = new User();
        user.setEmail("quota-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Quota Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user;
    }
}
