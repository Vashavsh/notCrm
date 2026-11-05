-- Progress of a student through the course: one row per (student, lesson), and one "attempt" per
-- pass through the lesson. "Start again" opens a new attempt; old attempts and the "completed" mark stay.
-- Attempt and attachment ids are plain integers because the front end sends them back as numbers.

CREATE TABLE lesson_progress (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    lesson_id           VARCHAR(50)  NOT NULL,
    opened_at           TIMESTAMPTZ,
    last_opened_at      TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    current_attempt_id  BIGINT,
    CONSTRAINT uq_lesson_progress UNIQUE (account_id, lesson_id)
);

CREATE TABLE attempt (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    progress_id   BIGINT       NOT NULL REFERENCES lesson_progress (id) ON DELETE CASCADE,
    -- question id -> {value, correct, feedback, selfAssessment, submitted, ...}; drafts: question id -> text
    answers       JSONB        NOT NULL DEFAULT '{}',
    drafts        JSONB        NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);

ALTER TABLE lesson_progress
    ADD CONSTRAINT fk_lesson_progress_current_attempt
    FOREIGN KEY (current_attempt_id) REFERENCES attempt (id) ON DELETE SET NULL;

CREATE INDEX idx_attempt_progress ON attempt (progress_id);

-- Images students attach to open answers, already re-encoded to PNG by the application.
CREATE TABLE attachment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id   BIGINT       NOT NULL REFERENCES attempt (id) ON DELETE CASCADE,
    question_id  VARCHAR(80)  NOT NULL,
    image        BYTEA        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_attempt ON attachment (attempt_id, question_id);
