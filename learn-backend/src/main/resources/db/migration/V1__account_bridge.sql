-- Student accounts of the course site. This table is the write side of the bridge:
-- the CRM creates/updates rows here (issue access, reset password, close access) and the
-- course app only reads it. The CRM student is referenced by id only, with no foreign key,
-- because the two apps share a database but not each other's tables.

CREATE TABLE account (
    id               UUID PRIMARY KEY,
    student_id       UUID         NOT NULL CONSTRAINT uq_account_student UNIQUE,
    login            VARCHAR(100) NOT NULL,
    full_name        VARCHAR(200) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- Bumped by the CRM on password reset and on closing access: every session issued
    -- with an older version is rejected on its next request.
    session_version  INTEGER      NOT NULL DEFAULT 0,
    last_seen_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

-- Logins are matched case-insensitively and must be unique.
CREATE UNIQUE INDEX uq_account_login ON account (lower(login));
