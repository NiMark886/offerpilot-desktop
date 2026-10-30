package com.offerpilot.interview;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV11IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsCompositeQueryIndexes() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE LOWER(index_name) IN (
                    'idx_interview_session_user_created',
                    'idx_interview_question_session_status_seq',
                    'idx_interview_message_session_id_id'
                )
                """,
                Integer.class);
        assertThat(count).isEqualTo(3);
    }
}
