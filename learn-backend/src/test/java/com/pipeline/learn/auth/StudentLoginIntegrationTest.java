package com.pipeline.learn.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.pipeline.learn.AbstractIntegrationTest;
import com.pipeline.learn.TestBrowser;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StudentLoginIntegrationTest extends AbstractIntegrationTest {

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
                id, UUID.randomUUID(), login, "Тест Ученик", encoder.encode(PASSWORD));
        return id;
    }

    private static Map<String, Object> userOf(ResponseEntity<Map<String, Object>> response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) response.getBody().get("user");
        return user;
    }

    @Test
    void anonymousVisitorGetsATokenAndNoSessionRow() {
        TestBrowser browser = new TestBrowser(http);
        var response = browser.get("/api/session");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("user")).isNull();
        assertThat((String) response.getBody().get("csrfToken")).isNotBlank();
        assertThat(browser.cookies).containsKey("LEARN_CSRF");
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
    }

    @Test
    void postWithoutCsrfTokenIsRejected() {
        createAccount("csrf_user");
        TestBrowser browser = new TestBrowser(http).open();
        var response = browser.postWithoutCsrf("/api/login", Map.of("login", "csrf_user", "password", PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "csrf");
    }

    @Test
    void loginStartsAHttpOnlySessionThatSlidesForNinetyDays() {
        createAccount("slide_user");
        TestBrowser browser = new TestBrowser(http).open();
        var login = browser.post("/api/login", Map.of("login", "slide_user", "password", PASSWORD));

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userOf(login)).containsEntry("login", "slide_user").containsEntry("name", "Тест Ученик");
        String cookie = browser.setCookieFor("LEARN_SESSION");
        assertThat(cookie).contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=7776000");

        var again = browser.get("/api/session");
        assertThat(userOf(again)).containsEntry("login", "slide_user");
        // Every request of a live session re-sends the cookie, so the 90 days start over.
        assertThat(browser.setCookieFor("LEARN_SESSION")).contains("Max-Age=7776000");
    }

    @Test
    void wrongPasswordAndUnknownLoginLookTheSame() {
        createAccount("same_user");
        TestBrowser a = new TestBrowser(http).open();
        TestBrowser b = new TestBrowser(http).open();
        var wrong = a.post("/api/login", Map.of("login", "same_user", "password", "wrong-password-12"));
        var unknown = b.post("/api/login", Map.of("login", "nobody_here", "password", "wrong-password-12"));

        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrong.getBody()).isEqualTo(unknown.getBody());
    }

    @Test
    void loginIgnoresCaseAndSurroundingSpaces() {
        createAccount("Case_User");
        TestBrowser browser = new TestBrowser(http).open();
        var response = browser.post("/api/login", Map.of("login", "  case_USER ", "password", PASSWORD));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectedEndpointsNeedLogin() {
        var response = new TestBrowser(http).get("/api/course");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Войдите в аккаунт.");
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
    }

    @Test
    void closedAccountCannotLogIn() {
        UUID id = createAccount("closed_user");
        jdbc.update("update account set status = 'CLOSED' where id = ?", id);
        TestBrowser browser = new TestBrowser(http).open();
        var response = browser.post("/api/login", Map.of("login", "closed_user", "password", PASSWORD));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("code", "access_closed");
    }

    @Test
    void closingAccessCutsOffAnOpenSessionOnItsNextRequest() {
        UUID id = createAccount("cut_user");
        TestBrowser browser = new TestBrowser(http).loginAs("cut_user", PASSWORD);
        assertThat(userOf(browser.get("/api/session"))).isNotNull();

        jdbc.update("update account set status = 'CLOSED' where id = ?", id);

        var revoked = browser.get("/api/session");
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(revoked.getBody()).containsEntry("code", "session_revoked");
        // The session is gone: the next call simply sees a logged-out visitor.
        var after = browser.get("/api/session");
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(after.getBody().get("user")).isNull();
    }

    @Test
    void passwordResetFromTheCrmCutsOffOldSessions() {
        UUID id = createAccount("reset_user");
        TestBrowser old = new TestBrowser(http).loginAs("reset_user", PASSWORD);

        // What the CRM does on "reset password": new hash and a bumped session version.
        jdbc.update("update account set password_hash = ?, session_version = session_version + 1 where id = ?",
                encoder.encode("maple-comet-lemon-63"), id);

        assertThat(old.get("/api/session").getBody()).containsEntry("code", "session_revoked");
        assertThat(new TestBrowser(http).open().post("/api/login", Map.of("login", "reset_user", "password", PASSWORD))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        new TestBrowser(http).loginAs("reset_user", "maple-comet-lemon-63");
    }

    @Test
    void studentChangesOwnPasswordAndOtherSessionsEnd() {
        createAccount("change_user");
        TestBrowser laptop = new TestBrowser(http).loginAs("change_user", PASSWORD);
        TestBrowser phone = new TestBrowser(http).loginAs("change_user", PASSWORD);

        var wrongCurrent = laptop.post("/api/change-password", Map.of("currentPassword", "nope-nope-nope", "newPassword", "birch-plum-otter-81"));
        assertThat(wrongCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongCurrent.getBody()).containsEntry("error", "Текущий пароль введён неверно.");

        var tooShort = laptop.post("/api/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "short1"));
        assertThat(tooShort.getBody()).containsEntry("error", "Пароль должен быть длиной от 10 до 1000 символов.");
        var onlyDigits = laptop.post("/api/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "12345678901234"));
        assertThat(onlyDigits.getBody()).containsEntry("error", "Пароль не должен состоять только из цифр.");
        var withLogin = laptop.post("/api/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "xx-change_user-xx"));
        assertThat(withLogin.getBody()).containsEntry("error", "Пароль не должен содержать ваш логин.");

        var changed = laptop.post("/api/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "birch-plum-otter-81"));
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userOf(laptop.get("/api/session"))).containsEntry("login", "change_user");
        assertThat(phone.get("/api/session").getBody()).containsEntry("code", "session_revoked");

        assertThat(new TestBrowser(http).open().post("/api/login", Map.of("login", "change_user", "password", PASSWORD))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        new TestBrowser(http).loginAs("change_user", "birch-plum-otter-81");
    }

    @Test
    void tooManyWrongPasswordsLockTheLoginEvenForTheRightPassword() {
        createAccount("lock_user");
        TestBrowser browser = new TestBrowser(http).open();
        for (int i = 0; i < 8; i++) {
            assertThat(browser.post("/api/login", Map.of("login", "lock_user", "password", "wrong-password-" + i))
                    .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        var locked = browser.post("/api/login", Map.of("login", "lock_user", "password", PASSWORD));
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void logoutEndsTheSession() {
        createAccount("bye_user");
        TestBrowser browser = new TestBrowser(http).loginAs("bye_user", PASSWORD);
        var out = browser.post("/api/logout", Map.of());
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(browser.get("/api/session").getBody().get("user")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
    }
}
