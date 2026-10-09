-- ============================================================================
-- College Timetable Lookup System - baseline schema
--
-- Portability note: engine and character set clauses are intentionally omitted.
-- MySQL 8 defaults to InnoDB and utf8mb4, so the same file also applies unchanged to
-- the H2 MySQL-compatibility database used by the test suite.
-- ============================================================================

CREATE TABLE departments (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(150) NOT NULL,
    code        VARCHAR(20)  NOT NULL,
    active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_code UNIQUE (code)
);

CREATE TABLE programs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    department_id BIGINT       NOT NULL,
    name          VARCHAR(150) NOT NULL,
    code          VARCHAR(30)  NOT NULL,
    degree_type   VARCHAR(20)  NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_programs_code UNIQUE (code),
    CONSTRAINT fk_programs_department FOREIGN KEY (department_id) REFERENCES departments (id)
);

CREATE INDEX ix_programs_department ON programs (department_id);

CREATE TABLE academic_terms (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    academic_year    VARCHAR(20) NOT NULL,
    semester_number  INT         NOT NULL,
    start_date       DATE        NOT NULL,
    end_date         DATE        NOT NULL,
    active           TINYINT(1)  NOT NULL DEFAULT 1,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_academic_terms_year_semester UNIQUE (academic_year, semester_number),
    CONSTRAINT ck_academic_terms_semester CHECK (semester_number BETWEEN 1 AND 12)
);

CREATE TABLE sections (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    program_id       BIGINT       NOT NULL,
    academic_term_id BIGINT       NOT NULL,
    section_name     VARCHAR(20)  NOT NULL,
    active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sections_program_term_name UNIQUE (program_id, academic_term_id, section_name),
    CONSTRAINT fk_sections_program FOREIGN KEY (program_id) REFERENCES programs (id),
    CONSTRAINT fk_sections_academic_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id)
);

CREATE INDEX ix_sections_program ON sections (program_id);
CREATE INDEX ix_sections_term ON sections (academic_term_id);

CREATE TABLE students (
    id                             BIGINT       NOT NULL AUTO_INCREMENT,
    registration_number            VARCHAR(40)  NOT NULL,
    registration_number_normalized VARCHAR(40)  NOT NULL,
    full_name                      VARCHAR(150) NULL,
    section_id                     BIGINT       NOT NULL,
    active                         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at                     DATETIME     NOT NULL,
    updated_at                     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_students_registration_normalized UNIQUE (registration_number_normalized),
    CONSTRAINT fk_students_section FOREIGN KEY (section_id) REFERENCES sections (id)
);

CREATE INDEX ix_students_section ON students (section_id);

CREATE TABLE subjects (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    subject_code VARCHAR(40)  NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    program_id   BIGINT       NULL,
    active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subjects_code UNIQUE (subject_code),
    CONSTRAINT fk_subjects_program FOREIGN KEY (program_id) REFERENCES programs (id)
);

CREATE INDEX ix_subjects_program ON subjects (program_id);

CREATE TABLE faculties (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    faculty_name  VARCHAR(150) NOT NULL,
    faculty_code  VARCHAR(40)  NULL,
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_faculties_code UNIQUE (faculty_code)
);

CREATE TABLE rooms (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    room_code  VARCHAR(40)  NOT NULL,
    building   VARCHAR(100) NULL,
    floor      VARCHAR(20)  NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_code UNIQUE (room_code)
);

CREATE TABLE app_users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    username            VARCHAR(60)  NOT NULL,
    password_hash       VARCHAR(100) NOT NULL,
    display_name        VARCHAR(120) NULL,
    role                VARCHAR(20)  NOT NULL,
    active              TINYINT(1)   NOT NULL DEFAULT 1,
    last_login_at       DATETIME     NULL,
    password_changed_at DATETIME     NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_users_username UNIQUE (username)
);

CREATE TABLE timetables (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    section_id       BIGINT       NOT NULL,
    academic_term_id BIGINT       NOT NULL,
    effective_from   DATE         NOT NULL,
    effective_to     DATE         NULL,
    status           VARCHAR(20)  NOT NULL,
    source_filename  VARCHAR(255) NULL,
    version          INT          NOT NULL,
    created_by       BIGINT       NULL,
    published_at     DATETIME     NULL,
    published_by     BIGINT       NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_timetables_section_version UNIQUE (section_id, version),
    CONSTRAINT fk_timetables_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_timetables_academic_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms (id),
    CONSTRAINT ck_timetables_status CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'PUBLISHED', 'SUPERSEDED'))
);

CREATE INDEX ix_timetables_lookup ON timetables (section_id, status, effective_from, effective_to);
CREATE INDEX ix_timetables_term ON timetables (academic_term_id);

CREATE TABLE timetable_entries (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    timetable_id        BIGINT        NOT NULL,
    section_id          BIGINT        NOT NULL,
    subject_id          BIGINT        NULL,
    faculty_id          BIGINT        NULL,
    room_id             BIGINT        NULL,
    day_of_week         TINYINT       NOT NULL,
    start_time          TIME          NOT NULL,
    end_time            TIME          NOT NULL,
    entry_type          VARCHAR(20)   NOT NULL,
    raw_source_text     VARCHAR(1000) NULL,
    verification_status VARCHAR(20)   NOT NULL,
    source_page_number  INT           NULL,
    created_at          DATETIME      NOT NULL,
    updated_at          DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_entries_timetable FOREIGN KEY (timetable_id) REFERENCES timetables (id),
    CONSTRAINT fk_entries_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_entries_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_entries_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id),
    CONSTRAINT fk_entries_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_entries_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_entries_type CHECK (entry_type IN ('CLASS', 'BREAK', 'OTHER')),
    CONSTRAINT ck_entries_interval CHECK (end_time > start_time)
);

CREATE INDEX ix_entries_timetable_day ON timetable_entries (timetable_id, day_of_week, start_time);
CREATE INDEX ix_entries_section_day ON timetable_entries (section_id, day_of_week);

CREATE TABLE import_jobs (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    filename                VARCHAR(255) NOT NULL,
    stored_filename         VARCHAR(255) NOT NULL,
    status                  VARCHAR(20)  NOT NULL,
    uploaded_by             BIGINT       NULL,
    uploaded_by_username    VARCHAR(60)  NULL,
    started_at              DATETIME     NULL,
    completed_at            DATETIME     NULL,
    error_summary           VARCHAR(2000) NULL,
    total_pages             INT          NULL,
    pages_needing_ocr       INT          NULL,
    ocr_service_used        TINYINT(1)   NOT NULL DEFAULT 0,
    result_timetable_id     BIGINT       NULL,
    file_size_bytes         BIGINT       NULL,
    created_at              DATETIME     NOT NULL,
    updated_at              DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_import_jobs_status CHECK (status IN
        ('PENDING', 'EXTRACTING', 'PARSING', 'OCR_PENDING', 'AWAITING_REVIEW',
         'APPROVED', 'PUBLISHED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX ix_import_jobs_status ON import_jobs (status);

CREATE TABLE import_candidates (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    import_job_id     BIGINT         NOT NULL,
    page_number       INT            NOT NULL,
    raw_text          VARCHAR(2000)  NULL,
    extracted_fields  TEXT           NULL,
    confidence        DECIMAL(5,4)   NULL,
    review_status     VARCHAR(20)    NOT NULL,
    reviewer_notes    VARCHAR(1000)  NULL,
    reviewed_by       BIGINT         NULL,
    section_label     VARCHAR(120)   NULL,
    section_id        BIGINT         NULL,
    subject_label     VARCHAR(300)   NULL,
    subject_id        BIGINT         NULL,
    faculty_label     VARCHAR(300)   NULL,
    faculty_id        BIGINT         NULL,
    room_label        VARCHAR(120)   NULL,
    room_id           BIGINT         NULL,
    day_of_week       TINYINT        NULL,
    start_time        TIME           NULL,
    end_time          TIME           NULL,
    entry_type        VARCHAR(20)    NULL,
    validation_errors VARCHAR(1000)  NULL,
    created_at        DATETIME       NOT NULL,
    updated_at        DATETIME       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_candidates_job FOREIGN KEY (import_job_id) REFERENCES import_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_candidates_section FOREIGN KEY (section_id) REFERENCES sections (id),
    CONSTRAINT fk_candidates_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_candidates_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id),
    CONSTRAINT fk_candidates_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT ck_candidates_review CHECK (review_status IN ('PENDING', 'APPROVED', 'EDITED', 'REJECTED'))
);

CREATE INDEX ix_candidates_job ON import_candidates (import_job_id, page_number);

CREATE TABLE audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NULL,
    username    VARCHAR(60)   NULL,
    action      VARCHAR(60)   NOT NULL,
    entity_type VARCHAR(60)   NULL,
    entity_id   BIGINT        NULL,
    occurred_at DATETIME      NOT NULL,
    details     VARCHAR(2000) NULL,
    ip_address  VARCHAR(60)   NULL,
    created_at  DATETIME      NOT NULL,
    updated_at  DATETIME      NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_audit_user ON audit_logs (user_id);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_time ON audit_logs (occurred_at);