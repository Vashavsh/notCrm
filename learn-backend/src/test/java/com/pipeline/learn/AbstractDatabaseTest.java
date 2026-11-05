package com.pipeline.learn;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL 18 container (production runs 18) shared by every integration test of the run. */
public abstract class AbstractDatabaseTest {

    public static final PostgreSQLContainer<?> POSTGRES;
    public static final java.nio.file.Path SECRETS;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("pipeline_learn_test")
                .withUsername("test_user")
                .withPassword("test_pass");
        POSTGRES.start();
        // Stands in for the CRM's database role, so the bridge grants of V5 can be checked.
        try (var connection = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("create role crm_probe login password 'probe-pass'");
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
        try {
            SECRETS = java.nio.file.Files.createTempDirectory("learn-secrets");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.crm_role", () -> "crm_probe");
    }
}
