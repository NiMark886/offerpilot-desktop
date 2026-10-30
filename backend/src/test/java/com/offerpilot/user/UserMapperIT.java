package com.offerpilot.user;

import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserMapperIT {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertsUserAndFillsAuditFields() {
        User user = new User();
        user.setEmail("mapper-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Mapper Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void logicDeleteHidesUser() {
        User user = new User();
        user.setEmail("delete-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Delete Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        userMapper.deleteById(user.getId());

        assertThat(userMapper.selectById(user.getId())).isNull();
    }
}
