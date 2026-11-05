package com.pipeline.crm.student;

import java.util.UUID;

/** Published inside the transaction that deletes a student (soft delete); the course site closes their access. */
public record StudentDeleted(UUID studentId, UUID actorId) {
}
