package com.offerpilot.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV14IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsTracksAndBindsQuestions() {
        Integer activeTracks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_track WHERE status = 'ACTIVE' AND deleted = 0",
                Integer.class);
        assertThat(activeTracks).isEqualTo(5);

        Integer totalTracks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_track WHERE deleted = 0",
                Integer.class);
        assertThat(totalTracks).isEqualTo(7);

        Integer trackedJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_template WHERE track_id IS NOT NULL AND deleted = 0",
                Integer.class);
        assertThat(trackedJobs).isEqualTo(6);

        Integer javaFullstackJobs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM job_template WHERE title = 'Java 全栈' AND deleted = 0",
                Integer.class);
        assertThat(javaFullstackJobs).isEqualTo(1);

        Integer javaBackendBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "WHERE t.code = 'java-backend-campus'",
                Integer.class);
        assertThat(javaBackendBindings).isGreaterThanOrEqualTo(100);

        Integer frontendBindings = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM track_question tq "
                        + "JOIN interview_track t ON t.id = tq.track_id "
                        + "WHERE t.code = 'frontend'",
                Integer.class);
        assertThat(frontendBindings).isGreaterThanOrEqualTo(7);
    }
}
