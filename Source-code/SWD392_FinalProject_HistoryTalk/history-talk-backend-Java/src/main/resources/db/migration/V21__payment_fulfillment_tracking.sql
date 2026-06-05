-- Track paid-order fulfillment independently from payment confirmation.
ALTER TABLE historical_schema.payment_order
    ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS fulfilled_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS fulfillment_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fulfillment_error TEXT NULL,
    ADD COLUMN IF NOT EXISTS fulfillment_locked_at TIMESTAMP NULL;

ALTER TABLE historical_schema.user_tier
    ADD COLUMN IF NOT EXISTS payment_order_id UUID NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'historical_schema'
          AND constraint_name = 'fk_user_tier_payment_order'
    ) THEN
        ALTER TABLE historical_schema.user_tier
            ADD CONSTRAINT fk_user_tier_payment_order
            FOREIGN KEY (payment_order_id)
            REFERENCES historical_schema.payment_order (order_id)
            ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_payment_order_fulfillment_retry
ON historical_schema.payment_order (status, fulfillment_status, updated_at)
WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_tier_payment_order
ON historical_schema.user_tier (payment_order_id)
WHERE payment_order_id IS NOT NULL;

-- Backfill fulfillment for paid orders that already have a matching UserTier row.
UPDATE historical_schema.payment_order po
SET fulfillment_status = 'FULFILLED',
    fulfilled_at = COALESCE(po.paid_at, po.updated_at, po.created_at)
WHERE po.status = 'PAID'
  AND po.fulfillment_status <> 'FULFILLED'
  AND EXISTS (
      SELECT 1
      FROM historical_schema.user_tier ut
      WHERE ut.uid = po.uid
        AND ut.tier_id = po.tier_id
        AND ut.deleted_at IS NULL
        AND ut.created_at >= COALESCE(po.paid_at, po.created_at) - INTERVAL '10 minutes'
  );

-- Add reference uniqueness only when existing data can satisfy it.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM historical_schema.payment_transaction
        WHERE reference IS NOT NULL
        GROUP BY reference
        HAVING COUNT(*) > 1
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transaction_reference
                 ON historical_schema.payment_transaction (reference)
                 WHERE reference IS NOT NULL';
    ELSE
        RAISE NOTICE 'Skipping uq_payment_transaction_reference because duplicate references exist.';
    END IF;
END $$;
