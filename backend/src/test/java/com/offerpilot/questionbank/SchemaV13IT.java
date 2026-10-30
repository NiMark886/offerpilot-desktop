package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV13IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsJavaQuestionBankExpansion() {
        Integer javaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE tags LIKE '%Java%' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(javaCount).isGreaterThanOrEqualTo(60);

        Integer concurrencyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = 'Java 并发' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(concurrencyCount).isGreaterThanOrEqualTo(20);

        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(total).isGreaterThanOrEqualTo(140);
    }
}
