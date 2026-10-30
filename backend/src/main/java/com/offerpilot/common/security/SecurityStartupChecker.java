package com.offerpilot.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SecurityStartupChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SecurityStartupChecker.class);
    private static final String DEFAULT_ADMIN_EMAIL = "admin@offerpilot.local";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public SecurityStartupChecker(UserMapper userMapper,
                                  PasswordEncoder passwordEncoder,
                                  Environment environment) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isDevProfile()) {
            return;
        }
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, DEFAULT_ADMIN_EMAIL)
                .last("LIMIT 1"));
        if (admin != null && passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash())) {
            log.warn("Default admin credentials are still active ({}). "
                    + "Change the password or disable this account before production.", DEFAULT_ADMIN_EMAIL);
        }
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "local".equals(profile) || "test".equals(profile));
    }
}
