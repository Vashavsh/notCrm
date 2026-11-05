package com.pipeline.learn.course;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pipeline.learn.AbstractIntegrationTest;

/** The read side of the bridge: the lesson catalog and the progress view the CRM reads. */
@SpringBootTest
class BridgeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CatalogSync catalogSync;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from account");
    }

    @Test
    void theCatalogHoldsTheLessonsOfTheCourseWithTheirBlocks() {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from lesson_catalog order by lesson_order");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("lesson_id", "lesson-01").containsEntry("module_id", "block-1")
                .containsEntry("module_order", 1).containsEntry("title", "Первый тестовый урок");
        assertThat(rows.get(0).get("module_title")).isEqualTo("Введение. Базовые метрики и анализ рынка");
        assertThat(rows.get(1)).containsEntry("lesson_id", "lesson-02").containsEntry("module_order", 2);
    }

    @Test
    void syncingAgainReplacesTheCatalogInsteadOfDuplicatingIt() {
        jdbc.update("insert into lesson_catalog values ('gone', 'block-1', 'x', 1, 99, 'Удалённый урок')");
        catalogSync.sync();
        catalogSync.sync();

        assertThat(jdbc.queryForObject("select count(*) from lesson_catalog", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from lesson_catalog where lesson_id = 'gone'", Integer.class)).isZero();
    }

    @Test
    void theProgressViewHasARowForEveryLessonEvenUnopenedOnes() {
        UUID student = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash) values (?, ?, 'bridge_user', 'Анна', 'x')", account, student);
        jdbc.update("insert into lesson_progress (account_id, lesson_id, opened_at, last_opened_at, completed_at) "
                + "values (?, 'lesson-01', '2026-11-01T10:00:00Z', '2026-11-03T12:00:00Z', '2026-11-03T12:05:00Z')", account);

        List<Map<String, Object>> rows = jdbc.queryForList("select * from v_student_progress where student_id = ? order by lesson_order", student);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("lesson_id", "lesson-01");
        assertThat(rows.get(0).get("opened_at")).isNotNull();
        assertThat(rows.get(0).get("completed_at")).isNotNull();
        assertThat(rows.get(1)).containsEntry("lesson_id", "lesson-02").containsEntry("lesson_title", "Второй тестовый урок");
        assertThat(rows.get(1).get("opened_at")).isNull();
        assertThat(rows.get(1).get("completed_at")).isNull();
    }

    @Test
    void aStudentWithoutAnAccountHasNoRowsAndStudentsDoNotSeeEachOthersProgress() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID firstAccount = UUID.randomUUID();
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash) values (?, ?, 'bridge_a', 'А', 'x')", firstAccount, first);
        jdbc.update("insert into account (id, student_id, login, full_name, password_hash) values (?, ?, 'bridge_b', 'Б', 'x')", UUID.randomUUID(), second);
        jdbc.update("insert into lesson_progress (account_id, lesson_id, completed_at) values (?, 'lesson-01', now())", firstAccount);

        assertThat(jdbc.queryForObject("select count(*) from v_student_progress where student_id = ?", Integer.class, UUID.randomUUID())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from v_student_progress where student_id = ? and completed_at is not null", Integer.class, second)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from v_student_progress where student_id = ? and completed_at is not null", Integer.class, first)).isEqualTo(1);
    }
}
