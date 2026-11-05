package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Checks the real (private) course material against the loader's rules. Skipped everywhere except where
 * LEARN_REAL_CONTENT_DIR points at a content directory, e.g. on the developer's machine:
 * {@code LEARN_REAL_CONTENT_DIR=../../notCrm-local-data/learn-content ./mvnw test -Dtest=RealCourseContentTest}.
 */
@EnabledIfEnvironmentVariable(named = "LEARN_REAL_CONTENT_DIR", matches = ".+")
class RealCourseContentTest {

    @Test
    void theRealCourseIsValidAndComplete() {
        CourseContent content = new CourseContent(
                new LearnProperties(System.getenv("LEARN_REAL_CONTENT_DIR"), null), new ObjectMapper());

        assertThat(content.lessons()).hasSize(38);
        long questions = content.lessons().stream().mapToLong(l -> l.questions().size()).sum();
        assertThat(questions).isEqualTo(456);
        assertThat(content.resources()).isNotEmpty();
    }
}
