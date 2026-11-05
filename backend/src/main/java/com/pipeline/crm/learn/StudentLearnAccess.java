package com.pipeline.crm.learn;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The CRM's own record of a student's course-site login (see V10). */
@Entity
@Table(name = "student_learn_access")
@Getter
@Setter
public class StudentLearnAccess {

    @Id
    @Column(name = "student_id")
    private UUID studentId;

    @Column(nullable = false, length = 100)
    private String login;

    @Column(name = "password_plain", length = 100)
    private String passwordPlain;

    @Column(name = "issued_hash", nullable = false)
    private String issuedHash;

    @Column(name = "issued_by_id", nullable = false)
    private UUID issuedById;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
