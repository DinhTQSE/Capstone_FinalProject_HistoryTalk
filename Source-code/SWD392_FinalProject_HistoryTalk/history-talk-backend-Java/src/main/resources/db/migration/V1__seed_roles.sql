-- Create schema and baseline tables (fresh DB without Hibernate DDL)
CREATE SCHEMA IF NOT EXISTS historical_schema;

CREATE TABLE IF NOT EXISTS historical_schema."user" (
    uid UUID PRIMARY KEY,
    user_name VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    deleted_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS historical_schema.historical_context (
    context_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NOT NULL,
    era VARCHAR(50),
    category VARCHAR(50),
    year INT,
    start_year INT,
    end_year INT,
    before_tcn BOOLEAN NOT NULL DEFAULT FALSE,
    location VARCHAR(255),
    image_url VARCHAR(500),
    video_url VARCHAR(500),
    is_draft BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_historical_context_created_by
        FOREIGN KEY (created_by) REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema."character" (
    character_id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    title VARCHAR(150),
    background TEXT NOT NULL,
    image_url VARCHAR(255),
    personality VARCHAR(500),
    born_date DATE,
    death_date DATE,
    is_draft BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_character_created_by
        FOREIGN KEY (created_by) REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.character_historical_context (
    character_id UUID NOT NULL,
    context_id UUID NOT NULL,
    CONSTRAINT pk_character_historical_context PRIMARY KEY (character_id, context_id),
    CONSTRAINT fk_chc_character FOREIGN KEY (character_id)
        REFERENCES historical_schema."character" (character_id) ON DELETE CASCADE,
    CONSTRAINT fk_chc_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.chat_session (
    session_id UUID PRIMARY KEY,
    uid UUID NOT NULL,
    character_id UUID NOT NULL,
    context_id UUID NOT NULL,
    title VARCHAR(255) DEFAULT '',
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_chat_session_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid),
    CONSTRAINT fk_chat_session_character FOREIGN KEY (character_id)
        REFERENCES historical_schema."character" (character_id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_session_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.message (
    message_id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    is_from_ai BOOLEAN NOT NULL,
    role VARCHAR(20),
    session_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    suggested_questions TEXT,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES historical_schema.chat_session (session_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.quiz (
    quiz_id UUID PRIMARY KEY,
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
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_context FOREIGN KEY (context_id)
        REFERENCES historical_schema.historical_context (context_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_created_by FOREIGN KEY (created_by)
        REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.question (
    question_id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    options TEXT,
    correct_answer INT,
    order_index INT,
    explanation TEXT,
    quiz_id UUID NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id)
        REFERENCES historical_schema.quiz (quiz_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.quiz_session (
    session_id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL,
    uid UUID NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    limited_time INT,
    score DOUBLE PRECISION,
    is_submitted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_session_quiz FOREIGN KEY (quiz_id)
        REFERENCES historical_schema.quiz (quiz_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_session_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid)
);

CREATE TABLE IF NOT EXISTS historical_schema.quiz_answer_detail (
    detail_id UUID PRIMARY KEY,
    selected_option INT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    session_id UUID NOT NULL,
    question_id UUID NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_quiz_answer_detail_session FOREIGN KEY (session_id)
        REFERENCES historical_schema.quiz_session (session_id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answer_detail_question FOREIGN KEY (question_id)
        REFERENCES historical_schema.question (question_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS historical_schema.document (
    doc_id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_url VARCHAR(500),
    content TEXT,
    document_type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    uploaded_by UUID NOT NULL,
    uploaded_date TIMESTAMP NOT NULL,
    updated_date TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_document_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES historical_schema."user" (uid)
);

CREATE INDEX IF NOT EXISTS idx_user_role ON historical_schema."user" (role);

INSERT INTO historical_schema."user" (uid, email, password, role, user_name)
VALUES
    (
        gen_random_uuid(),
        'customer@historytalk.com',
        '$2a$10$8pvPMT2jfy33l7sab.9yUOg8eR/nhwNt4Gom2k9UHk1ml8VomXY8C',
        'CUSTOMER',
        'CUSTOMER1'
     ),
    (
        gen_random_uuid(),
        'user@historytalk.com',
        '$2a$10$8pvPMT2jfy33l7sab.9yUOg8eR/nhwNt4Gom2k9UHk1ml8VomXY8C',
        'STAFF',
        'STAFF1'
    )
    ON CONFLICT (email) DO NOTHING;