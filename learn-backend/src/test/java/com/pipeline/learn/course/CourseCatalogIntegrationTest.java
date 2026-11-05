package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pipeline.learn.AbstractIntegrationTest;
import com.pipeline.learn.TestBrowser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CourseCatalogIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "lamp-river-tiger-47";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from account");
        jdbc.update("delete from spring_session");
    }

    private UUID createAccount(String login) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash, status) values (?, ?, ?, ?, ?, 'ACTIVE')",
                id, UUID.randomUUID(), login, "Анна Тестова", encoder.encode(PASSWORD));
        return id;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> modules(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("modules");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lessonsOf(Map<String, Object> module) {
        return (List<Map<String, Object>>) module.get("lessons");
    }

    @Test
    void courseNeedsLogin() {
        assertThat(new TestBrowser(http).get("/api/course").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aNewStudentSeesTheWholeCourseAsAvailable() {
        createAccount("catalog_new");
        var response = new TestBrowser(http).loginAs("catalog_new", PASSWORD).get("/api/course");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var modules = modules(response.getBody());
        assertThat(modules).hasSize(7);
        assertThat(modules.get(0)).containsEntry("id", "block-1").containsEntry("stage", 1).containsEntry("available", true);
        assertThat(modules.get(2)).containsEntry("id", "block-3").containsEntry("available", false);
        var first = lessonsOf(modules.get(0)).get(0);
        assertThat(first).containsEntry("id", "lesson-01").containsEntry("status", "available")
                .containsEntry("answered", 0).containsEntry("total", 6).containsEntry("minutes", 10);
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) response.getBody().get("progress");
        assertThat(progress).containsEntry("completed", 0).containsEntry("total", 2).containsEntry("resumeLessonId", "lesson-01");
    }

    @Test
    void statusesCountsAndResumePointFollowTheStudentsProgress() {
        UUID id = createAccount("catalog_progress");
        // lesson-01: finished, 2 answers. lesson-02: opened later, 1 answer, not finished.
        long p1 = insertProgress(id, "lesson-01", "2026-11-01T10:00:00Z", "2026-11-01T10:30:00Z");
        long a1 = insertAttempt(p1, "{\"t01-q1\": {\"submitted\": true, \"value\": 1}, \"t01-q2\": {\"submitted\": true, \"value\": [0, 2]}, \"unknown-q\": {\"submitted\": true}}");
        jdbc.update("update lesson_progress set current_attempt_id = ? where id = ?", a1, p1);
        long p2 = insertProgress(id, "lesson-02", "2026-11-02T10:00:00Z", null);
        long a2 = insertAttempt(p2, "{\"t02-q1\": {\"submitted\": false}}");
        jdbc.update("update lesson_progress set current_attempt_id = ? where id = ?", a2, p2);

        var body = new TestBrowser(http).loginAs("catalog_progress", PASSWORD).get("/api/course").getBody();

        var first = lessonsOf(modules(body).get(0)).get(0);
        var second = lessonsOf(modules(body).get(1)).get(0);
        // The answer to a question that no longer exists is not counted.
        assertThat(first).containsEntry("status", "completed").containsEntry("answered", 2);
        assertThat(second).containsEntry("status", "in_progress").containsEntry("answered", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> progress = (Map<String, Object>) body.get("progress");
        assertThat(progress).containsEntry("completed", 1).containsEntry("total", 2).containsEntry("resumeLessonId", "lesson-02");
    }

    @Test
    void anotherStudentsProgressIsNotVisible() {
        UUID other = createAccount("catalog_other");
        createAccount("catalog_me");
        long p = insertProgress(other, "lesson-01", "2026-11-01T10:00:00Z", "2026-11-01T10:30:00Z");
        long a = insertAttempt(p, "{}");
        jdbc.update("update lesson_progress set current_attempt_id = ? where id = ?", a, p);

        var body = new TestBrowser(http).loginAs("catalog_me", PASSWORD).get("/api/course").getBody();
        assertThat(lessonsOf(modules(body).get(0)).get(0)).containsEntry("status", "available");
    }

    private long insertProgress(UUID account, String lesson, String opened, String completed) {
        return jdbc.queryForObject("insert into lesson_progress (account_id, lesson_id, opened_at, last_opened_at, completed_at) "
                + "values (?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz) returning id", Long.class,
                account, lesson, opened, opened, completed);
    }

    private long insertAttempt(long progress, String answersJson) {
        return jdbc.queryForObject("insert into attempt (progress_id, answers) values (?, ?::jsonb) returning id", Long.class,
                progress, answersJson);
    }
}
