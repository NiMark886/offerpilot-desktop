package com.offerpilot.resume;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV3IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsW2Tables() {
        assertThat(tableCount("resume")).isEqualTo(1);
        assertThat(tableCount("interview_session")).isEqualTo(1);
        assertThat(tableCount("interview_question")).isEqualTo(1);
    }

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table);
    }
}
