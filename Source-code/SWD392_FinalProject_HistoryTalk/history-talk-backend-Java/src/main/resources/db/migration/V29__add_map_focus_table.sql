-- =============================================
-- V29: Add map_focus table for historical context camera viewpoints
-- =============================================

CREATE TABLE IF NOT EXISTS historical_schema.map_focus (
    focus_id        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id      UUID         NOT NULL,
    created_by      UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    latitude        FLOAT8       NOT NULL,
    longitude       FLOAT8       NOT NULL,
    zoom_level      FLOAT8       NOT NULL DEFAULT 8.0,
    order_index     INT4         NOT NULL DEFAULT 0,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT fk_map_focus_context FOREIGN KEY (context_id) REFERENCES historical_schema.historical_context(context_id),
    CONSTRAINT fk_map_focus_creator FOREIGN KEY (created_by)  REFERENCES historical_schema."user"(uid)
);

CREATE INDEX IF NOT EXISTS idx_map_focus_context ON historical_schema.map_focus(context_id, order_index);
CREATE INDEX IF NOT EXISTS idx_map_focus_active  ON historical_schema.map_focus(context_id, is_active, deleted_at);
