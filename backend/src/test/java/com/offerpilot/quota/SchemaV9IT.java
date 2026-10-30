package com.offerpilot.quota;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SchemaV9IT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsQuotaTableAndAdminUser() {
        Integer quotaTable = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = 'user_quota'",
                Integer.class);
        Integer admin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE email = 'admin@offerpilot.local' AND role = 'ADMIN'",
                Integer.class);
        assertThat(quotaTable).isEqualTo(1);
        assertThat(admin).isEqualTo(1);
    }
}
