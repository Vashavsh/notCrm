package com.pipeline.learn.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.utility.MountableFile;

import com.pipeline.learn.AbstractDatabaseTest;

/** Runs deploy/db-roles.sql the way the operator does (psql, as a superuser) on a scratch database, then uses the role it creates. */
class DbRolesScriptTest extends AbstractDatabaseTest {

    private ExecResult runScript(String database, String password) throws IOException, InterruptedException {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath("deploy/db-roles.sql"), "/tmp/db-roles.sql");
        return POSTGRES.execInContainer("psql", "-U", POSTGRES.getUsername(), "-d", database, "-v", "ON_ERROR_STOP=1",
                "-v", "learn_password=" + password, "-f", "/tmp/db-roles.sql");
    }

    private Connection as(String user, String password, String database) throws SQLException {
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        return DriverManager.getConnection(url, user, password);
    }

    @Test
    void theCourseSiteRoleOwnsItsSchemaAndCannotTouchTheCrmsTables() throws Exception {
        try (Connection admin = as(POSTGRES.getUsername(), POSTGRES.getPassword(), POSTGRES.getDatabaseName()); var s = admin.createStatement()) {
            s.execute("create database roles_probe");
        }
        try (Connection admin = as(POSTGRES.getUsername(), POSTGRES.getPassword(), "roles_probe"); var s = admin.createStatement()) {
            // A table of the CRM, created by someone other than the course site's role.
            s.execute("create table public.students (id int, full_name text)");
            s.execute("insert into public.students values (1, 'Иван Петров')");
        }

        ExecResult first = runScript("roles_probe", "first-pass");
        assertThat(first.getExitCode()).as(first.getStderr()).isZero();
        // Running it again is fine and changes the password.
        ExecResult second = runScript("roles_probe", "second-pass");
        assertThat(second.getExitCode()).as(second.getStderr()).isZero();

        assertThatThrownBy(() -> as("learn_app", "first-pass", "roles_probe")).hasMessageContaining("authentication failed");
        try (Connection app = as("learn_app", "second-pass", "roles_probe"); var s = app.createStatement()) {
            var path = s.executeQuery("show search_path");
            path.next();
            assertThat(path.getString(1)).isEqualTo("learn");

            s.execute("create table t (id int)");
            s.execute("insert into t values (1)");
            var where = s.executeQuery("select schemaname from pg_tables where tablename = 't'");
            where.next();
            assertThat(where.getString(1)).isEqualTo("learn");

            assertThatThrownBy(() -> s.executeQuery("select * from public.students")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("create table public.sneaky (id int)")).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> s.execute("update public.students set full_name = 'x'")).hasMessageContaining("permission denied");
        }
    }
}
