-- =========================================================================
-- Flyway V33: Schema for Assignments and Student Submissions (Sprint 6)
-- Author: KhaiVDD
-- =========================================================================

-- 1. Table assignment
CREATE TABLE historical_schema.assignment (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    classroom_id        UUID NOT NULL REFERENCES historical_schema.classroom(id) ON DELETE CASCADE,
    teacher_id          UUID NOT NULL REFERENCES historical_schema."user"(uid),
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    assignment_type     VARCHAR(30) NOT NULL, -- 'CONTEXT_READING', 'AI_CHAT_ROLEPLAY', 'QUIZ_TEST'
    target_id           UUID NOT NULL,        -- context_id, character_id, or quiz_id
    deadline            TIMESTAMP WITH TIME ZONE NOT NULL,
    min_requirement     INT DEFAULT 5,        -- Số tin nhắn chat tối thiểu hoặc số giây đọc tối thiểu để đạt 10 điểm
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'CANCELLED', 'ARCHIVED'
    created_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at          TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_assignment_classroom_id ON historical_schema.assignment(classroom_id);
CREATE INDEX idx_assignment_teacher_id ON historical_schema.assignment(teacher_id);
CREATE INDEX idx_assignment_status ON historical_schema.assignment(status);
CREATE INDEX idx_assignment_deadline ON historical_schema.assignment(deadline);

-- 2. Table assignment_submission
CREATE TABLE historical_schema.assignment_submission (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id               UUID NOT NULL REFERENCES historical_schema.assignment(id) ON DELETE CASCADE,
    student_id                  UUID NOT NULL REFERENCES historical_schema."user"(uid),
    status                      VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED', 
    -- Statuses: 'NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'LATE_SUBMITTED', 'MISSED', 'EXEMPTED'
    score                       NUMERIC(4, 2) NULL,        -- Thang điểm 10.00 cho bài Quiz/Interactive
    message_count               INT NOT NULL DEFAULT 0,    -- Số lượt đối thoại với AI
    reading_duration_seconds    INT NOT NULL DEFAULT 0,    -- Số giây đọc bối cảnh
    started_at                  TIMESTAMP WITH TIME ZONE NULL,
    submitted_at                TIMESTAMP WITH TIME ZONE NULL,
    created_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at                  TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_assignment_student UNIQUE (assignment_id, student_id)
);

CREATE INDEX idx_submission_assignment_id ON historical_schema.assignment_submission(assignment_id);
CREATE INDEX idx_submission_student_id ON historical_schema.assignment_submission(student_id);
CREATE INDEX idx_submission_status ON historical_schema.assignment_submission(status);
