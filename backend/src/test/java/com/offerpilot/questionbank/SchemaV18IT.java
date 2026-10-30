package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV18IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsFrontendQuestionBankAndActivatesTrack() {
        Integer newQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE source = '网络整理-2026-10-28-前端' "
                        + "AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(newQuestions).isGreaterThanOrEqualTo(60);

        Integer totalQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(totalQuestions).isGreaterThanOrEqualTo(300);

        Integer frontendBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "WHERE t.code = 'frontend'",
                Integer.class);
        assertThat(frontendBindings).isGreaterThanOrEqualTo(67);

        String frontendStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM interview_track WHERE code = 'frontend'",
                String.class);
        assertThat(frontendStatus).isEqualTo("ACTIVE");

        Integer activeFrontendJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_template WHERE title = '前端' AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(activeFrontendJobs).isEqualTo(1);

        Integer fullstackFrontendBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "JOIN question_bank q ON q.id = tq.question_id "
                        + "WHERE t.code = 'java-fullstack-junior' "
                        + "AND q.source = '网络整理-2026-10-28-前端'",
                Integer.class);
        assertThat(fullstackFrontendBindings).isGreaterThanOrEqualTo(60);
    }
}
