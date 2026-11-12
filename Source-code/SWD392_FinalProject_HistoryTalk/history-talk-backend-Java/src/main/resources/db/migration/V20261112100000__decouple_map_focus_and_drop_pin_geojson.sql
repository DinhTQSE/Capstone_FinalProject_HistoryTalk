-- =============================================
-- V20261112100000: Decouple map_focus, drop map_pin GeoJSON,
--                  cascade map_pin on context purge
-- =============================================

-- ── Part A: map_focus becomes standalone ─────────────────────────────────────
-- No FK to historical_context / user, so purging a context or user is never
-- blocked by focus rows. Access is still gated in MapFocusServiceImpl.
ALTER TABLE map_focus DROP CONSTRAINT IF EXISTS fk_map_focus_context;
ALTER TABLE map_focus DROP CONSTRAINT IF EXISTS fk_map_focus_creator;

-- ── Part B: remove GeoJSON route path from map_pin ───────────────────────────
ALTER TABLE map_pin DROP CONSTRAINT IF EXISTS chk_map_route_path_type;
ALTER TABLE map_pin DROP COLUMN IF EXISTS path_geojson;

-- ── Part C: purge pins together with their context (System Trash hard delete)
ALTER TABLE map_pin DROP CONSTRAINT IF EXISTS fk_map_pin_context;
ALTER TABLE map_pin ADD CONSTRAINT fk_map_pin_context
    FOREIGN KEY (context_id) REFERENCES historical_context(context_id) ON DELETE CASCADE;
