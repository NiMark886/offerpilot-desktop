package com.offerpilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsTablesAndSeedsJobTemplates() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_template", Integer.class);
        assertThat(count).isEqualTo(6);
        Integer trackCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interview_track", Integer.class);
        assertThat(trackCount).isEqualTo(7);
    }
}
