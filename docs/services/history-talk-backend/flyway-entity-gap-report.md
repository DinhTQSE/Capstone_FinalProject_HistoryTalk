# Flyway Entity Gap Report

Date: 2026-05-22

Scope: compare the Java entity mappings in `history-talk-backend-Java` with the clean
Flyway schema built from `V1__seed_roles.sql` through the current migrations.

## Reported Error

```text
org.postgresql.util.PSQLException:
ERROR: column "created_at" of relation "user" does not exist
```

`User` maps these columns on `historical_schema."user"`:

| Entity field | Database column | Required |
| --- | --- | --- |
| `uid` | `uid` | yes |
| `userName` | `user_name` | yes |
| `email` | `email` | yes |
| `password` | `password` | yes |
| `role` | `role` | yes |
| `token` | `token` | yes |
| `lastActiveDate` | `last_active_date` | no |
| `createdDate` | `created_at` | yes |
| `updatedDate` | `updated_at` | no |
| `deletedAt` | `deleted_at` | no |

## Root Cause

The committed Flyway baseline (V1) creates `historical_schema."user"` without
`created_at` (and without `token`, `last_active_date`, `updated_at`).

Hibernate was trying to insert into `created_at` because the `User` entity mapped it,
causing the PostgreSQL error.

## Fix Direction

Entities were updated to match the committed Flyway baseline, rather than changing
the V1 migration.

## Entity And Flyway Check

| Area | Result | Notes |
| --- | --- | --- |
| `user` | aligned after fix | `User` no longer maps columns not present in Flyway V1. |
| `historical_context` | aligned | Columns, draft flag, audit fields, and creator FK are present. |
| `character` | aligned | Columns, draft flag, audit fields, creator FK, and join table are present. |
| `chat_session` | aligned | V1 contains `title` and `last_message_at`; V2 is idempotent. |
| `message` | aligned | V1 contains `suggested_questions`; V3 is idempotent. |
| `document` | aligned after fix | `Document` no longer declares Flyway-missing indexes. |
| `quiz` | aligned after fix | Quiz nullability now matches Flyway V1 table definitions. |
| `question` | aligned after fix | Question nullability now matches Flyway V1 table definitions. |
| `quiz_session` | aligned after fix | QuizSession nullability now matches Flyway V1 table definitions. |
| `quiz_answer_detail` | aligned after fix | QuizAnswerDetail created audit nullability matches Flyway V1. |

## Gaps Fixed

### User baseline columns

Flyway V1 does not define `created_at` on `historical_schema."user"`. The `User` entity
was changed so these fields are not persisted columns:

| Field | Change |
| --- | --- |
| `token` | `@Transient` |
| `lastActiveDate` | `@Transient` |
| `createdDate` | `@Transient` |
| `updatedDate` | `@Transient` |

### Quiz nullability

Several quiz entities marked fields as non-null while Flyway V1 created them nullable.
Entities were updated so JPA nullability matches Flyway for:

| Table | Columns |
| --- | --- |
| `quiz` | `play_count`, `rating`, `created_at` |
| `question` | `options`, `correct_answer`, `created_at` |
| `quiz_session` | `start_time`, `is_submitted`, `created_at` |
| `quiz_answer_detail` | `created_at` |

### Document indexes

`Document` previously declared `@Table(indexes=...)` that Flyway V1 does not create.
Those JPA indexes were removed.

## Reset Guidance

If the DB schema was created from a modified V1 earlier, recreate the schema so it
matches the committed Flyway baseline before re-testing.

## Validation

Validation run locally in `history-talk-backend-Java`:

- `mvn -q -Dtest=EntityFlywayMappingContractTest test`
- `mvn -q -DskipTests compile`
