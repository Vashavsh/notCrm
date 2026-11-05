package com.pipeline.learn.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.pipeline.learn.AbstractIntegrationTest;

/** What the CRM's database role (crm_probe in these tests) may and may not do in schema learn, as granted by migration V5. */
@SpringBootTest
class BridgePermissionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from account");
    }

    private Connection crm() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crm_probe", "probe-pass");
    }

    @Test
    void theCrmCanIssueAndResetAccountsAndReadProgress() throws SQLException {
        UUID student = UUID.randomUUID();
        try (Connection c = crm(); var s = c.createStatement()) {
            s.execute("insert into learn.account (id, student_id, login, full_name, password_hash) values (gen_random_uuid(), '" + student + "', 'probe_user', 'Проба', 'x')");
            assertThat(s.executeUpdate("update learn.account set status = 'CLOSED', session_version = session_version + 1 where student_id = '" + student + "'")).isEqualTo(1);
            var rows = s.executeQuery("select count(*) from learn.v_student_progress where student_id = '" + student + "'");
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(2);
            var account = s.executeQuery("select login, status from learn.account where student_id = '" + student + "'");
            account.next();
            assertThat(account.getString("status")).isEqualTo("CLOSED");
        }
    }

    @Test
    void theCrmCannotReachWhatBelongsToTheCourseSite() throws SQLException {
        try (Connection c = crm(); var s = c.createStatement()) {
            for (String table : new String[] {"lesson_progress", "attempt", "attachment", "spring_session", "spring_session_attributes", "lesson_catalog"}) {
                assertThatThrownBy(() -> s.executeQuery("select * from learn." + table)).as(table).hasMessageContaining("permission denied");
            }
            assertThatThrownBy(() -> s.executeUpdate("delete from learn.account")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.executeUpdate("create table learn.sneaky (id int)")).hasMessageContaining("permission denied");
        }
    }
}
