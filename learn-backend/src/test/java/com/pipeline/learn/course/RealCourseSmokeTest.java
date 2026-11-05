package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.pipeline.learn.AbstractDatabaseTest;
import com.pipeline.learn.TestBrowser;

/**
 * The whole app against the real (private) course: every lesson opens, no answer leaks, every picture the lessons use is
 * served, and, when LEARN_REAL_SECRETS_DIR is set, every Amplitude login is served. Skipped unless LEARN_REAL_CONTENT_DIR is set.
 */
@EnabledIfEnvironmentVariable(named = "LEARN_REAL_CONTENT_DIR", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealCourseSmokeTest extends AbstractDatabaseTest {

    @DynamicPropertySource
    static void realCourse(DynamicPropertyRegistry registry) {
        registry.add("learn.content-dir", () -> System.getenv("LEARN_REAL_CONTENT_DIR"));
        if (System.getenv("LEARN_REAL_SECRETS_DIR") != null) {
            registry.add("learn.secrets-dir", () -> System.getenv("LEARN_REAL_SECRETS_DIR"));
        }
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    CourseContent content;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void everyRealLessonOpensWithoutLeakingAnswersAndEveryPictureIsServed() {
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash, status) values (?, ?, 'smoke_user', 'Мария Иванова', ?, 'ACTIVE')",
                UUID.randomUUID(), UUID.randomUUID(), encoder.encode("lamp-river-tiger-47"));
        TestBrowser browser = new TestBrowser(http).loginAs("smoke_user", "lamp-river-tiger-47");

        var course = browser.get("/api/course").getBody();
        @SuppressWarnings("unchecked")
        var progress = (Map<String, Object>) course.get("progress");
        assertThat(progress).containsEntry("total", 38).containsEntry("completed", 0);

        int questions = 0;
        Set<String> pictures = new HashSet<>();
        Pattern picture = Pattern.compile("/assets/course/[^\\s\"'<>\\\\]+");
        for (CourseContent.Lesson lesson : content.lessons()) {
            var response = browser.get("/api/lessons/" + lesson.id());
            assertThat(response.getStatusCode()).as(lesson.id()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            var shown = (Map<String, Object>) response.getBody().get("lesson");
            for (Map<String, Object> section : list(shown.get("sections"))) {
                if ("question".equals(section.get("type"))) {
                    questions++;
                    assertThat(section).as(lesson.id() + "/" + section.get("id"))
                            .doesNotContainKeys("answer", "correct", "tolerance", "feedback", "wrongFeedback");
                }
                for (String key : List.of("html", "prompt")) {
                    if (section.get(key) instanceof String text) {
                        Matcher m = picture.matcher(text);
                        while (m.find()) {
                            pictures.add(m.group());
                        }
                        // The placeholder name is replaced everywhere (as a whole word).
                        assertThat(text).as(lesson.id()).doesNotContainPattern("(?<![А-Яа-яЁё])Анон(?![А-Яа-яЁё])");
                    }
                }
            }
        }
        assertThat(questions).isEqualTo(456);

        // Pictures shown in the lessons themselves; the rest appear in explanations that are revealed after answering.
        assertThat(pictures).hasSizeGreaterThan(100);
        List<String> failed = new ArrayList<>();
        List<String> everyPicture = new ArrayList<>();
        try (var files = java.nio.file.Files.list(content.imagesDir())) {
            files.forEach(f -> everyPicture.add("/assets/course/" + f.getFileName()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        assertThat(everyPicture).hasSize(216);
        for (String reference : everyPicture) {
            var image = browser.getBytes(reference);
            if (image.getStatusCode() != HttpStatus.OK || image.getBody() == null || image.getBody().length == 0) {
                failed.add(reference);
            }
        }
        assertThat(failed).isEmpty();

        if (System.getenv("LEARN_REAL_SECRETS_DIR") != null) {
            for (Map<String, Object> resource : content.resources()) {
                var login = browser.get("/api/resources/" + resource.get("id"));
                assertThat(login.getStatusCode()).as("resource %s", resource.get("id")).isEqualTo(HttpStatus.OK);
                assertThat(login.getBody()).containsKeys("login", "password", "organization");
            }
        }
    }
}
