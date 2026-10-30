package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV8IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsQuestionBank() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE source = '个人整理' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(count).isEqualTo(71);
    }
}
