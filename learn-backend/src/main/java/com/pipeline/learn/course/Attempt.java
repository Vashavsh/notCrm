package com.pipeline.learn.course;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One pass through a lesson: the answers given and the unsent drafts. */
@Entity
@Table(name = "attempt")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "progress_id", nullable = false)
    private Long progressId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Map<String, Object>> answers = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> drafts = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Attempt() {
    }

    public Attempt(Long progressId) {
        this.progressId = progressId;
    }

    public Long getId() {
        return id;
    }

    public Long getProgressId() {
        return progressId;
    }

    public Map<String, Map<String, Object>> getAnswers() {
        return answers;
    }

    public Map<String, String> getDrafts() {
        return drafts;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void putAnswer(String questionId, Map<String, Object> answer) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>(answers);
        copy.put(questionId, answer);
        this.answers = copy;
    }

    public void putDraft(String questionId, String text) {
        Map<String, String> copy = new LinkedHashMap<>(drafts);
        copy.put(questionId, text);
        this.drafts = copy;
    }

    public void removeDraft(String questionId) {
        Map<String, String> copy = new LinkedHashMap<>(drafts);
        copy.remove(questionId);
        this.drafts = copy;
    }

    public void markCompleted(Instant when) {
        if (completedAt == null) {
            completedAt = when;
        }
    }
}
