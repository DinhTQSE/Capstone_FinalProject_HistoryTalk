-- Add indexes for deleted_at columns (created CONCURRENTLY to avoid locks)
CREATE INDEX idx_character_deleted_at ON "character"(deleted_at);
CREATE INDEX idx_character_doc_deleted_at ON character_document(deleted_at);
CREATE INDEX idx_context_deleted_at ON historical_context(deleted_at);
CREATE INDEX idx_context_doc_deleted_at ON historical_context_document(deleted_at);
CREATE INDEX idx_chat_session_deleted_at ON chat_session(deleted_at);
CREATE INDEX idx_message_deleted_at ON message(deleted_at);
CREATE INDEX idx_quiz_result_deleted_at ON quiz_result(deleted_at);
CREATE INDEX idx_quiz_answer_detail_deleted_at ON quiz_answer_detail(deleted_at);
CREATE INDEX idx_quiz_session_deleted_at ON quiz_session(deleted_at);
CREATE INDEX idx_user_deleted_at ON "user"(deleted_at);
