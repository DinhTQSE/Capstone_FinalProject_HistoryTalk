-- =============================================
-- chat_session.context_id becomes optional
-- =============================================
-- A chat may run without a historical context: the context is not sent, or it was unpublished/hidden after the
-- character was published. ChatSession already maps the column as nullable and the AI payload falls back to no
-- context, but the NOT NULL constraint turned these cases into HTTP 500.

ALTER TABLE chat_session
    ALTER COLUMN context_id DROP NOT NULL;
