-- =============================================================================
-- HistoryTalk - Consolidated PostgreSQL DDL Database Schema
-- Schema Name: historical_schema
-- Engine: PostgreSQL 15+ (with pgcrypto & pgvector extensions)
-- Compiled from Flyway Migrations V1 to V38 + Supabase Vector Store (32 Tables)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS historical_schema;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- 1. TABLE: tier (Subscription Tier Specs)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.tier (
    tier_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(50) NOT NULL,          -- 'free' | 'plus' | 'pro'
    amount          INT NOT NULL DEFAULT 0,        -- Price in VND
    no_month        INT NOT NULL DEFAULT 1,        -- Duration in months
    limited_token   INT NOT NULL DEFAULT 0,        -- Monthly token allowance
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 2. TABLE: school (B2B Multi-Tenant School Organization - V30, V38)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.school (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                            VARCHAR(255) NOT NULL,
    school_code                     VARCHAR(50) NOT NULL UNIQUE,
    address                         VARCHAR(500),
    contact_email                   VARCHAR(100) NOT NULL,
    contact_phone                   VARCHAR(20),
    package_type                    VARCHAR(50) NOT NULL, -- BASIC | STANDARD | ENTERPRISE
    total_school_token_quota        INT NOT NULL DEFAULT 0,
    unallocated_token_quota         INT NOT NULL DEFAULT 0,
    default_daily_student_quota     INT NOT NULL DEFAULT 1000,
    subscription_start_date         DATE,
    subscription_end_date           DATE,
    renewed_for_next_year           BOOLEAN NOT NULL DEFAULT FALSE,
    next_package_type               VARCHAR(50),
    local_history_policy_accepted   BOOLEAN NOT NULL DEFAULT FALSE,
    status                          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | SUSPENDED | EXPIRED
    created_at                      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                      TIMESTAMP,
    deleted_at                      TIMESTAMP
);

-- =============================================================================
-- 3. TABLE: "user" (User Management, RBAC & Daily Token Tracking - V1..V38)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                   UUID REFERENCES historical_schema.school(id) ON DELETE SET NULL,
    user_name                   VARCHAR(100) NOT NULL UNIQUE,
    email                       VARCHAR(100) NOT NULL UNIQUE,
    password                    VARCHAR(255) NOT NULL,
    role                        VARCHAR(50) NOT NULL, -- CUSTOMER | STUDENT | TEACHER | SCHOOL_ADMIN | CONTENT_ADMIN | SYSTEM_ADMIN
    account_status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | SUSPENDED
    deactivated_reason          VARCHAR(255),
    student_code                VARCHAR(50),
    subject_department          VARCHAR(100),
    must_change_password        BOOLEAN NOT NULL DEFAULT FALSE,
    token                       INT NOT NULL DEFAULT 0,
    daily_tokens_used           INT NOT NULL DEFAULT 0,
    daily_bonus_tokens          INT NOT NULL DEFAULT 0,
    full_name                   VARCHAR(150),
    dob                         DATE,
    gender                      VARCHAR(20),
    phone_number                VARCHAR(20),
    address                     VARCHAR(500),
    avatar_url                  VARCHAR(500),
    last_active_date            TIMESTAMP,
    last_token_reset_at         TIMESTAMP(6),
    password_reset_token        VARCHAR(255),
    password_reset_token_expiry TIMESTAMP,
    password_reset_token_hash   VARCHAR(64),
    password_reset_expires_at   TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP,
    deleted_at                  TIMESTAMP
);

-- =============================================================================
-- 4. TABLE: classroom (B2B School Classrooms - V31, V38)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.classroom (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES historical_schema.school(id) ON DELETE CASCADE,
    teacher_id          UUID REFERENCES historical_schema."user"(uid) ON DELETE SET NULL,
    class_name          VARCHAR(100) NOT NULL,
    class_code          VARCHAR(50) NOT NULL UNIQUE,
    grade_level         INT,
    academic_year       INT NOT NULL,
    daily_token_quota   INT,
    description         VARCHAR(500),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 5. TABLE: class_student (Student-Classroom Enrollment - V31)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.class_student (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id    UUID NOT NULL REFERENCES historical_schema.classroom(id) ON DELETE CASCADE,
    student_id      UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    joined_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_class_student UNIQUE (classroom_id, student_id)
);

-- =============================================================================
-- 6. TABLE: token_boost_request (Daily Student Token Boost Workflow - V38)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.token_boost_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           UUID NOT NULL REFERENCES historical_schema.school(id),
    classroom_id        UUID NOT NULL REFERENCES historical_schema.classroom(id),
    student_uid         UUID NOT NULL REFERENCES historical_schema."user"(uid),
    requested_tokens    INT NOT NULL,
    approved_tokens     INT,
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | EXPIRED
    reviewed_by_uid     UUID REFERENCES historical_schema."user"(uid),
    review_note         VARCHAR(500),
    request_date        DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP
);

-- =============================================================================
-- 7. TABLE: historical_context (Historical Events, Battle Map & Local Content - V1..V33)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.historical_context (
    context_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    school_id       UUID REFERENCES historical_schema.school(id) ON DELETE SET NULL,
    approved_by     UUID REFERENCES historical_schema."user"(uid) ON DELETE SET NULL,
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT NOT NULL,
    era             VARCHAR(50),
    category        VARCHAR(50),
    year            INT,
    start_year      INT,
    end_year        INT,
    is_bc           BOOLEAN NOT NULL DEFAULT FALSE,
    location        VARCHAR(255),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    image_url       VARCHAR(500),
    video_url       VARCHAR(500),
    battle_map      JSONB,
    content_scope   VARCHAR(20), -- CLASS_ONLY | SCHOOL_WIDE
    content_status  VARCHAR(20), -- DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE
    review_note     TEXT,
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 8. TABLE: "character" (Historical Characters / Persona RAG & Local Content - V1..V33)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema."character" (
    character_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    school_id           UUID REFERENCES historical_schema.school(id) ON DELETE SET NULL,
    approved_by         UUID REFERENCES historical_schema."user"(uid) ON DELETE SET NULL,
    name                VARCHAR(100) NOT NULL,
    title               VARCHAR(150),
    background          TEXT NOT NULL,
    image_url           VARCHAR(255),
    model_url           VARCHAR(500),
    character_model_url VARCHAR(500),
    personality         VARCHAR(500),
    born_year           INT,
    born_month          INT,
    born_day            INT,
    is_born_bc          BOOLEAN,
    death_year          INT,
    death_month         INT,
    death_day           INT,
    is_death_bc         BOOLEAN,
    content_scope       VARCHAR(20), -- CLASS_ONLY | SCHOOL_WIDE
    content_status      VARCHAR(20), -- DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE
    review_note         TEXT,
    is_published        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 9. TABLE: context_character_mapping (Many-to-Many Bridge)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.context_character_mapping (
    mapping_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id   UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    character_id UUID NOT NULL REFERENCES historical_schema."character"(character_id) ON DELETE CASCADE,
    CONSTRAINT uk_context_character_mapping UNIQUE (context_id, character_id)
);

-- =============================================================================
-- 10. TABLE: class_local_content (M2M Classroom <-> Local Content - V33)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.class_local_content (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id    UUID NOT NULL REFERENCES historical_schema.classroom(id) ON DELETE CASCADE,
    entity_id   UUID NOT NULL,
    entity_type VARCHAR(20) NOT NULL, -- 'CONTEXT' | 'CHARACTER'
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID REFERENCES historical_schema."user"(uid),
    CONSTRAINT uq_class_entity UNIQUE (class_id, entity_id, entity_type)
);

-- =============================================================================
-- 11. TABLE: map_pin (Interactive Map Pins & GeoJSON Routes - V27, V28)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.map_pin (
    pin_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id      UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    pin_owner_type  VARCHAR(10) NOT NULL CHECK (pin_owner_type IN ('ADMIN', 'USER')),
    label           VARCHAR(200) NOT NULL,
    description     TEXT,
    pin_type        VARCHAR(50),
    latitude        FLOAT8 NOT NULL,
    longitude       FLOAT8 NOT NULL,
    pin_year        INT4 NOT NULL,
    path_geojson    JSONB NOT NULL DEFAULT '{"type":"LineString","coordinates":[]}'::jsonb,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 12. TABLE: map_focus (Historical Context Camera Viewpoints - V29)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.map_focus (
    focus_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id      UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    latitude        FLOAT8 NOT NULL,
    longitude       FLOAT8 NOT NULL,
    zoom_level      FLOAT8 NOT NULL DEFAULT 8.0,
    order_index     INT4 NOT NULL DEFAULT 0,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 13. TABLE: document (Uploaded RAG Source Documents - V1..V25)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.document (
    doc_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_by     UUID NOT NULL REFERENCES historical_schema."user"(uid),
    entity_id       UUID NOT NULL,
    entity_type     VARCHAR(50) NOT NULL, -- CHARACTER | CONTEXT
    title           VARCHAR(255) NOT NULL,
    file_url        VARCHAR(500),
    content         TEXT,
    document_type   VARCHAR(50) NOT NULL DEFAULT 'TEXT', -- TEXT | MARKDOWN | PDF
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT', -- DRAFT | PUBLISHED | ARCHIVED
    media_type      VARCHAR(50),
    media_url       VARCHAR(500),
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 14. TABLE: document_media_metadata (Polymorphic Document/Entity Media - V23, V24)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.document_media_metadata (
    metadata_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id         UUID REFERENCES historical_schema.document(doc_id) ON DELETE CASCADE,
    entity_type         VARCHAR(50) NOT NULL CHECK (entity_type IN ('CHARACTER', 'CONTEXT', 'DOCUMENT', 'USER')),
    entity_id           UUID NOT NULL,
    media_type          VARCHAR(20) NOT NULL,
    file_format         VARCHAR(20),
    file_size_bytes     BIGINT,
    width               INT,
    height              INT,
    storage_path        VARCHAR(500) NOT NULL,
    thumbnail_path      VARCHAR(500),
    extended_metadata   JSONB,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 15. TABLE: media_metadata (Legacy Polymorphic Media Attachments)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.media_metadata (
    media_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID NOT NULL,
    media_type      VARCHAR(50) NOT NULL,
    media_url       VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255),
    mime_type       VARCHAR(100),
    file_size       BIGINT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 16. TABLE: vector_chunk (Supabase PgVector Semantic Chunks for AI RAG)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.vector_chunk (
    chunk_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id          UUID NOT NULL REFERENCES historical_schema.document(doc_id) ON DELETE CASCADE,
    entity_id       UUID NOT NULL,
    content         TEXT NOT NULL,
    embedding       vector(768),
    sequence_number INT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 17. TABLE: chat_session (AI Roleplay Chat Sessions)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.chat_session (
    session_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid),
    context_id      UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    character_id    UUID NOT NULL REFERENCES historical_schema."character"(character_id) ON DELETE CASCADE,
    title           VARCHAR(255) DEFAULT '',
    last_message_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 18. TABLE: message (Chat Messages & Grounded AI Responses - V1..V22)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.message (
    message_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES historical_schema.chat_session(session_id) ON DELETE CASCADE,
    content             TEXT NOT NULL,
    is_from_ai          BOOLEAN NOT NULL DEFAULT FALSE,
    message_type        VARCHAR(50) DEFAULT 'TEXT', -- TEXT | VOICE
    token               INT NOT NULL DEFAULT 0,
    suggested_questions TEXT,
    quotes              TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 19. TABLE: quiz (Historical Assessment Quizzes - V1..V13)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz (
    quiz_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id      UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    created_by      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title           VARCHAR(255) NOT NULL,
    level           VARCHAR(255) NOT NULL DEFAULT 'EASY', -- EASY | MEDIUM | HARD
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 20. TABLE: question (Question Bank with Polymorphic JSON Payload - V34, V35)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.question (
    question_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id         UUID REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    question_type   VARCHAR(40) NOT NULL DEFAULT 'MULTIPLE_CHOICE' CHECK (question_type IN ('MULTIPLE_CHOICE', 'FILL_IN_BLANK', 'MATCHING')),
    question_data   TEXT,
    explanation     TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 21. TABLE: quiz_question (Many-to-Many Quiz <-> Question Bank Link - V34)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_question (
    quiz_id     UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES historical_schema.question(question_id) ON DELETE CASCADE,
    order_index INT NOT NULL DEFAULT 0,
    points      NUMERIC(8,2) NOT NULL DEFAULT 1.00,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (quiz_id, question_id)
);

-- =============================================================================
-- 22. TABLE: quiz_session (User Quiz Attempt Session - V1..V13)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_session (
    session_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id         UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid),
    limited_time    INT,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    score           DOUBLE PRECISION,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 23. TABLE: quiz_answer_detail (Per-Question Submission & Partial Score - V34, V35)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_answer_detail (
    detail_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES historical_schema.question(question_id) ON DELETE CASCADE,
    session_id      UUID NOT NULL REFERENCES historical_schema.quiz_session(session_id) ON DELETE CASCADE,
    answer_data     TEXT,
    score_earned    NUMERIC(8,2),
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 24. TABLE: quiz_rating (User Quiz Star Ratings)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_rating (
    rating_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id         UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    rating_value    INT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_rating_user UNIQUE (quiz_id, user_id)
);

-- =============================================================================
-- 25. TABLE: question_report (User Question Error Reports)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.question_report (
    report_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES historical_schema.question(question_id) ON DELETE CASCADE,
    quiz_id         UUID REFERENCES historical_schema.quiz(quiz_id) ON DELETE SET NULL,
    user_id         UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    reason          TEXT,
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============================================================================
-- 26. TABLE: assignment (Classroom Learning Assignments - V33)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.assignment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id        UUID NOT NULL REFERENCES historical_schema.classroom(id) ON DELETE CASCADE,
    teacher_id          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    assignment_type     VARCHAR(30) NOT NULL, -- CONTEXT_READING | AI_CHAT_ROLEPLAY | QUIZ_TEST
    target_id           UUID NOT NULL,
    deadline            TIMESTAMP WITH TIME ZONE NOT NULL,
    min_requirement     INT DEFAULT 5,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | CANCELLED | ARCHIVED
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE
);

-- =============================================================================
-- 27. TABLE: assignment_submission (Student Assignment Submissions - V33, V37)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.assignment_submission (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id               UUID NOT NULL REFERENCES historical_schema.assignment(id) ON DELETE CASCADE,
    student_id                  UUID NOT NULL REFERENCES historical_schema."user"(uid),
    quiz_session_id             UUID REFERENCES historical_schema.quiz_session(session_id) ON DELETE SET NULL,
    chat_session_id             UUID REFERENCES historical_schema.chat_session(session_id) ON DELETE SET NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED | IN_PROGRESS | SUBMITTED | LATE_SUBMITTED | MISSED | EXEMPTED
    score                       NUMERIC(4, 2),
    message_count               INT NOT NULL DEFAULT 0,
    reading_duration_seconds    INT NOT NULL DEFAULT 0,
    started_at                  TIMESTAMP WITH TIME ZONE,
    submitted_at                TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_assignment_student UNIQUE (assignment_id, student_id)
);

-- =============================================================================
-- 28. TABLE: payment_order (PayOS Checkout Orders & Fulfillment - V10, V11, V21)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_order (
    order_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid                     UUID NOT NULL REFERENCES historical_schema."user"(uid),
    tier_id                 UUID NOT NULL REFERENCES historical_schema.tier(tier_id),
    order_code              BIGINT NOT NULL UNIQUE,
    amount                  INT NOT NULL,
    payment_link_id         VARCHAR(255),
    checkout_url            VARCHAR(500),
    qr_code                 TEXT,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | CANCELLED | EXPIRED
    fulfillment_status      VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | FULFILLED | FAILED
    fulfilled_at            TIMESTAMP,
    fulfillment_attempts    INT NOT NULL DEFAULT 0,
    fulfillment_error       TEXT,
    fulfillment_locked_at   TIMESTAMP,
    paid_at                 TIMESTAMP,
    expired_at              TIMESTAMP,
    description             TEXT,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP,
    deleted_at              TIMESTAMP
);

-- =============================================================================
-- 29. TABLE: user_tier (User Subscription Entitlement Ledger - V10, V18, V21)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.user_tier (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid                 UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    tier_id             UUID NOT NULL REFERENCES historical_schema.tier(tier_id),
    payment_order_id    UUID REFERENCES historical_schema.payment_order(order_id) ON DELETE SET NULL,
    start_time          TIMESTAMP NOT NULL,
    end_time            TIMESTAMP NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 30. TABLE: payment_transaction (PayOS Webhook / IPN Event Log - V10, V21)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_transaction (
    transaction_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL REFERENCES historical_schema.payment_order(order_id) ON DELETE CASCADE,
    amount              INT NOT NULL,
    payment_link_id     VARCHAR(255),
    payload             TEXT,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | SUCCESS | FAILED
    transaction_date    TIMESTAMP,
    reference           VARCHAR(255),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 31. TABLE: daily_quest (Gamification Daily Quests - V26)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.daily_quest (
    quest_id        VARCHAR(50) PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    target          INT NOT NULL,
    reward_tokens   INT NOT NULL,
    order_index     INT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

-- =============================================================================
-- 32. TABLE: user_quest_progress & daily_check_in (Gamification Progress - V26)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.user_quest_progress (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uid        UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    date            DATE NOT NULL,
    quest_id        VARCHAR(50) NOT NULL REFERENCES historical_schema.daily_quest(quest_id),
    progress        INT NOT NULL DEFAULT 0,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    claimed         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_user_quest_date UNIQUE (user_uid, quest_id, date)
);

CREATE TABLE IF NOT EXISTS historical_schema.daily_check_in (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_uid        UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    date            DATE NOT NULL,
    streak_count    INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_user_checkin_date UNIQUE (user_uid, date)
);

-- =============================================================================
-- INDEXES & PERFORMANCE OPTIMIZATION
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_user_role ON historical_schema."user" (role);
CREATE INDEX IF NOT EXISTS idx_user_deleted_at ON historical_schema."user" (deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_school_id ON historical_schema."user" (school_id);
CREATE INDEX IF NOT EXISTS idx_user_school_student_code ON historical_schema."user" (school_id, student_code);
CREATE INDEX IF NOT EXISTS idx_classroom_school_id ON historical_schema.classroom (school_id);
CREATE INDEX IF NOT EXISTS idx_classroom_teacher_id ON historical_schema.classroom (teacher_id);
CREATE INDEX IF NOT EXISTS idx_class_student_classroom ON historical_schema.class_student (classroom_id);
CREATE INDEX IF NOT EXISTS idx_class_student_student ON historical_schema.class_student (student_id);
CREATE INDEX IF NOT EXISTS idx_token_boost_req_classroom_status ON historical_schema.token_boost_request (classroom_id, status, request_date);
CREATE INDEX IF NOT EXISTS idx_context_deleted_at ON historical_schema.historical_context (deleted_at);
CREATE INDEX IF NOT EXISTS idx_context_publish_filter ON historical_schema.historical_context (deleted_at, is_published, created_at);
CREATE INDEX IF NOT EXISTS idx_character_deleted_at ON historical_schema."character" (deleted_at);
CREATE INDEX IF NOT EXISTS idx_character_publish_filter ON historical_schema."character" (deleted_at, is_published, name);
CREATE INDEX IF NOT EXISTS idx_map_pin_context_year ON historical_schema.map_pin (context_id, pin_year);
CREATE INDEX IF NOT EXISTS idx_map_focus_context ON historical_schema.map_focus (context_id, order_index);
CREATE INDEX IF NOT EXISTS idx_document_deleted_at ON historical_schema.document (deleted_at);
CREATE INDEX IF NOT EXISTS idx_document_entity ON historical_schema.document (entity_type, entity_id, uploaded_at);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_doc ON historical_schema.vector_chunk (doc_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_chat_session_deleted_at ON historical_schema.chat_session (deleted_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_user ON historical_schema.chat_session (uid, created_at);
CREATE INDEX IF NOT EXISTS idx_message_deleted_at ON historical_schema.message (deleted_at);
CREATE INDEX IF NOT EXISTS idx_message_session ON historical_schema.message (session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_quiz_context_deleted_at ON historical_schema.quiz (context_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_quiz_question_quiz_order ON historical_schema.quiz_question (quiz_id, order_index);
CREATE INDEX IF NOT EXISTS idx_quiz_answer_detail_deleted_at ON historical_schema.quiz_answer_detail (deleted_at);
CREATE INDEX IF NOT EXISTS idx_quiz_session_deleted_at ON historical_schema.quiz_session (deleted_at);
CREATE INDEX IF NOT EXISTS idx_assignment_classroom_id ON historical_schema.assignment (classroom_id);
CREATE INDEX IF NOT EXISTS idx_submission_assignment_id ON historical_schema.assignment_submission (assignment_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_code ON historical_schema.payment_order (order_code);
CREATE INDEX IF NOT EXISTS idx_user_quest_progress_user_date ON historical_schema.user_quest_progress (user_uid, date);
CREATE INDEX IF NOT EXISTS idx_daily_check_in_user_date ON historical_schema.daily_check_in (user_uid, date);
