package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pipeline.learn.AbstractIntegrationTest;
import com.pipeline.learn.TestBrowser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LessonFlowIntegrationTest extends AbstractIntegrationTest {

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

    private void createAccount(String login) {
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash, status) values (?, ?, ?, ?, ?, 'ACTIVE')",
                UUID.randomUUID(), UUID.randomUUID(), login, "Анна Тестова", encoder.encode(PASSWORD));
    }

    private TestBrowser student(String login) {
        createAccount(login);
        return new TestBrowser(http).loginAs(login, PASSWORD);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> body(Object attemptId, String questionId, Object value) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("attemptId", attemptId);
        body.put("questionId", questionId);
        body.put("value", value);
        return body;
    }

    private static Object attemptId(ResponseEntity<Map<String, Object>> detail) {
        return map(detail.getBody().get("attempt")).get("id");
    }

    private static Map<String, Object> answers(ResponseEntity<Map<String, Object>> detail) {
        return map(map(detail.getBody().get("attempt")).get("answers"));
    }

    // ---- opening a lesson ----

    @Test
    void openingALessonHidesAnswersAndAddressesTheStudentByName() {
        TestBrowser browser = student("flow_open");
        var detail = browser.get("/api/lessons/lesson-01");

        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        var lesson = map(detail.getBody().get("lesson"));
        var sections = list(lesson.get("sections"));
        String html = (String) sections.get(0).get("html");
        assertThat(html).contains("Привет, Анна Тестова!").contains("Анонимный текст не меняется");
        var single = sections.get(1);
        assertThat(single.get("prompt")).isEqualTo("Выберите <b>второй</b> вариант, Анна Тестова.");
        assertThat(single).doesNotContainKeys("correct", "feedback", "wrongFeedback");
        assertThat(sections.get(3)).doesNotContainKeys("answer", "tolerance");
        assertThat(sections.get(4)).containsEntry("requiresSelfAssessment", true);
        assertThat(sections.get(7)).containsEntry("resource", "fixture-data");

        assertThat(map(detail.getBody().get("navigation"))).containsEntry("previous", null).containsEntry("next", "lesson-02");
        assertThat(map(detail.getBody().get("attempt"))).containsEntry("completed", false);
        assertThat(attemptId(detail)).isInstanceOf(Number.class);
    }

    @Test
    void firstAndLastOpenAreRemembered() throws Exception {
        TestBrowser browser = student("flow_visits");
        var first = map(browser.get("/api/lessons/lesson-01").getBody().get("progress"));
        Thread.sleep(20);
        var second = map(browser.get("/api/lessons/lesson-01").getBody().get("progress"));

        assertThat(first.get("openedAt")).isNotNull();
        assertThat(second.get("openedAt")).isEqualTo(first.get("openedAt"));
        assertThat((String) second.get("lastOpenedAt")).isGreaterThan((String) first.get("lastOpenedAt"));
        assertThat(jdbc.queryForObject("select count(*) from lesson_progress", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownLessonAndQuestionAreNotFound() {
        TestBrowser browser = student("flow_404");
        assertThat(browser.get("/api/lessons/nope").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var detail = browser.get("/api/lessons/lesson-01");
        var response = browser.post("/api/lessons/lesson-01/answers", body(attemptId(detail), "t99-q1", 0));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var noQuestion = browser.post("/api/lessons/lesson-01/answers", Map.of("attemptId", attemptId(detail)));
        assertThat(noQuestion.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- answers ----

    @Test
    void singleChoiceIsGradedAndTheFirstAnswerStands() {
        TestBrowser browser = student("flow_single");
        var detail = browser.get("/api/lessons/lesson-01");
        Object attempt = attemptId(detail);

        var wrong = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 0));
        var item = map(answers(wrong).get("t01-q1"));
        assertThat(item).containsEntry("correct", false).containsEntry("submitted", true)
                .containsEntry("feedback", "<p>Подумайте ещё.</p>");
        assertThat(item.get("correctOptions")).isEqualTo(List.of(1));

        // Answering again, even with the right option, changes nothing.
        var again = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 1));
        assertThat(map(answers(again).get("t01-q1"))).containsEntry("correct", false).containsEntry("value", 0);
    }

    @Test
    void rightSingleAnswerGetsPersonalFeedback() {
        TestBrowser browser = student("flow_single_ok");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        var right = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 1));
        assertThat(map(answers(right).get("t01-q1"))).containsEntry("correct", true)
                .containsEntry("feedback", "<p>Верно, Анна Тестова.</p>");
    }

    @Test
    void multiChoiceNeedsTheExactSetAndRejectsNonsense() {
        TestBrowser browser = student("flow_multi");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));

        for (Object bad : new Object[] {List.of(), List.of(0, 0), List.of(0, 9), "0", List.of("0"), List.of(true)}) {
            var response = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q2", bad));
            assertThat(response.getStatusCode()).as("value %s", bad).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).containsEntry("error", "Выберите допустимые варианты ответа.");
        }
        var partial = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q2", List.of(0)));
        assertThat(map(answers(partial).get("t01-q2"))).containsEntry("correct", false);

        TestBrowser other = student("flow_multi2");
        Object attempt2 = attemptId(other.get("/api/lessons/lesson-01"));
        var exact = other.post("/api/lessons/lesson-01/answers", body(attempt2, "t01-q2", List.of(2, 0)));
        assertThat(map(answers(exact).get("t01-q2"))).containsEntry("correct", true);
    }

    @Test
    void numbersAcceptCommasPercentSignsAndTolerance() {
        for (String ok : new String[] {"12,5", " 12.5 ", "12.5%", "12.504", "1 2.5"}) {
            TestBrowser browser = student("flow_num_" + Math.abs(ok.hashCode()));
            Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
            var response = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q3", ok));
            assertThat(map(answers(response).get("t01-q3"))).as("value '%s'", ok).containsEntry("correct", true);
        }
        TestBrowser browser = student("flow_num_wrong");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        var wrong = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q3", 12.4));
        var item = map(answers(wrong).get("t01-q3"));
        assertThat(item).containsEntry("correct", false);
        assertThat((String) item.get("feedback")).startsWith("<p>Правильный ответ — 12.5.</p>").contains("Разбор");
        assertThat(item).containsEntry("correctAnswer", 12.5);
    }

    @Test
    void notANumberIsRejectedWithAMessage() {
        TestBrowser browser = student("flow_num_bad");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        String[][] cases = {{"abc", "Введите число: можно использовать точку или запятую."},
                {"1.2.3", "Введите число: можно использовать точку или запятую."},
                {"inf", "Введите конечное число."}, {"NaN", "Введите конечное число."}, {"0x10", "Введите число: можно использовать точку или запятую."}};
        for (String[] c : cases) {
            var response = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q3", c[0]));
            assertThat(response.getBody()).as("value %s", c[0]).containsEntry("error", c[1]);
        }
        var bool = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q3", true));
        assertThat(bool.getBody()).containsEntry("error", "Введите число.");
    }

    @Test
    void openAnswersAreTrimmedAndNeedText() {
        TestBrowser browser = student("flow_open_text");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        var blank = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q5", "   "));
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blank.getBody()).containsEntry("error", "Напишите ответ или прикрепите график (текст до 30 000 символов).");

        var ok = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q5", "  мой вывод  "));
        assertThat(map(answers(ok).get("t01-q5"))).containsEntry("value", "мой вывод").containsEntry("correct", null)
                .containsEntry("submitted", true);
    }

    @Test
    void selfAssessmentIsOnlyForQuestionsThatAskForItAndOnlyOnce() {
        TestBrowser browser = student("flow_self");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));

        var notAsked = browser.post("/api/lessons/lesson-01/answers",
                Map.of("attemptId", attempt, "questionId", "t01-q5", "value", "текст", "selfAssessment", "correct"));
        assertThat(notAsked.getBody()).containsEntry("error", "Самооценка не требуется для этого задания.");

        var invalid = browser.post("/api/lessons/lesson-01/answers",
                Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "текст", "selfAssessment", "great"));
        assertThat(invalid.getBody()).containsEntry("error", "Выберите самооценку ответа.");

        var first = browser.post("/api/lessons/lesson-01/answers",
                Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "текст", "selfAssessment", "partial"));
        assertThat(map(answers(first).get("t01-q4"))).containsEntry("selfAssessment", "partial");
        var second = browser.post("/api/lessons/lesson-01/answers",
                Map.of("attemptId", attempt, "questionId", "t01-q4", "value", "текст", "selfAssessment", "correct"));
        assertThat(map(answers(second).get("t01-q4"))).containsEntry("selfAssessment", "partial");
    }

    @Test
    void reflectionHasNoRightOrWrong() {
        TestBrowser browser = student("flow_reflect");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        assertThat(browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q6", 7)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        var response = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q6", 2));
        assertThat(map(answers(response).get("t01-q6"))).containsEntry("correct", null).containsEntry("submitted", true);
    }

    // ---- attempts ----

    @Test
    void aWriteWithoutTheCurrentAttemptIdIsRefused() {
        TestBrowser browser = student("flow_conflict");
        var detail = browser.get("/api/lessons/lesson-01");
        Object attempt = attemptId(detail);

        Object[] wrongIds = {null, 0, ((Number) attempt).longValue() + 1, String.valueOf(attempt), true, 1.5};
        for (Object wrong : wrongIds) {
            var response = browser.post("/api/lessons/lesson-01/answers", body(wrong, "t01-q1", 1));
            assertThat(response.getStatusCode()).as("attemptId %s", wrong).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).containsEntry("code", "attempt_conflict");
        }
        for (String path : new String[] {"drafts", "complete", "restart"}) {
            var response = browser.post("/api/lessons/lesson-01/" + path, body(((Number) attempt).longValue() + 99, "t01-q5", "x"));
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(jdbc.queryForObject("select count(*) from attempt", Integer.class)).isEqualTo(1);
    }

    @Test
    void restartingOpensANewEmptyAttemptAndKeepsTheOldOne() {
        TestBrowser browser = student("flow_restart");
        var detail = browser.get("/api/lessons/lesson-01");
        Object first = attemptId(detail);
        browser.post("/api/lessons/lesson-01/answers", body(first, "t01-q1", 1));
        browser.post("/api/lessons/lesson-01/complete", Map.of("attemptId", first));

        var restarted = browser.post("/api/lessons/lesson-01/restart", Map.of("attemptId", first));
        assertThat(attemptId(restarted)).isNotEqualTo(first);
        assertThat(answers(restarted)).isEmpty();
        // The lesson stays passed, and the old attempt is still in the database.
        assertThat(map(restarted.getBody().get("progress")).get("completedAt")).isNotNull();
        assertThat(map(restarted.getBody().get("attempt"))).containsEntry("completed", true);
        assertThat(jdbc.queryForObject("select count(*) from attempt", Integer.class)).isEqualTo(2);

        // The page still showing the first attempt can no longer write.
        var stale = browser.post("/api/lessons/lesson-01/answers", body(first, "t01-q2", List.of(0, 2)));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- drafts and completion ----

    @Test
    void draftsAreKeptUntilTheAnswerIsSentThenForgotten() {
        TestBrowser browser = student("flow_draft");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));

        assertThat(browser.post("/api/lessons/lesson-01/drafts", body(attempt, "t01-q5", "полдела")).getBody())
                .containsEntry("saved", true);
        var reopened = browser.get("/api/lessons/lesson-01");
        assertThat(map(map(reopened.getBody().get("attempt")).get("drafts"))).containsEntry("t01-q5", "полдела");

        assertThat(browser.post("/api/lessons/lesson-01/drafts", body(attempt, "t01-q1", "x")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(browser.post("/api/lessons/lesson-01/drafts", body(attempt, "t01-q5", 5)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        var sent = browser.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q5", "готово"));
        assertThat(map(map(sent.getBody().get("attempt")).get("drafts"))).doesNotContainKey("t01-q5");
        var late = browser.post("/api/lessons/lesson-01/drafts", body(attempt, "t01-q5", "ещё"));
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void completingALessonIsTheStudentsOwnMarkAndNeedsNoAnswers() {
        TestBrowser browser = student("flow_complete");
        Object attempt = attemptId(browser.get("/api/lessons/lesson-01"));
        var done = browser.post("/api/lessons/lesson-01/complete", Map.of("attemptId", attempt));

        assertThat(done.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(map(done.getBody().get("attempt"))).containsEntry("completed", true);
        var course = browser.get("/api/course").getBody();
        var cards = list(list(course.get("modules")).get(0).get("lessons"));
        assertThat(cards.get(0)).containsEntry("status", "completed").containsEntry("answered", 0);
        assertThat(map(course.get("progress"))).containsEntry("completed", 1).containsEntry("resumeLessonId", "lesson-02");
        // Doing it twice keeps the first date.
        var again = browser.post("/api/lessons/lesson-01/complete", Map.of("attemptId", attempt));
        assertThat(map(again.getBody().get("progress")).get("completedAt")).isEqualTo(map(done.getBody().get("progress")).get("completedAt"));
    }

    // ---- isolation and concurrency ----

    @Test
    void studentsNeverSeeEachOthersAnswers() {
        TestBrowser anna = student("flow_anna");
        TestBrowser boris = student("flow_boris");
        Object attempt = attemptId(anna.get("/api/lessons/lesson-01"));
        anna.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 1));

        var seenByBoris = boris.get("/api/lessons/lesson-01");
        assertThat(answers(seenByBoris)).isEmpty();
        assertThat(attemptId(seenByBoris)).isNotEqualTo(attempt);
        // Boris cannot write into Anna's attempt, even knowing its number.
        var attack = boris.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q2", List.of(0, 2)));
        assertThat(attack.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void simultaneousFirstOpensCreateOneProgressRowAndOneAttempt() throws Exception {
        createAccount("flow_parallel");
        List<TestBrowser> tabs = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tabs.add(new TestBrowser(http).loginAs("flow_parallel", PASSWORD));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<ResponseEntity<Map<String, Object>>>> results = new java.util.ArrayList<>();
            for (TestBrowser tab : tabs) {
                Callable<ResponseEntity<Map<String, Object>>> open = () -> tab.get("/api/lessons/lesson-01");
                results.add(pool.submit(open));
            }
            Object expected = null;
            for (var future : results) {
                var response = future.get();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                Object id = attemptId(response);
                expected = expected == null ? id : expected;
                assertThat(id).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from lesson_progress", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from attempt", Integer.class)).isEqualTo(1);
    }

    @Test
    void simultaneousIdenticalAnswersAreStoredOnce() throws Exception {
        createAccount("flow_double");
        TestBrowser first = new TestBrowser(http).loginAs("flow_double", PASSWORD);
        TestBrowser second = new TestBrowser(http).loginAs("flow_double", PASSWORD);
        Object attempt = attemptId(first.get("/api/lessons/lesson-01"));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map<String, Object>>> a = pool.submit(() -> first.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 1)));
            Future<ResponseEntity<Map<String, Object>>> b = pool.submit(() -> second.post("/api/lessons/lesson-01/answers", body(attempt, "t01-q1", 0)));
            assertThat(a.get().getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(b.get().getStatusCode()).isEqualTo(HttpStatus.OK);
            // Whichever got in first wins for both.
            assertThat(map(answers(a.get()).get("t01-q1")).get("value")).isEqualTo(map(answers(b.get()).get("t01-q1")).get("value"));
        } finally {
            pool.shutdownNow();
        }
    }
}
