-- V18: Fix User-Tier relationship
-- Goal: User table connects to UserTier, and UserTier connects to Tier.
--       Remove the old direct User → Tier FK (user.tier_id) which was a
--       conflicting duplicate of the UserTier subscription log.
--
-- Steps:
--   1. Back-fill user_tier rows for any users that had tier_id set but no
--      existing active user_tier record (safety net for existing data).
--   2. Drop the FK constraint fk_user_tier from user.
--   3. Drop the tier_id column from user.
--   4. Drop the cancel_at column from user_tier (no cancellation support).

-- ============================================================
-- Step 1: Back-fill user_tier from user.tier_id (safety net)
-- Inserts a long-lived free-tier-style row for any user who had
-- a tier_id but no existing active user_tier record.
-- This covers edge-case data only; under normal operation the
-- registration flow already creates the user_tier row.
-- ============================================================
INSERT INTO historical_schema.user_tier
    (id, uid, tier_id, start_time, end_time, is_active, created_at)
SELECT
    gen_random_uuid(),
    u.uid,
    u.tier_id,
    NOW(),
    NOW() + INTERVAL '10 years',
    true,
    NOW()
FROM historical_schema."user" u
WHERE u.tier_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM historical_schema.user_tier ut
      WHERE ut.uid = u.uid
        AND ut.is_active = true
        AND ut.deleted_at IS NULL
  );

-- ============================================================
-- Step 2: Drop the direct FK constraint user → tier
-- ============================================================
ALTER TABLE historical_schema."user"
    DROP CONSTRAINT IF EXISTS fk_user_tier;

-- ============================================================
-- Step 3: Drop the tier_id column from user
-- The user table now connects to tier exclusively via user_tier.
-- ============================================================
ALTER TABLE historical_schema."user"
    DROP COLUMN IF EXISTS tier_id;

-- ============================================================
-- Step 4: Drop cancel_at from user_tier
-- Mid-period cancellation is not supported.
-- ============================================================
ALTER TABLE historical_schema.user_tier
    DROP COLUMN IF EXISTS cancel_at;
