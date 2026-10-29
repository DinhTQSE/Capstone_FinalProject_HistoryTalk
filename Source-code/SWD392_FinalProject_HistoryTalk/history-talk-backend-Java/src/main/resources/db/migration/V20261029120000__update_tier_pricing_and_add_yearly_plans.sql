-- Pricing update:
--   Free  0đ                         1.500 tokens/day
--   Plus  49.000đ/month · 499.000đ/year   5.000 tokens/day
--   Pro   99.000đ/month · 999.000đ/year  10.000 tokens/day
-- Yearly plans are separate tier rows (no_month = 12) sharing the monthly plan's title,
-- so checkout/fulfillment (by tier_id, end_time = now + no_month) need no change.

UPDATE historical_schema.tier
SET limited_token = 1500, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000001';

UPDATE historical_schema.tier
SET amount = 49000, no_month = 1, limited_token = 5000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000002';

UPDATE historical_schema.tier
SET amount = 99000, no_month = 1, limited_token = 10000, updated_at = CURRENT_TIMESTAMP
WHERE tier_id = '00000000-0000-0000-0000-000000000003';

INSERT INTO historical_schema.tier (tier_id, title, amount, no_month, limited_token, is_active, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000004', 'plus', 499000, 12, 5000,  TRUE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000005', 'pro',  999000, 12, 10000, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT (tier_id) DO NOTHING;
