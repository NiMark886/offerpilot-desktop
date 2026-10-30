package com.offerpilot.interview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV6IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void addsEvaluationPromptContext() {
        assertThat(columnCount("answer_evaluation", "prompt_text")).isEqualTo(1);
        assertThat(columnCount("answer_evaluation", "prompt_type")).isEqualTo(1);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                Integer.class, table, column);
    }
}
