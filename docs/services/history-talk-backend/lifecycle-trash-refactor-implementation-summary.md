# Lifecycle Trash Refactor Implementation Summary

## Scope

Implemented lifecycle refactor for the Java backend:

- Removed Java usage of `isActive`.
- Added `ContentStatus` enum with frontend-compatible values: `ACTIVE`, `DRAFT`, `INACTIVE`.
- Kept `isPublished` for `Character` and `HistoricalContext`.
- Added `isPublished` to `Quiz` so quiz visibility no longer depends on `isActive`.
- Kept `deletedAt` as the trash/soft-delete marker.
- Added content-management trash APIs for characters, historical contexts, and quizzes.

## Lifecycle Rules

Backend now derives content status as:

```text
deletedAt != null -> INACTIVE
deletedAt == null && isPublished == false -> DRAFT
deletedAt == null && isPublished == true -> ACTIVE
```

Customer-facing quiz queries now require:

```text
isPublished = true AND deletedAt IS NULL
```

Customer-facing character and historical context queries continue to hide draft/trash content through repository/service filters.

## Trash Flow

Added `CONTENT_ADMIN`/`SYSTEM_ADMIN` endpoints under:

```text
/api/v1/system/trash
```

Supported resources:

- `characters`
- `historical-contexts`
- `quizzes`

Supported actions:

- list trashed items
- bulk restore
- bulk hard delete

Hard delete is only allowed for records already in trash (`deletedAt IS NOT NULL`). Historical context hard delete clears context-character mappings but does not delete mapped characters because characters are independent entities connected through `context_character_mapping`.

## Database Migration

Added Flyway migration:

```text
V13__lifecycle_trash_refactor.sql
```

Migration behavior:

- Adds `quiz.is_published`.
- Backfills `quiz.is_published` from existing `quiz.is_active`.
- Drops `is_active` from lifecycle tables.
- Adds trash/publish indexes.
- Adds case-insensitive lookup indexes for character names, historical context names, and quiz titles. Duplicate creation is enforced in service validation so existing duplicated production/seed rows do not break Flyway migration.

## Validation

Executed successfully:

```text
mvn -q -DskipTests compile
mvn -q test
```

Added focused tests for:

- character duplicate-name validation across trash
- trash restore rejection for non-trashed records
- trash hard delete for trashed records
