-- Clean baseline for HistoryTalk.
-- This migration is intended to run after dropping historical_schema.
-- Keep UUID as the PK/FK data type.

CREATE SCHEMA IF NOT EXISTS historical_schema;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_id UUID NULL,
    user_name VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token INT NOT NULL DEFAULT 0,
    last_active_date TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS historical_schema.historical_context (
    context_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by UUID NOT NULL,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    era VARCHAR(50),
    category VARCHAR(50),
    year INT,
    start_year INT,
    end_year INT,
    is_bc BOOLEAN NOT NULL DEFAULT FALSE,
    location VARCHAR(255),
    image_url VARCHAR(500),
    video_url VARCHAR(500),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_historical_context_created_by
        FOREIGN KEY (created_by) REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema."character" (
    character_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    title VARCHAR(150),
    background TEXT NOT NULL,
    image_url VARCHAR(255),
    born_date DATE,
    death_date DATE,
    personality VARCHAR(500),
    is_published BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_character_created_by
        FOREIGN KEY (created_by) REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.context_character_mapping (
    mapping_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id UUID NOT NULL,
    character_id UUID NOT NULL,
    CONSTRAINT uk_context_character_mapping UNIQUE (context_id, character_id),
    CONSTRAINT fk_ccm_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE,
    CONSTRAINT fk_ccm_character FOREIGN KEY (character_id)
        REFERENCES historical_schema."character" (character_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.document (
    doc_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_by UUID NOT NULL,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_url VARCHAR(500),
    content TEXT,
    document_type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_document_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.vector_chunk (
    chunk_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    content TEXT NOT NULL,
    embedding DOUBLE PRECISION[],
    sequence_number INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_vector_chunk_document FOREIGN KEY (doc_id)
        REFERENCES historical_schema.document (doc_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.chat_session (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uid UUID NOT NULL,
    context_id UUID NOT NULL,
    character_id UUID NOT NULL,
    title VARCHAR(255) DEFAULT '',
    last_message_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_chat_session_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid),
    CONSTRAINT fk_chat_session_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_session_character FOREIGN KEY (character_id)
        REFERENCES historical_schema."character" (character_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.message (
    message_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    content TEXT NOT NULL,
    is_from_ai BOOLEAN NOT NULL DEFAULT FALSE,
    role VARCHAR(20),
    suggested_questions TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES historical_schema.chat_session (session_id) ON DELETE CASCADE
);

-- Quiz tables are kept as current implementation tables only.
-- Quiz business refactor is explicitly out of scope for this ERD alignment pass.
CREATE TABLE IF NOT EXISTS historical_schema.quiz (
    quiz_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    grade INT,
    chapter_number INT,
    chapter_title VARCHAR(255),
    era VARCHAR(50),
    duration_seconds INT,
    play_count INT DEFAULT 0,
    rating DOUBLE PRECISION DEFAULT 0.0,
    context_id UUID NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_created_by FOREIGN KEY (created_by)
        REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.question (
    question_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL,
    content TEXT NOT NULL,
    options TEXT,
    correct_answer INT,
    order_index INT,
    explanation TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id)
        REFERENCES historical_schema.quiz (quiz_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.quiz_session (
    session_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL,
    uid UUID NOT NULL,
    limited_time INT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    score DOUBLE PRECISION,
    is_submitted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_session_quiz FOREIGN KEY (quiz_id)
        REFERENCES historical_schema.quiz (quiz_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_session_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.quiz_answer_detail (
    detail_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL,
    session_id UUID NOT NULL,
    selected_option INT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_answer_detail_question FOREIGN KEY (question_id)
        REFERENCES historical_schema.question (question_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answer_detail_session FOREIGN KEY (session_id)
        REFERENCES historical_schema.quiz_session (session_id) ON DELETE CASCADE
);

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

INSERT INTO historical_schema."user" (
    uid,
    user_name,
    email,
    password,
    role,
    token,
    is_active,
    created_at,
    updated_at,
    deleted_at
) VALUES
    (
        'ae2352c6-704b-4125-978f-4ef4c80c3d4e',
        'CUSTOMER1',
        'customer@historytalk.com',
        '$2a$10$8pvPMT2jfy33l7sab.9yUOg8eR/nhwNt4Gom2k9UHk1ml8VomXY8C',
        'CUSTOMER',
        0,
        TRUE,
        CURRENT_TIMESTAMP,
        NULL,
        NULL
    ),
    (
        '687b57fe-865c-496a-83b3-f83690885dd1',
        'CONTENT_ADMIN1',
        'user@historytalk.com',
        '$2a$10$8pvPMT2jfy33l7sab.9yUOg8eR/nhwNt4Gom2k9UHk1ml8VomXY8C',
        'CONTENT_ADMIN',
        0,
        TRUE,
        CURRENT_TIMESTAMP,
        NULL,
        NULL
    ),
    (
        'a0000000-0000-0000-0000-000000000001',
        'SYSTEM_ADMIN1',
        'admin@historytalk.com',
        '$2a$10$8pvPMT2jfy33l7sab.9yUOg8eR/nhwNt4Gom2k9UHk1ml8VomXY8C',
        'SYSTEM_ADMIN',
        0,
        TRUE,
        CURRENT_TIMESTAMP,
        NULL,
        NULL
    )
ON CONFLICT (email) DO NOTHING;
