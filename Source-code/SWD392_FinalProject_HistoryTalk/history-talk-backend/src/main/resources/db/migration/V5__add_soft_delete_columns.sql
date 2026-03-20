-- Disable statement timeout for this migration
SET statement_timeout = 0;

-- Add deleted_at columns (Phase 1 - Character & HistoricalContext modules)
ALTER TABLE "character" ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE character_document ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE historical_context_document ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add deleted_at columns (Phase 2 - Chat & Quiz modules)
ALTER TABLE chat_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE message ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_result ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE quiz_answer_detail ADD COLUMN deleted_at TIMESTAMP NULL;

-- Add deleted_at columns (Phase 3 - Quiz Session & User)
ALTER TABLE quiz_session ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE "user" ADD COLUMN deleted_at TIMESTAMP NULL;

-- Reset statement timeout
RESET statement_timeout;
