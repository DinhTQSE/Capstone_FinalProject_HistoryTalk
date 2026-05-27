# Active List APIs And Character Model URL Implementation Summary

## Scope

Implemented backend Java changes for:

- Character get-all APIs.
- Quiz get-all API for staff.
- Historical context get-all APIs.
- Character `modelUrl` support for storing 3D model assets.

## Behavior Changes

- Character get-all now always excludes soft-deleted records by passing `includeDeleted = false`.
- Character by-context list now excludes soft-deleted records by passing `includeDeleted = false`.
- Historical context paginated get-all now always excludes soft-deleted records by passing `includeDeleted = false`.
- Historical context simple get-all now always excludes soft-deleted records by passing `includeDeleted = false`.
- Staff quiz get-all now always excludes soft-deleted records by passing `includeDeleted = false`.
- `deletedAt` is no longer exposed by these main response DTOs:
  - `CharacterResponse`
  - `HistoricalContextResponse`
  - `QuizStaffResponse`
- Trash API response remains unchanged through `TrashItemResponse`, so deleted records and deletion timestamps are still available from trash endpoints.

## Character 3D Model URL

Added `modelUrl` to the character module:

- Entity field: `Character.modelUrl`
- DB column: `historical_schema."character".model_url`
- Create request field: `CreateCharacterRequest.modelUrl`
- Update request field: `UpdateCharacterRequest.modelUrl`
- Response field: `CharacterResponse.modelUrl`
- Service mapping for create, update, and response.

## Database Migration

Added Flyway migration:

```sql
ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS model_url VARCHAR(500);
```

File:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V14__add_character_model_url.sql
```

## Validation

Focused regression tests:

```powershell
mvn -q "-Dtest=CharacterServiceImplTest,HistoricalContextServiceImplTest,QuizServiceImplTest" test
```

Result: exit code 0.

Compile validation:

```powershell
mvn -q -DskipTests compile
```

Result: exit code 0.

## Notes

- The implementation intentionally keeps `status` in response DTOs because the requested removal only applies to `deletedAt`.
- Existing untracked repository files outside this task were not modified.
