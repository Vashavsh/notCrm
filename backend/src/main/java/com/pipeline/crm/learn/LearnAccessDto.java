package com.pipeline.crm.learn;

import java.time.Instant;
import java.util.List;

/**
 * The "Учебный сайт" tab of a student. {@code connected} is false until the course site has been set up on the server;
 * {@code account} is null until access is issued.
 */
public record LearnAccessDto(boolean connected, AccountDto account, ProgressDto progress) {

    /**
     * @param password the password as the CRM issued it, or null when the student has set their own since
     * @param passwordChangedByStudent true when the password in the course site is no longer the one the CRM issued
     * @param status ACTIVE or CLOSED
     */
    public record AccountDto(String login, String password, boolean passwordChangedByStudent, String status, Instant issuedAt,
            Instant lastSeenAt) {
    }

    public record ProgressDto(int completed, int total, Instant firstOpenedAt, Instant lastOpenedAt, List<BlockDto> blocks) {
    }

    public record BlockDto(String id, String title, int order, int completed, int total, List<LessonDto> lessons) {
    }

    public record LessonDto(String id, String title, int order, Instant openedAt, Instant completedAt) {
    }
}
