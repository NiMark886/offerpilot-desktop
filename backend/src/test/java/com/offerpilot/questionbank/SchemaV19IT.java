package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV19IT {

    private static final String SOURCE = "网络整理-2026-10-28-黑马面试题";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsHeimaJavaInterviewQuestionsAndBindsTracks() {
        Integer newQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE source = ? AND status = 'ACTIVE' AND deleted = 0",
                Integer.class, SOURCE);
        assertThat(newQuestions).isEqualTo(100);

        Integer totalQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(totalQuestions).isGreaterThanOrEqualTo(400);

        Integer algorithmQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = '基础算法' "
                        + "AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(algorithmQuestions).isGreaterThanOrEqualTo(17);

        Integer componentQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = '常用组件' "
                        + "AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(componentQuestions).isGreaterThanOrEqualTo(19);

        Integer webQuestions = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank WHERE category = 'Web 基础' "
                        + "AND status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(webQuestions).isGreaterThanOrEqualTo(7);

        assertThat(bindingCount("java-backend-campus")).isGreaterThanOrEqualTo(100);
        assertThat(bindingCount("java-fullstack-junior")).isGreaterThanOrEqualTo(100);
        assertThat(bindingCount("java-ai")).isGreaterThanOrEqualTo(100);
        assertThat(bindingCount("java-microservices")).isGreaterThanOrEqualTo(70);
    }

    private Integer bindingCount(String trackCode) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "JOIN question_bank q ON q.id = tq.question_id "
                        + "WHERE t.code = ? AND q.source = ?",
                Integer.class, trackCode, SOURCE);
    }
}
