package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV17IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsAdditionalWebQuestionBankAndBindsToTracks() {
        Integer newQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE source = '网络整理-2026-10-28' "
                        + "AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(newQuestions).isGreaterThanOrEqualTo(100);

        Integer totalQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(totalQuestions).isGreaterThanOrEqualTo(240);

        Integer javaBackendBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "JOIN question_bank q ON q.id = tq.question_id "
                        + "WHERE t.code = 'java-backend-campus' AND q.source = '网络整理-2026-10-28'",
                Integer.class);
        assertThat(javaBackendBindings).isGreaterThanOrEqualTo(70);

        Integer aiQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = 'Java + AI' "
                        + "AND source = '网络整理-2026-10-28' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(aiQuestions).isGreaterThanOrEqualTo(18);

        Integer aiBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "JOIN question_bank q ON q.id = tq.question_id "
                        + "WHERE t.code = 'java-ai' AND q.source = '网络整理-2026-10-28'",
                Integer.class);
        assertThat(aiBindings).isGreaterThanOrEqualTo(90);

        Integer fullstackQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = 'Java 全栈' "
                        + "AND source = '网络整理-2026-10-28' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(fullstackQuestions).isGreaterThanOrEqualTo(6);
    }
}
