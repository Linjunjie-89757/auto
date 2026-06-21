package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExecutionSuiteMigrationTests extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationCreatesExecutionSuiteTables() {
        assertThat(existingTables()).contains(
                "tb_api_execution_suite_module",
                "tb_api_execution_suite",
                "tb_api_execution_suite_item",
                "tb_api_execution_suite_run_history"
        );
    }

    private List<String> existingTables() {
        return jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);
    }
}
