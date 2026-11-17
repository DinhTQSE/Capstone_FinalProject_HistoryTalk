-- Mid-period tier upgrade: a payment order may replace the user's active paid subscription.
-- upgrade_credit_amount = prorated value (VND) of the replaced subscription deducted from the price.
ALTER TABLE historical_schema.payment_order
    ADD COLUMN IF NOT EXISTS upgrade_from_user_tier_id UUID NULL,
    ADD COLUMN IF NOT EXISTS upgrade_credit_amount INT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = 'historical_schema'
          AND constraint_name = 'fk_payment_order_upgrade_from_user_tier'
    ) THEN
        ALTER TABLE historical_schema.payment_order
            ADD CONSTRAINT fk_payment_order_upgrade_from_user_tier
            FOREIGN KEY (upgrade_from_user_tier_id)
            REFERENCES historical_schema.user_tier(id);
    END IF;
END $$;
