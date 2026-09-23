-- =============================================
-- V27: Interactive Map Feature
-- Single unified map_pin table with
-- pin_owner_type discriminator (ADMIN | USER)
-- =============================================

CREATE TABLE map_pin (
    pin_id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    context_id      UUID         NOT NULL,
    created_by      UUID         NOT NULL,
    pin_owner_type  VARCHAR(10)  NOT NULL,
    label           VARCHAR(200) NOT NULL,
    description     TEXT,
    pin_type        VARCHAR(50),
    latitude        FLOAT8       NOT NULL,
    longitude       FLOAT8       NOT NULL,
    pin_year        INT4         NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT fk_map_pin_context  FOREIGN KEY (context_id) REFERENCES historical_context(context_id),
    CONSTRAINT fk_map_pin_creator  FOREIGN KEY (created_by)  REFERENCES "user"(uid),
    CONSTRAINT chk_pin_owner_type  CHECK (pin_owner_type IN ('ADMIN', 'USER'))
);

CREATE INDEX idx_map_pin_context_year  ON map_pin(context_id, pin_year);
CREATE INDEX idx_map_pin_context_owner ON map_pin(context_id, pin_owner_type);
CREATE INDEX idx_map_pin_creator_user  ON map_pin(created_by, pin_owner_type);
