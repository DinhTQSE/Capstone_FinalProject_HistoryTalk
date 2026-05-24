-- V10: Add Tier, UserTier, PaymentOrder, PaymentTransaction tables
-- and add FK constraint on user.tier_id → tier.tier_id

-- ============================================================
-- 1. tier
-- ============================================================
CREATE TABLE IF NOT EXISTS historical_schema.tier (
    tier_id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(50) NOT NULL,          -- 'free' | 'plus' | 'pro'
    amount          INT         NOT NULL DEFAULT 0, -- price in VND
    no_month        INT         NOT NULL DEFAULT 1, -- subscription duration
    limited_token   INT         NOT NULL DEFAULT 0, -- tokens per month
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP
);

-- Seed default tiers
INSERT INTO historical_schema.tier (tier_id, title, amount, no_month, limited_token, is_active, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'free',  0,      1, 20,   TRUE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'plus',  49000,  1, 100,  TRUE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'pro',   99000,  1, 999,  TRUE, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- ============================================================
-- 2. Add FK constraint on user.tier_id (column already exists)
-- ============================================================
ALTER TABLE historical_schema."user"
    ADD CONSTRAINT fk_user_tier
    FOREIGN KEY (tier_id) REFERENCES historical_schema.tier (tier_id)
    ON DELETE SET NULL;

-- ============================================================
-- 3. user_tier  (subscription records)
-- ============================================================
CREATE TABLE IF NOT EXISTS historical_schema.user_tier (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    uid         UUID        NOT NULL,
    tier_id     UUID        NOT NULL,
    start_time  TIMESTAMP   NOT NULL,
    end_time    TIMESTAMP   NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancel_at   TIMESTAMP,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_user_tier_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid) ON DELETE CASCADE,
    CONSTRAINT fk_user_tier_tier FOREIGN KEY (tier_id)
        REFERENCES historical_schema.tier (tier_id)
);

-- ============================================================
-- 4. payment_order
-- ============================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_order (
    order_id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    uid             UUID        NOT NULL,
    tier_id         UUID        NOT NULL,
    order_code      BIGINT      NOT NULL,
    amount          INT         NOT NULL,
    payment_link_id VARCHAR(255),
    checkout_url    VARCHAR(500),
    qr_code         TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | CANCELLED | EXPIRED
    paid_at         TIMESTAMP,
    expired_at      TIMESTAMP,
    description     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP,
    deleted_at      TIMESTAMP,
    CONSTRAINT fk_payment_order_user FOREIGN KEY (uid)
        REFERENCES historical_schema."user" (uid),
    CONSTRAINT fk_payment_order_tier FOREIGN KEY (tier_id)
        REFERENCES historical_schema.tier (tier_id)
);

-- ============================================================
-- 5. payment_transaction
-- ============================================================
CREATE TABLE IF NOT EXISTS historical_schema.payment_transaction (
    transaction_id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID        NOT NULL,
    amount              INT         NOT NULL,
    payment_link_id     VARCHAR(255),
    payload             TEXT,                          -- raw JSON webhook body
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    transaction_date    TIMESTAMP,
    reference           VARCHAR(255),                  -- bank/gateway ref code
    is_active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP,
    deleted_at          TIMESTAMP,
    CONSTRAINT fk_payment_transaction_order FOREIGN KEY (order_id)
        REFERENCES historical_schema.payment_order (order_id) ON DELETE CASCADE
);

-- ============================================================
-- 6. Indexes
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_tier_is_active        ON historical_schema.tier             (is_active, deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_tier_uid         ON historical_schema.user_tier        (uid, is_active);
CREATE INDEX IF NOT EXISTS idx_user_tier_tier_id     ON historical_schema.user_tier        (tier_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_uid     ON historical_schema.payment_order    (uid, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_order_code    ON historical_schema.payment_order    (order_code);
CREATE INDEX IF NOT EXISTS idx_payment_order_status  ON historical_schema.payment_order    (status);
CREATE INDEX IF NOT EXISTS idx_payment_tx_order      ON historical_schema.payment_transaction (order_id, created_at);
