-- =============================================================================
-- HistoryTalk - Consolidated PostgreSQL DDL Database Schema
-- Schema Name: historical_schema
-- Engine: PostgreSQL 15+ (with pgcrypto extension)
-- Compiled from Flyway Migrations V1 to V25
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS historical_schema;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
-- 2. TABLE: "user" (User Management & Auth)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id                     UUID REFERENCES historical_schema.tier(tier_id) ON DELETE SET NULL,
    user_name                   VARCHAR(100) NOT NULL UNIQUE,
    email                       VARCHAR(100) NOT NULL UNIQUE,
    password                    VARCHAR(255) NOT NULL,
    role                        VARCHAR(50) NOT NULL, -- CUSTOMER | CONTENT_ADMIN | SYSTEM_ADMIN
    token                       INT NOT NULL DEFAULT 0,
    full_name                   VARCHAR(100),
    avatar_url                  VARCHAR(500),
    phone_number                VARCHAR(20),
    last_active_date            TIMESTAMP,
    last_token_reset_at         TIMESTAMP,
    password_reset_token        VARCHAR(255),
    password_reset_token_expiry TIMESTAMP,
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP,
    deleted_at                  TIMESTAMP
);

-- =============================================================================
-- 3. TABLE: historical_context (Historical Events & Contexts)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.historical_context (
    context_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by      UUID NOT NULL REFERENCES historical_schema."user"(uid),
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     TEXT NOT NULL,
    era             VARCHAR(50),
    category        VARCHAR(50),
    year            INT,
    start_year      INT,
    end_year        INT,
    is_bc           BOOLEAN NOT NULL DEFAULT FALSE,
    location        VARCHAR(255),
    image_url       VARCHAR(500),
    video_url       VARCHAR(500),
    is_published    BOOLEAN NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 4. TABLE: "character" (Historical Characters / Persona RAG)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema."character" (
    character_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    name                VARCHAR(100) NOT NULL,
    title               VARCHAR(150),
    background          TEXT NOT NULL,
    image_url           VARCHAR(255),
    character_model_url VARCHAR(500),
    born_date           DATE,
    death_date          DATE,
    personality         VARCHAR(500),
    is_published        BOOLEAN NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 5. TABLE: context_character_mapping (Many-to-Many Bridge)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.context_character_mapping (
    mapping_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id   UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    character_id UUID NOT NULL REFERENCES historical_schema."character"(character_id) ON DELETE CASCADE,
    CONSTRAINT uk_context_character_mapping UNIQUE (context_id, character_id)
);

-- =============================================================================
-- 6. TABLE: document (Uploaded RAG Source Documents)
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
    status          VARCHAR(50) NOT NULL DEFAULT 'COMPLETED', -- PENDING | PROCESSING | COMPLETED | FAILED
    media_type      VARCHAR(50),
    media_url       VARCHAR(500),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 7. TABLE: vector_chunk (Relational metadata for RAG Chunks)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.vector_chunk (
    chunk_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id          UUID NOT NULL REFERENCES historical_schema.document(doc_id) ON DELETE CASCADE,
    entity_id       UUID NOT NULL,
    content         TEXT NOT NULL,
    embedding       DOUBLE PRECISION[],
    sequence_number INT NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 8. TABLE: chat_session (AI Chat Sessions)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.chat_session (
    session_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid),
    context_id      UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    character_id    UUID NOT NULL REFERENCES historical_schema."character"(character_id) ON DELETE CASCADE,
    title           VARCHAR(255) DEFAULT '',
    last_message_at TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 9. TABLE: message (Chat Messages & AI Responses)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.message (
    message_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID NOT NULL REFERENCES historical_schema.chat_session(session_id) ON DELETE CASCADE,
    content             TEXT NOT NULL,
    is_from_ai          BOOLEAN NOT NULL DEFAULT FALSE,
    role                VARCHAR(20), -- user | model
    message_type        VARCHAR(20) DEFAULT 'CHAT',
    token               INT NOT NULL DEFAULT 0,
    suggested_questions TEXT,
    quotes              TEXT,
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 10. TABLE: quiz (Gamification Quiz Modules)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz (
    quiz_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id          UUID NOT NULL REFERENCES historical_schema.historical_context(context_id) ON DELETE CASCADE,
    created_by          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    grade               INT,
    chapter_number      INT,
    chapter_title       VARCHAR(255),
    era                 VARCHAR(50),
    duration_seconds    INT,
    play_count          INT DEFAULT 0,
    rating              DOUBLE PRECISION DEFAULT 0.0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP
);

-- =============================================================================
-- 11. TABLE: question (Quiz Questions)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.question (
    question_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id         UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    options         TEXT,
    correct_answer  INT,
    order_index     INT,
    explanation     TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 12. TABLE: quiz_session (User Quiz Attempt Session)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_session (
    session_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id         UUID NOT NULL REFERENCES historical_schema.quiz(quiz_id) ON DELETE CASCADE,
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid),
    limited_time    INT,
    start_time      TIMESTAMP,
    end_time        TIMESTAMP,
    score           DOUBLE PRECISION,
    is_submitted    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 13. TABLE: quiz_answer_detail (Per Question Submission Detail)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.quiz_answer_detail (
    detail_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id     UUID NOT NULL REFERENCES historical_schema.question(question_id) ON DELETE CASCADE,
    session_id      UUID NOT NULL REFERENCES historical_schema.quiz_session(session_id) ON DELETE CASCADE,
    selected_option INT NOT NULL,
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 14. TABLE: user_tier (User Subscriptions Active Log)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.user_tier (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid) ON DELETE CASCADE,
    tier_id         UUID NOT NULL REFERENCES historical_schema.tier(tier_id),
    start_time      TIMESTAMP NOT NULL,
    end_time        TIMESTAMP NOT NULL,
    is_active_tier  BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_at       TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 15. TABLE: payment_order (PayOS Checkout Orders)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_order (
    order_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             UUID NOT NULL REFERENCES historical_schema."user"(uid),
    tier_id         UUID NOT NULL REFERENCES historical_schema.tier(tier_id),
    order_code      BIGINT NOT NULL UNIQUE,
    amount          INT NOT NULL,
    payment_link_id VARCHAR(255),
    checkout_url    VARCHAR(500),
    qr_code         TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | CANCELLED | EXPIRED
    fulfilled       BOOLEAN NOT NULL DEFAULT FALSE,
    fulfilled_at    TIMESTAMP,
    paid_at         TIMESTAMP,
    expired_at      TIMESTAMP,
    description     TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 16. TABLE: payment_transaction (PayOS Webhook Log)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_transaction (
    transaction_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES historical_schema.payment_order(order_id) ON DELETE CASCADE,
    amount          INT NOT NULL,
    payment_link_id VARCHAR(255),
    payload         TEXT, -- Raw JSON webhook payload
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    transaction_date TIMESTAMP,
    reference       VARCHAR(255),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- =============================================================================
-- 17. TABLE: media_metadata (Polymorphic Media Attachments)
-- =============================================================================
CREATE TABLE IF NOT EXISTS historical_schema.media_metadata (
    media_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type     VARCHAR(50) NOT NULL, -- CHARACTER | CONTEXT | DOCUMENT
    entity_id       UUID NOT NULL,
    media_type      VARCHAR(50) NOT NULL, -- IMAGE | AUDIO | VIDEO | PDF
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
-- INDEXES & PERFORMANCE OPTIMIZATION
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_user_role ON historical_schema."user" (role);
CREATE INDEX IF NOT EXISTS idx_user_deleted_at ON historical_schema."user" (deleted_at);
CREATE INDEX IF NOT EXISTS idx_context_deleted_at ON historical_schema.historical_context (deleted_at);
CREATE INDEX IF NOT EXISTS idx_context_publish_filter ON historical_schema.historical_context (deleted_at, is_published, created_at);
CREATE INDEX IF NOT EXISTS idx_character_deleted_at ON historical_schema."character" (deleted_at);
CREATE INDEX IF NOT EXISTS idx_character_publish_filter ON historical_schema."character" (deleted_at, is_published, name);
CREATE INDEX IF NOT EXISTS idx_document_deleted_at ON historical_schema.document (deleted_at);
CREATE INDEX IF NOT EXISTS idx_document_entity ON historical_schema.document (entity_type, entity_id, uploaded_at);
CREATE INDEX IF NOT EXISTS idx_vector_chunk_doc ON historical_schema.vector_chunk (doc_id, sequence_number);
CREATE INDEX IF NOT EXISTS idx_chat_session_deleted_at ON historical_schema.chat_session (deleted_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_user ON historical_schema.chat_session (uid, created_at);
CREATE INDEX IF NOT EXISTS idx_message_deleted_at ON historical_schema.message (deleted_at);
CREATE INDEX IF NOT EXISTS idx_message_session ON historical_schema.message (session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_quiz_context_deleted_at ON historical_schema.quiz (context_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_quiz_answer_detail_deleted_at ON historical_schema.quiz_answer_detail (deleted_at);
CREATE INDEX IF NOT EXISTS idx_quiz_session_deleted_at ON historical_schema.quiz_session (deleted_at);
