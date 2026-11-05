-- What the CRM remembers about a student's login on the course site. The account itself lives in the
-- `learn` schema (owned by learn-backend); this is the CRM's own record next to it.
--
-- password_plain: kept readable so the curator can pass the login on again (decided 2026-11-05, to be removed
-- once the bot delivers credentials). It is dropped from the screen as soon as the student sets their own password:
-- issued_hash is the hash the CRM wrote last, and when learn.account holds a different one the student changed it.

CREATE TABLE student_learn_access (
    student_id      UUID PRIMARY KEY REFERENCES students (id) ON DELETE CASCADE,
    login           VARCHAR(100)  NOT NULL,
    password_plain  VARCHAR(100),
    issued_hash     VARCHAR(255)  NOT NULL,
    issued_by_id    UUID          NOT NULL REFERENCES users (id),
    issued_at       TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);
