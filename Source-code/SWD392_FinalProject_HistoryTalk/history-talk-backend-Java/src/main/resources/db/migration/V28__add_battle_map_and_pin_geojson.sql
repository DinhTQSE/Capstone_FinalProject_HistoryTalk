-- =============================================
-- V28: Battle-map JSON on historical_context
--      + GeoJSON path on map_pin
-- =============================================

-- ── Part A: battle_map field ─────────────────────────────────────────────────
-- Stores the full battleMap overlay object (version, mode, imageUrl,
-- factions[], symbols[]) as defined in map-api.md §5.
-- Nullable: existing contexts have no battle map yet; FE handles null gracefully.

ALTER TABLE historical_context
    ADD COLUMN battle_map JSONB;

COMMENT ON COLUMN historical_context.battle_map IS
    'Nullable JSON object describing the interactive battle-map overlay '
    '(version, mode, imageUrl, imageSource, factions[], symbols[]). '
    'Populated by CONTENT_ADMIN / SYSTEM_ADMIN via PUT /historical-contexts/{id}.';

-- ── Part B: path_geojson field on map_pin ────────────────────────────────────
-- GeoJSON LineString that represents the movement path for a pin arrow.
-- Supports both straight (2 coordinates) and curved (3+ coordinates) arrows.
-- Empty coordinates array = pin with no drawn path (still valid LineString).
-- The DEFAULT lets the existing rows in map_pin satisfy the NOT NULL constraint.

ALTER TABLE map_pin
    ADD COLUMN path_geojson JSONB NOT NULL
        DEFAULT '{"type":"LineString","coordinates":[]}'::jsonb,
    ADD CONSTRAINT chk_map_route_path_type
        CHECK (path_geojson->>'type' = 'LineString');

COMMENT ON COLUMN map_pin.path_geojson IS
    'GeoJSON LineString for the movement/route arrow of this pin. '
    'An empty coordinates array means no path is drawn. '
    'Use 2 coordinate pairs for a straight arrow, 3+ for a curved path.';
