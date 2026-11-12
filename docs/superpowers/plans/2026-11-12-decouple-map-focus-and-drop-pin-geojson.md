# Decouple Map Focus & Drop Map Pin GeoJSON Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `map_pin.path_geojson` from the DB and code completely, and make `map_focus` a standalone module. Keep its own table and its own `/map-focuses` API, but remove every DB foreign key and every service call that ties it to other modules.

**Architecture:** One Flyway migration does three things. It drops the two `map_focus` FKs (to `historical_context` and `"user"`). It drops the `path_geojson` column and its CHECK constraint on `map_pin`. It recreates `fk_map_pin_context` with `ON DELETE CASCADE`, so permanently deleting a context also removes its pins. In Java, `MapFocus` stores `contextId`/`createdBy` as plain `UUID` columns instead of `@ManyToOne` associations. `HistoricalContextServiceImpl` stops calling `MapFocusService`. All GeoJSON fields and helpers are removed from the map pin entity, DTOs and service.

**Tech Stack:** Spring Boot 3.2, Java 21, Spring Data JPA, Flyway (PostgreSQL, `ddl-auto=none`, `out-of-order=true`), JUnit 5 + Mockito.

**Spec:** User request (2026-11-12). Keep only the map focus API's own code. Delete the parts of map focus that are tied into the DB and into other APIs (e.g. historical context) so those APIs don't break. Remove GeoJSON from map pin completely. Add `ON DELETE CASCADE` on `map_pin → historical_context` so hard-deleting a context doesn't fail. Project rules: `history-talk-backend-Java/docs/historytalk-overall-business-spec.md` (BUS-SPEC-OVERALL-01) and `history-talk-backend-Java/docs/historytalk-overall-technical-guide.md` (TECH-GUIDE-OVERALL-01).

## Global Constraints

- Java service root: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/` (all paths below are relative to it unless they start with `docs/`).
- Migration naming follows the newest convention: `V<yyyyMMddHHmmss>__<snake_desc>.sql` → use `V20261112100000__decouple_map_focus_and_drop_pin_geojson.sql`.
- Never edit existing migrations (V27/V28/V29). Forward-only.
- Do not add `@Version` or `@DynamicUpdate` locking. Do not add column-only update queries. Use plain load-modify-save.
- The `/map-focuses` endpoint paths, request DTOs and `MapFocusResponse` JSON shape (`contextId`, `createdBy` as strings) stay unchanged.
- The `historical_context.battle_map` column (also added by V28) is **not** touched.
- TECH-GUIDE §2.3 soft delete: no `@Where`/`@SQLDelete` on entities. Filter `deletedAt IS NULL` in the service/repository for regular users, and let admin/staff see soft-deleted rows. Do not add associations that can lazy-load a missing row (`EntityNotFoundException`). This is why `MapFocus` moves to plain UUID columns.
- BUS-SPEC §2.9 / TECH-GUIDE §2.3: System Trash must be able to **permanently purge** a context. A hard delete must never fail on FKs from `map_pin` / `map_focus`.
- BUS-SPEC §3 RBAC: context-owned content (pins, focuses) is managed by STAFF/CONTENT_ADMIN/ADMIN/SYSTEM_ADMIN (plus the existing school-scoped teacher/school-admin checks in the services). This plan must not change any role check in `MapFocusServiceImpl` / `MapPinServiceImpl`.
- Layering (TECH-GUIDE §2.1): no business logic in controllers. Changes stay in `entity/map`, `repository`, `service/map`, `service/historicalContext`.

## Findings: where map focus reaches outside its own API

| Coupling | Location | Action |
|---|---|---|
| Soft-delete cascade | `HistoricalContextServiceImpl.softDeleteContext` → `mapFocusService.softDeleteAllFocusesForContext(contextId)` (line ~306) | Remove the call and the `MapFocusService` field |
| Test mock | `HistoricalContextServiceImplTest` `@Mock MapFocusService` (line ~54) | Remove |
| DB FK `fk_map_focus_context` | `map_focus.context_id → historical_context` (V29) | Drop. It would otherwise block `TrashServiceImpl.hardDeleteContext` / `permanentDeleteContext` with an FK violation whenever focuses exist |
| DB FK `fk_map_focus_creator` | `map_focus.created_by → "user"` (V29) | Drop. It would otherwise block hard-deleting a user who created focuses |
| JPA associations | `MapFocus.historicalContext` / `MapFocus.createdBy` (`@ManyToOne`) | Replace with `UUID` columns. Without FKs, orphan rows would make lazy proxies throw `EntityNotFoundException` |
| (related) DB FK `fk_map_pin_context` | `map_pin.context_id → historical_context` (V27), no `ON DELETE` | Recreate with `ON DELETE CASCADE`. Neither `TrashServiceImpl.hardDeleteContext` nor `HistoricalContextServiceImpl.permanentDeleteContext` deletes pins, so a context with any pin (even soft-deleted ones) can't be purged today. `HistoricalContext` has no `mapPins` collection, so the DB-side cascade doesn't conflict with Hibernate and no Java change is needed. `fk_map_pin_creator` stays as is, because the code never hard-deletes users. |

No other entity references `MapFocus`, and neither the AI service nor monitoring references it. Access control is unaffected because `MapFocusServiceImpl` already loads the context through `loadActiveContext` / `loadNonDeletedContext`. The cascade is therefore not needed: a soft-deleted context makes its focuses unreachable for non-admins and blocks admin writes. A side benefit is that restoring a context now brings its focuses back. Before, `restoreContext` never un-deleted them.

## Review Focus

1. **Existing DB rows**: the migration must run on a DB that already has `map_focus` rows and `map_pin` rows with geojson. Use `DROP ... IF EXISTS` and expect no data loss in `map_focus`.
2. **Clients still sending `pathGeoJson`** in create/update pin bodies must not get a 400. Check that Jackson ignores unknown properties (`spring.jackson.deserialization.fail-on-unknown-properties` is not set to true). If it is, report it; don't change it.
3. **Focus whose context was hard-deleted**: `GET /historical-contexts/{id}/map-focuses` for that id must return 404 via `loadActiveContext`, not a 500.
4. **Wrong-context focus id**: `PATCH/DELETE .../{contextId}/map-focuses/{focusId}` with a focus from another context must still be rejected. The comparison changes from `focus.getHistoricalContext().getContextId()` to `focus.getContextId()`.
5. **Purging a trashed context that has pins and focuses** (`DELETE` hard-delete in System Trash) must succeed. Its pins are removed by the DB cascade, and its focuses are left as unreachable orphans (Task 5 manual check, plus the Task 1 SQL check).

---

### Task 1: Flyway migration

**Files:**
- Create: `src/main/resources/db/migration/V20261112100000__decouple_map_focus_and_drop_pin_geojson.sql`
- Modify: `docs/database/schema.sql`. Remove the `path_geojson` line (~237) and the two map_focus FK constraints (~246–265), and add `ON DELETE CASCADE` to the `map_pin` context FK, so the reference schema matches.

- [ ] **Step 1: Write the migration**

```sql
-- map_focus becomes standalone: no FK to historical_context / user
ALTER TABLE map_focus DROP CONSTRAINT IF EXISTS fk_map_focus_context;
ALTER TABLE map_focus DROP CONSTRAINT IF EXISTS fk_map_focus_creator;

-- map_pin: remove GeoJSON route path entirely
ALTER TABLE map_pin DROP CONSTRAINT IF EXISTS chk_map_route_path_type;
ALTER TABLE map_pin DROP COLUMN IF EXISTS path_geojson;

-- map_pin: purge pins together with their context (System Trash hard delete)
ALTER TABLE map_pin DROP CONSTRAINT IF EXISTS fk_map_pin_context;
ALTER TABLE map_pin ADD CONSTRAINT fk_map_pin_context
    FOREIGN KEY (context_id) REFERENCES historical_context(context_id) ON DELETE CASCADE;
```
Keep the `idx_map_focus_*` indexes. Use unqualified table names, because `spring.flyway.schemas` sets the schema (same as V28).

- [ ] **Step 2: Update `docs/database/schema.sql`** to match.

- [ ] **Step 3: Verify** by running `mvn spring-boot:run` against the dev DB. Expected: the log shows `Migrating schema ... to version "20261112100000 - decouple map focus and drop pin geojson"` and the app starts. Then confirm the constraints in psql:
```sql
SELECT conname, confdeltype FROM pg_constraint
WHERE conrelid IN ('map_focus'::regclass, 'map_pin'::regclass) AND contype = 'f';
```
Expected: no `fk_map_focus_*` rows, and `fk_map_pin_context` has `confdeltype = 'c'`. `\d map_pin` shows no `path_geojson`. If no DB is reachable, report it as not run.

- [ ] **Step 4: Commit** `feat(java): decouple map_focus FKs, drop pin geojson, cascade pins on context purge`

### Task 2: Remove map focus cascade from historical context

**Files:**
- Modify: `src/main/java/com/historytalk/service/historicalContext/HistoricalContextServiceImpl.java` (import line 22, field line 48, call lines ~305–306)
- Modify: `src/main/java/com/historytalk/service/map/MapFocusService.java` (remove `softDeleteAllFocusesForContext`)
- Modify: `src/main/java/com/historytalk/service/map/MapFocusServiceImpl.java` (remove its impl, ~line 252)
- Test: `src/test/java/com/historytalk/service/historicalContext/HistoricalContextServiceImplTest.java`

**Interfaces:**
- Produces: `MapFocusService` without `softDeleteAllFocusesForContext`. `HistoricalContextServiceImpl` constructor without `MapFocusService`.

- [ ] **Step 1: Update the test.** Remove the `MapFocusService` import and `@Mock`. Add `softDeleteContext_cascadesToPinsWithoutMapFocus`: stub the context lookup the same way the existing soft-delete tests do, call `softDeleteContext(id)`, and assert `verify(mapPinService).softDeleteAllPinsForContext(id)` and `context.getDeletedAt() != null`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=HistoricalContextServiceImplTest`. Expected: compiles, and the new test passes (it documents current pin-cascade behaviour). Existing tests still pass.
- [ ] **Step 3: Remove** the field, import, call and the `// Cascade to Map Focus points` comment from `HistoricalContextServiceImpl`. Remove the method from the `MapFocusService` interface and impl.
- [ ] **Step 4: Run** `mvn -q -DskipTests compile` then `mvn -q test -Dtest=HistoricalContextServiceImplTest`. Expected: PASS. Then run `grep -rn "softDeleteAllFocusesForContext\|MapFocusService" src/main/java/com/historytalk/service/historicalContext`. Expected: no output.
- [ ] **Step 5: Commit** `refactor(java): stop cascading context soft-delete to map focus`

### Task 3: MapFocus entity uses plain UUID columns

**Files:**
- Modify: `src/main/java/com/historytalk/entity/map/MapFocus.java`
- Modify: `src/main/java/com/historytalk/repository/MapFocusRepository.java`
- Modify: `src/main/java/com/historytalk/service/map/MapFocusServiceImpl.java` (lines ~57, 62, 83–98, 137, 192, 237, 274, 329, 338)
- Create test: `src/test/java/com/historytalk/service/map/MapFocusServiceImplTest.java`

**Interfaces:**
- Produces in `MapFocus`:
  - `@Column(name = "context_id", nullable = false, columnDefinition = "uuid") private UUID contextId;`
  - `@Column(name = "created_by", nullable = false, columnDefinition = "uuid") private UUID createdBy;`
  - Remove the `HistoricalContext` and `User` fields and their imports.
- Repository renames:
  - `findByContextIdAndDeletedAtIsNullAndIsActiveTrue(UUID, Sort)`
  - `findByContextIdAndDeletedAtIsNull(UUID, Sort)`
  - `findByContextIdAndIsDefaultTrueAndDeletedAtIsNull(UUID)`
  - `findByContextIdAndDeletedAtIsNull(UUID)`
  - `findByFocusIdAndDeletedAtIsNull(UUID)` is unchanged
  - In `findMaxOrderIndexByContextId`, change the JPQL to `WHERE f.contextId = :contextId`

- [ ] **Step 1: Write `MapFocusServiceImplTest`** (Mockito, `@ExtendWith(MockitoExtension.class)`, using the same SecurityUtils static-mock pattern as other service tests) with:
  - `createFocus_storesContextAndCreatorAsUuid`: as SYSTEM_ADMIN, with a non-deleted context stub. Capture the saved `MapFocus` and assert `getContextId()` equals the context UUID and `getCreatedBy()` equals the caller UUID. Assert the response's `contextId` and `createdBy` are those UUIDs' `toString()`.
  - `deleteFocus_rejectsFocusFromAnotherContext`: the focus has `contextId = otherUuid`. Calling `deleteFocus(ctxId, focusId, "SYSTEM_ADMIN")` throws the same exception type the current mismatch branch throws (line ~137/237), and `save` is never called.
  - `getFocuses_unknownContext_throwsNotFound`: `contextRepository.findById` returns empty → `ResourceNotFoundException`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=MapFocusServiceImplTest`. Expected: FAIL to compile (`getContextId()` is undefined).
- [ ] **Step 3: Implement** the entity, repository and service changes. The service sets `.contextId(ctxUuid).createdBy(callerUuid)`. The existing `userRepository.findById(callerId)` existence check at line ~83 may stay as validation, but it must not be stored as an entity. The mismatch checks use `focus.getContextId().equals(ctxUuid)`. The response mapping uses `focus.getContextId().toString()` and `focus.getCreatedBy().toString()`.
- [ ] **Step 4: Run** `mvn -q test -Dtest=MapFocusServiceImplTest`. Expected: PASS. Then run `grep -rn "getHistoricalContext\|HistoricalContext_" src/main/java/com/historytalk/service/map/MapFocusServiceImpl.java src/main/java/com/historytalk/repository/MapFocusRepository.java`. Expected: no output.
- [ ] **Step 5: Commit** `refactor(java): store map focus context/creator as plain uuid`

### Task 4: Remove GeoJSON from map pin

**Files:**
- Modify: `src/main/java/com/historytalk/entity/map/MapPin.java` (field + javadoc, lines ~76–86)
- Modify: `src/main/java/com/historytalk/dto/map/CreateMapPinRequest.java` (~39–46), `UpdateMapPinRequest.java` (~56–60), `MapPinResponse.java` (~43–47)
- Modify: `src/main/java/com/historytalk/service/map/MapPinServiceImpl.java`. Remove `EMPTY_LINE_STRING` (~38–39), the builder line (~156), the update branch (~264–266), the response line (~306), `serializePathGeoJson` and `deserializeJson` (~366–406). Then remove the now-unused `objectMapper` field and the `ObjectMapper`, `TypeReference` and `JsonProcessingException` imports. Before removing them, run `grep` to confirm they have no other use.
- Create test: `src/test/java/com/historytalk/service/map/MapPinServiceImplTest.java`

- [ ] **Step 1: Write `MapPinServiceImplTest.createPin_savesPinWithoutGeoJson`** as SYSTEM_ADMIN with a valid `CreateMapPinRequest` (`pinType = "ALLIED_FORCE"`, lat/lng set). Assert `mapPinRepository.save` is called once and the response has the expected `pinType`/`latitude`. Add `jsonDoesNotExposePathGeoJson`: serialize a `MapPinResponse` with `new ObjectMapper()` and assert the string doesn't contain `"pathGeoJson"`.
- [ ] **Step 2: Run** `mvn -q test -Dtest=MapPinServiceImplTest`. Expected: `jsonDoesNotExposePathGeoJson` FAILS. The service constructor still needs `ObjectMapper`; wire it as a mock for now.
- [ ] **Step 3: Remove** all GeoJSON code listed above. Update the test to drop the `ObjectMapper` mock once the field is gone.
- [ ] **Step 4: Run** `mvn -q test -Dtest=MapPinServiceImplTest`. Expected: PASS. Then run `grep -rni "geojson\|LineString" src/main/java src/test/java`. Expected: no output.
- [ ] **Step 5: Check Review Focus #2.** Run `grep -rn "fail-on-unknown-properties\|FAIL_ON_UNKNOWN" src/main`. If it shows no override (the Spring Boot default is lenient), old clients sending `pathGeoJson` are fine. Otherwise note it in the PR.
- [ ] **Step 6: Commit** `refactor(java): remove pathGeoJson from map pin`

### Task 5: Full validation

- [ ] **Step 1:** `mvn test`. Expected: BUILD SUCCESS.
- [ ] **Step 2:** In Swagger (`http://localhost:8080/Historical-tell/api/v1/swagger-ui`), check these:
  - `GET/POST/PATCH/DELETE /historical-contexts/{id}/map-focuses` still work.
  - The map pin schemas have no `pathGeoJson`.
  - `DELETE /historical-contexts/{id}` (soft delete) succeeds.
  - Pick a context that has at least one map pin and one map focus. Soft-delete it, then hard-delete it via System Trash (`SystemTrashController` hard-delete contexts). Expected: the result item is `HARD_DELETED` with no FK error. `SELECT count(*) FROM map_pin WHERE context_id = '<id>'` returns 0. `GET /historical-contexts/<id>/map-focuses` returns 404.
- [ ] **Step 3:** Report anything not run.

## Out of scope (noted, not changed)

- `fk_map_pin_creator` (map_pin → user) stays without `ON DELETE`, because no code path hard-deletes users.
- Orphan `map_focus` rows left after a context purge are not cleaned up. They are unreachable through the API, and the user chose to keep map_focus fully decoupled.
- Old plans and reports under `docs/` that mention `pathGeoJson` are historical and are left as they are.
