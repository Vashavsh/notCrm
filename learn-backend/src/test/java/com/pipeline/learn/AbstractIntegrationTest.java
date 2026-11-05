package com.pipeline.learn;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Integration tests that run against the small fictional course in {@code src/test/resources/content-fixture}. */
public abstract class AbstractIntegrationTest extends AbstractDatabaseTest {

    @DynamicPropertySource
    static void fixtureCourse(DynamicPropertyRegistry registry) {
        // A fictional course, so tests never need the real (private) material.
        registry.add("learn.content-dir", () -> new java.io.File("src/test/resources/content-fixture").getAbsolutePath());
        registry.add("learn.secrets-dir", () -> SECRETS.toString());
    }
}
