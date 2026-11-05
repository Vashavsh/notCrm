package com.pipeline.crm.learn;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.pipeline.crm.common.exception.ConflictException;

import lombok.RequiredArgsConstructor;

/**
 * The CRM's side of the bridge to the course site: plain SQL on the {@code learn} schema, which belongs to learn-backend.
 * The CRM writes {@code learn.account} (issue access, reset password, close or reopen it) and reads {@code learn.v_student_progress};
 * it touches nothing else there. Until learn-backend has run once the schema does not exist, and {@link #available()} says so.
 */
@Component
@RequiredArgsConstructor
public class LearnBridge {

    public record Account(String login, String passwordHash, String status, int sessionVersion, OffsetDateTime lastSeenAt,
            OffsetDateTime createdAt) {
    }

    public record LessonRow(String lessonId, String moduleId, String moduleTitle, int moduleOrder, int lessonOrder,
            String lessonTitle, OffsetDateTime openedAt, OffsetDateTime lastOpenedAt, OffsetDateTime completedAt) {
    }

    private final JdbcTemplate jdbc;

    public boolean available() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select to_regclass('learn.account') is not null and to_regclass('learn.v_student_progress') is not null", Boolean.class));
    }

    public void requireAvailable() {
        if (!available()) {
            throw new ConflictException("The course site is not connected yet");
        }
    }

    public Optional<Account> account(UUID studentId) {
        return jdbc.query("select login, password_hash, status, session_version, last_seen_at, created_at from learn.account where student_id = ?",
                (ResultSet rs, int i) -> new Account(rs.getString("login"), rs.getString("password_hash"), rs.getString("status"),
                        rs.getInt("session_version"), time(rs, "last_seen_at"), time(rs, "created_at")), studentId).stream().findFirst();
    }

    public boolean loginTaken(String login) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from learn.account where lower(login) = lower(?))", Boolean.class, login));
    }

    public void insertAccount(UUID studentId, String login, String fullName, String passwordHash) {
        try {
            jdbc.update("insert into learn.account (id, student_id, login, full_name, password_hash, status, session_version) "
                    + "values (gen_random_uuid(), ?, ?, ?, ?, 'ACTIVE', 0)", studentId, login, fullName, passwordHash);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("Access has already been issued");
        }
    }

    /** A new password; bumping the session version ends every open session of the student. */
    public void resetPassword(UUID studentId, String passwordHash) {
        jdbc.update("update learn.account set password_hash = ?, session_version = session_version + 1, updated_at = now() where student_id = ?",
                passwordHash, studentId);
    }

    public List<LessonRow> progress(UUID studentId) {
        return jdbc.query("select lesson_id, module_id, module_title, module_order, lesson_order, lesson_title, opened_at, last_opened_at, completed_at "
                + "from learn.v_student_progress where student_id = ? order by module_order, lesson_order",
                (ResultSet rs, int i) -> new LessonRow(rs.getString("lesson_id"), rs.getString("module_id"), rs.getString("module_title"),
                        rs.getInt("module_order"), rs.getInt("lesson_order"), rs.getString("lesson_title"), time(rs, "opened_at"),
                        time(rs, "last_opened_at"), time(rs, "completed_at")), studentId);
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }
}
