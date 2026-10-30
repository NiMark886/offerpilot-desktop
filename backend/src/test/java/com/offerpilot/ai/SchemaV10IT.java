package com.offerpilot.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV10IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsAiCallLogTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'ai_call_log'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
