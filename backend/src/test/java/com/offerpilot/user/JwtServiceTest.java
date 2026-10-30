package com.offerpilot.user;

import com.offerpilot.common.security.JwtService;
import com.offerpilot.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void createsAndParsesAccessToken() {
        User user = new User();
        user.setId(1L);
        user.setRole("USER");
        user.setNickname("Tester");

        String token = jwtService.createAccessToken(user);
        Claims claims = jwtService.parse(token);

        assertThat(jwtService.isType(claims, "access")).isTrue();
        assertThat(jwtService.userId(claims)).isEqualTo(1L);
        assertThat(jwtService.role(claims)).isEqualTo("USER");
    }

    @Test
    void rejectsTamperedToken() {
        User user = new User();
        user.setId(1L);
        user.setRole("USER");
        user.setNickname("Tester");

        String token = jwtService.createAccessToken(user);

        assertThatThrownBy(() -> jwtService.parse(token + "x"))
                .isInstanceOf(JwtException.class);
    }
}
