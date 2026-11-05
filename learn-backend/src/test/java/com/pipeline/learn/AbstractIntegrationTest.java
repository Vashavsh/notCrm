package com.pipeline.learn;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final java.nio.file.Path SECRETS;

    static {
        // Production runs PostgreSQL 18.
        POSTGRES = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("pipeline_learn_test")
                .withUsername("test_user")
                .withPassword("test_pass");
        POSTGRES.start();
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
        // A small fictional course, so tests never need the real (private) material.
        registry.add("learn.content-dir", () -> new java.io.File("src/test/resources/content-fixture").getAbsolutePath());
        registry.add("learn.secrets-dir", () -> SECRETS.toString());
    }
}
