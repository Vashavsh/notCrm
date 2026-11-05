-- Read side of the bridge: what the CRM shows on a student's "Учебный сайт" tab.
--
-- lesson_catalog is a copy of the course's lesson list, refreshed by the application at every startup, so the CRM can
-- show lesson and block titles (also for lessons the student has not opened) without reading the content files.
-- v_student_progress has one row per (student with an account) x (lesson in the catalog).

CREATE TABLE lesson_catalog (
    lesson_id     VARCHAR(50)  PRIMARY KEY,
    module_id     VARCHAR(50)  NOT NULL,
    module_title  VARCHAR(300) NOT NULL,
    module_order  INTEGER      NOT NULL,
    lesson_order  INTEGER      NOT NULL,
    title         VARCHAR(300) NOT NULL
);

CREATE VIEW v_student_progress AS
SELECT a.student_id,
       c.lesson_id,
       c.module_id,
       c.module_title,
       c.module_order,
       c.lesson_order,
       c.title AS lesson_title,
       p.opened_at,
       p.last_opened_at,
       p.completed_at
FROM account a
CROSS JOIN lesson_catalog c
LEFT JOIN lesson_progress p ON p.account_id = a.id AND p.lesson_id = c.lesson_id;
