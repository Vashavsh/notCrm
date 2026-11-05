package com.pipeline.learn.account;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A student's login on the course site. Rows are created and changed by the CRM through the bridge
 * (see V1__account_bridge.sql); this app only reads them, except for the student's own password
 * change and the last-seen timestamp.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 100)
    private String login;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getLogin() {
        return login;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    /** The student's own password change: new hash, and every other session of this account stops working. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.sessionVersion++;
    }
}
