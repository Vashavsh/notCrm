package com.pipeline.learn.course;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "lesson_id", nullable = false, length = 50)
    private String lessonId;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "current_attempt_id")
    private Long currentAttemptId;

    protected LessonProgress() {
    }

    public LessonProgress(UUID accountId, String lessonId) {
        this.accountId = accountId;
        this.lessonId = lessonId;
    }

    public Long getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getLessonId() {
        return lessonId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getLastOpenedAt() {
        return lastOpenedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getCurrentAttemptId() {
        return currentAttemptId;
    }

    public void markOpened(Instant now) {
        if (openedAt == null) {
            openedAt = now;
        }
        lastOpenedAt = now;
    }

    public void markCompleted(Instant when) {
        if (completedAt == null) {
            completedAt = when;
        }
    }

    public void setCurrentAttemptId(Long currentAttemptId) {
        this.currentAttemptId = currentAttemptId;
    }
}
