package com.offerpilot.interview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV4IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsConversationTableAndColumns() {
        assertThat(tableCount("interview_message")).isEqualTo(1);
        assertThat(columnCount("interview_question", "follow_up_count")).isEqualTo(1);
        assertThat(columnCount("interview_question", "hint_count")).isEqualTo(1);
        assertThat(columnCount("interview_question", "asked_at")).isEqualTo(1);
        assertThat(columnCount("interview_question", "answered_at")).isEqualTo(1);
        assertThat(columnCount("interview_session", "current_question_id")).isEqualTo(1);
    }

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
                Integer.class, table);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name) = ? AND LOWER(column_name) = ?",
                Integer.class, table, column);
    }
}
