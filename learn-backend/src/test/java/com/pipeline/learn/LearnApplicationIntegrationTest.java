package com.pipeline.learn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LearnApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthIsOpen() {
        var response = http.getForEntity("/api/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"ok\"");
    }

    @Test
    void everythingElseRequiresLogin() {
        assertThat(http.getForEntity("/api/course", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void migrationsLiveInTheLearnSchemaOnly() {
        var tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'learn'", String.class);
        assertThat(tables).contains("account", "flyway_schema_history");

        // Nothing of ours may leak into the CRM's schema.
        Integer inPublic = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public'", Integer.class);
        assertThat(inPublic).isZero();
    }

    @Test
    void loginIsUniqueRegardlessOfCase() {
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash) values (gen_random_uuid(), gen_random_uuid(), 'Ivan_Petrov', 'Ivan', 'x')");
        try {
            jdbc.update("insert into account (id, student_id, login, full_name, password_hash) values (gen_random_uuid(), gen_random_uuid(), 'ivan_petrov', 'Other', 'x')");
            throw new AssertionError("duplicate login in another case must be rejected");
        } catch (org.springframework.dao.DuplicateKeyException expected) {
            // ok
        } finally {
            jdbc.update("delete from account");
        }
    }
}
