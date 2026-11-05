package com.pipeline.crm.learn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pipeline.crm.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudentLearnAccessIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD_FORMAT = "[a-z]{3,8}-[a-z]{3,8}-[a-z]{3,8}-[1-9][0-9]";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private String admin;
    private String curator;
    private String otherCurator;
    private UUID curatorId;
    private UUID stageId;

    @BeforeEach
    void setUp() throws Exception {
        LearnBridgeSchema.install(dataSource);
        jdbc.update("delete from learn.account");
        jdbc.update("delete from learn.lesson_catalog");
        jdbc.update("delete from student_learn_access");
        admin = login("admin", "admin123");
        curator = login("anya.t", "curator1");
        otherCurator = login("igor.l", "curator2");
        curatorId = UUID.fromString(getJson("/api/v1/auth/me", curator).get("id").asText());
        stageId = UUID.fromString(getJson("/api/v1/pipeline-stages", admin).get(0).get("id").asText());
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from learn.account");
        jdbc.update("delete from learn.lesson_catalog");
    }

    // ---- issuing ----

    @Test
    void issuingAccessCreatesTheLoginOnTheCourseSiteAndShowsThePasswordToTheCurator() throws Exception {
        UUID student = createStudent("Иван Петров", "@Ivan_Petrov");

        JsonNode before = getJson("/api/v1/students/" + student + "/learn", curator);
        assertThat(before.get("connected").asBoolean()).isTrue();
        assertThat(before.get("account").isNull()).isTrue();

        JsonNode issued = postJson("/api/v1/students/" + student + "/learn/issue", curator, 200);
        JsonNode account = issued.get("account");
        assertThat(account.get("login").asText()).isEqualTo("ivan_petrov");
        assertThat(account.get("password").asText()).matches(PASSWORD_FORMAT);
        assertThat(account.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(account.get("passwordChangedByStudent").asBoolean()).isFalse();

        // The account on the course site: BCrypt hash of exactly that password, tied to the student, with the student's name.
        Map<String, Object> row = jdbc.queryForMap("select * from learn.account where student_id = ?", student);
        assertThat(row).containsEntry("login", "ivan_petrov").containsEntry("full_name", "Иван Петров").containsEntry("status", "ACTIVE");
        assertThat(encoder.matches(account.get("password").asText(), (String) row.get("password_hash"))).isTrue();
        assertThat(row.get("session_version")).isEqualTo(0);

        // The curator can open the card again later and still sees the password.
        assertThat(getJson("/api/v1/students/" + student + "/learn", curator).get("account").get("password").asText())
                .isEqualTo(account.get("password").asText());
    }

    @Test
    void theAuditLogRecordsWhoIssuedAccessButNeverThePassword() throws Exception {
        UUID student = createStudent("Мария Лебедева", null);
        String password = postJson("/api/v1/students/" + student + "/learn/issue", admin, 200).get("account").get("password").asText();

        List<Map<String, Object>> entries = jdbc.queryForList("select * from audit_log where entity_id = ? and entity_type = 'student_learn_access'", student);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0)).containsEntry("action", "issue");
        assertThat(String.valueOf(entries.get(0))).doesNotContain(password);
    }

    @Test
    void theLoginComesFromTheNameWhenThereIsNoHandleAndAvoidsTakenOnes() throws Exception {
        UUID first = createStudent("Анна Смирнова", null);
        UUID second = createStudent("Анна Смирнова", "");
        assertThat(postJson("/api/v1/students/" + first + "/learn/issue", admin, 200).get("account").get("login").asText()).isEqualTo("anna_smirnova");
        assertThat(postJson("/api/v1/students/" + second + "/learn/issue", admin, 200).get("account").get("login").asText()).isEqualTo("anna_smirnova2");
    }

    @Test
    void accessCanBeIssuedOnlyOnce() throws Exception {
        UUID student = createStudent("Олег Новиков", "@oleg_n");
        postJson("/api/v1/students/" + student + "/learn/issue", admin, 200);
        postJson("/api/v1/students/" + student + "/learn/issue", admin, 409);
        assertThat(jdbc.queryForObject("select count(*) from learn.account where student_id = ?", Integer.class, student)).isEqualTo(1);
    }

    // ---- who may do it ----

    @Test
    void onlyAnAdminAndTheStudentsOwnCuratorMayManageAccess() throws Exception {
        UUID student = createStudent("Борис Орлов", "@boris_o");
        String url = "/api/v1/students/" + student + "/learn";

        // Another curator gets "not found", as for the rest of the card.
        mockMvc.perform(get(url).header("Authorization", "Bearer " + otherCurator)).andExpect(status().isNotFound());
        mockMvc.perform(post(url + "/issue").header("Authorization", "Bearer " + otherCurator)).andExpect(status().isNotFound());
        mockMvc.perform(post(url + "/reset-password").header("Authorization", "Bearer " + otherCurator)).andExpect(status().isNotFound());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from learn.account", Integer.class)).isZero();

        postJson(url + "/issue", curator, 200);
        assertThat(getJson(url, admin).get("account").get("login").asText()).isEqualTo("boris_o");
    }

    @Test
    void anUnknownStudentIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/students/" + UUID.randomUUID() + "/learn").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    // ---- resetting ----

    @Test
    void resettingGivesANewPasswordAndEndsTheOpenSessions() throws Exception {
        UUID student = createStudent("Вера Белова", "@vera_b");
        String first = postJson("/api/v1/students/" + student + "/learn/issue", curator, 200).get("account").get("password").asText();

        JsonNode reset = postJson("/api/v1/students/" + student + "/learn/reset-password", curator, 200);
        String second = reset.get("account").get("password").asText();

        assertThat(second).matches(PASSWORD_FORMAT).isNotEqualTo(first);
        Map<String, Object> row = jdbc.queryForMap("select * from learn.account where student_id = ?", student);
        assertThat(encoder.matches(second, (String) row.get("password_hash"))).isTrue();
        assertThat(encoder.matches(first, (String) row.get("password_hash"))).isFalse();
        // The bumped version is what cuts off sessions that were open with the old password.
        assertThat(row.get("session_version")).isEqualTo(1);
        assertThat(jdbc.queryForList("select action from audit_log where entity_id = ? and entity_type = 'student_learn_access' order by created_at", String.class, student))
                .containsExactly("issue", "reset_password");
    }

    @Test
    void resettingNeedsIssuedAccess() throws Exception {
        UUID student = createStudent("Глеб Яров", "@gleb_y");
        postJson("/api/v1/students/" + student + "/learn/reset-password", admin, 409);
    }

    @Test
    void whenTheStudentSetsTheirOwnPasswordTheCuratorNoLongerSeesTheOldOne() throws Exception {
        UUID student = createStudent("Дарья Ким", "@darya_k");
        postJson("/api/v1/students/" + student + "/learn/issue", curator, 200);

        // What the course site does when the student changes the password in their profile.
        jdbc.update("update learn.account set password_hash = ?, session_version = session_version + 1 where student_id = ?",
                encoder.encode("my-own-secret-pass-1"), student);

        JsonNode account = getJson("/api/v1/students/" + student + "/learn", curator).get("account");
        assertThat(account.get("passwordChangedByStudent").asBoolean()).isTrue();
        assertThat(account.get("password").isNull()).isTrue();

        // A reset by the curator brings a visible password back.
        JsonNode reset = postJson("/api/v1/students/" + student + "/learn/reset-password", curator, 200).get("account");
        assertThat(reset.get("passwordChangedByStudent").asBoolean()).isFalse();
        assertThat(reset.get("password").asText()).matches(PASSWORD_FORMAT);
    }

    // ---- progress ----

    @Test
    void progressShowsBlocksLessonsAndVisits() throws Exception {
        jdbc.update("insert into learn.lesson_catalog values ('lesson-01', 'block-1', 'Введение', 1, 1, 'Первый урок')");
        jdbc.update("insert into learn.lesson_catalog values ('lesson-02', 'block-1', 'Введение', 1, 2, 'Второй урок')");
        jdbc.update("insert into learn.lesson_catalog values ('lesson-03', 'block-2', 'Retention', 2, 3, 'Третий урок')");
        UUID student = createStudent("Егор Волков", "@egor_v");
        postJson("/api/v1/students/" + student + "/learn/issue", curator, 200);
        UUID account = jdbc.queryForObject("select id from learn.account where student_id = ?", UUID.class, student);
        jdbc.update("insert into learn.lesson_progress (account_id, lesson_id, opened_at, last_opened_at, completed_at) values (?, 'lesson-01', '2026-11-01T10:00:00Z', '2026-11-02T10:00:00Z', '2026-11-02T10:30:00Z')", account);
        jdbc.update("insert into learn.lesson_progress (account_id, lesson_id, opened_at, last_opened_at) values (?, 'lesson-02', '2026-11-03T09:00:00Z', '2026-11-04T09:00:00Z')", account);
        jdbc.update("update learn.account set last_seen_at = '2026-11-04T09:05:00Z' where id = ?", account);

        JsonNode view = getJson("/api/v1/students/" + student + "/learn", curator);
        JsonNode progress = view.get("progress");

        assertThat(progress.get("completed").asInt()).isEqualTo(1);
        assertThat(progress.get("total").asInt()).isEqualTo(3);
        assertThat(progress.get("firstOpenedAt").asText()).isEqualTo("2026-11-01T10:00:00Z");
        assertThat(progress.get("lastOpenedAt").asText()).isEqualTo("2026-11-04T09:00:00Z");
        JsonNode blocks = progress.get("blocks");
        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).get("title").asText()).isEqualTo("Введение");
        assertThat(blocks.get(0).get("completed").asInt()).isEqualTo(1);
        assertThat(blocks.get(0).get("total").asInt()).isEqualTo(2);
        assertThat(blocks.get(0).get("lessons").get(1).get("openedAt").asText()).isEqualTo("2026-11-03T09:00:00Z");
        assertThat(blocks.get(0).get("lessons").get(1).get("completedAt").isNull()).isTrue();
        assertThat(blocks.get(1).get("lessons").get(0).get("openedAt").isNull()).isTrue();
        assertThat(view.get("account").get("lastSeenAt").asText()).isEqualTo("2026-11-04T09:05:00Z");
    }

    // ---- the course site is not there yet ----

    @Test
    void withoutTheCourseSiteTheTabSaysSoAndNothingBreaks() throws Exception {
        UUID student = createStudent("Зоя Крылова", "@zoya_k");
        jdbc.execute("alter schema learn rename to learn_parked");
        try {
            JsonNode view = getJson("/api/v1/students/" + student + "/learn", curator);
            assertThat(view.get("connected").asBoolean()).isFalse();
            assertThat(view.get("account").isNull()).isTrue();
            postJson("/api/v1/students/" + student + "/learn/issue", curator, 409);
        } finally {
            jdbc.execute("alter schema learn_parked rename to learn");
        }
    }

    // ---- closing and reopening ----

    @Test
    void closingAccessLocksTheStudentOutAndReopeningLetsThemBackIn() throws Exception {
        UUID student = createStudent("Кира Седова", "@kira_s");
        postJson("/api/v1/students/" + student + "/learn/issue", curator, 200);

        JsonNode closed = postJson("/api/v1/students/" + student + "/learn/close", curator, 200);
        assertThat(closed.get("account").get("status").asText()).isEqualTo("CLOSED");
        Map<String, Object> row = jdbc.queryForMap("select status, session_version from learn.account where student_id = ?", student);
        assertThat(row).containsEntry("status", "CLOSED").containsEntry("session_version", 1);

        // Closing again changes nothing and is not logged twice.
        postJson("/api/v1/students/" + student + "/learn/close", curator, 200);
        assertThat(jdbc.queryForObject("select session_version from learn.account where student_id = ?", Integer.class, student)).isEqualTo(1);
        assertThat(jdbc.queryForList("select action from audit_log where entity_id = ? and entity_type = 'student_learn_access' order by created_at", String.class, student))
                .containsExactly("issue", "close_access");

        JsonNode reopened = postJson("/api/v1/students/" + student + "/learn/reopen", curator, 200);
        assertThat(reopened.get("account").get("status").asText()).isEqualTo("ACTIVE");
        // The password is still the one issued; sessions that were open before the closing stay ended (version 1).
        assertThat(reopened.get("account").get("password").asText()).matches(PASSWORD_FORMAT);
        assertThat(jdbc.queryForObject("select session_version from learn.account where student_id = ?", Integer.class, student)).isEqualTo(1);
    }

    @Test
    void closingNeedsIssuedAccessAndTheRightToSeeTheStudent() throws Exception {
        UUID student = createStudent("Лев Орлов", "@lev_o");
        postJson("/api/v1/students/" + student + "/learn/close", admin, 409);
        postJson("/api/v1/students/" + student + "/learn/reopen", admin, 409);

        postJson("/api/v1/students/" + student + "/learn/issue", admin, 200);
        mockMvc.perform(post("/api/v1/students/" + student + "/learn/close").header("Authorization", "Bearer " + otherCurator))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/students/" + student + "/learn/reopen").header("Authorization", "Bearer " + otherCurator))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select status from learn.account where student_id = ?", String.class, student)).isEqualTo("ACTIVE");
    }

    @Test
    void deletingAStudentClosesTheirAccess() throws Exception {
        UUID student = createStudent("Мила Фёдорова", "@mila_f");
        postJson("/api/v1/students/" + student + "/learn/issue", admin, 200);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/students/" + student)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());

        Map<String, Object> row = jdbc.queryForMap("select status, session_version from learn.account where student_id = ?", student);
        assertThat(row).containsEntry("status", "CLOSED").containsEntry("session_version", 1);
        assertThat(jdbc.queryForList("select action from audit_log where entity_id = ? and entity_type = 'student_learn_access' order by created_at", String.class, student))
                .containsExactly("issue", "close_access");
    }

    @Test
    void deletingAStudentWithoutAccessOrWithoutTheCourseSiteStillWorks() throws Exception {
        UUID withoutAccess = createStudent("Нина Зотова", "@nina_z");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/students/" + withoutAccess)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());

        UUID student = createStudent("Олег Рябов", "@oleg_r");
        jdbc.execute("alter schema learn rename to learn_parked");
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/students/" + student)
                    .header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        } finally {
            jdbc.execute("alter schema learn_parked rename to learn");
        }
    }

    // ---- helpers ----

    private UUID createStudent(String name, String telegram) throws Exception {
        String body = "{\"fullName\":\"" + name + "\",\"curatorId\":\"" + curatorId + "\",\"currentStageId\":\"" + stageId + "\""
                + (telegram != null ? ",\"telegramUsername\":\"" + telegram + "\"" : "") + "}";
        MvcResult result = mockMvc.perform(post("/api/v1/students").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode postJson(String url, String token, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(url).header("Authorization", "Bearer " + token)).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return content.isBlank() ? null : json.readTree(content);
    }
}
