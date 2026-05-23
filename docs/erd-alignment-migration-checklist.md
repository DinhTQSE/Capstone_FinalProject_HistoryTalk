# ERD Alignment And Flyway Reset Checklist

This checklist defines how to align the current backend schema with the target ERD.

Scope is intentionally limited. Do not implement payment, transaction, tier analytics, or quiz refactor work in this migration pass.

## 0. Progress Log

Use this section to track implementation progress.

Status legend:

```text
[ ] Not started
[~] In progress
[x] Done
[!] Blocked / needs confirmation
```

- [x] Created ERD alignment checklist.
- [x] Confirmed scope excludes payment, transaction, tier analytics, token usage, AI cost, and quiz refactor.
- [x] Confirmed UUID must remain the data type for primary keys and foreign keys.
- [x] Confirmed timestamp convention must be `created_at`, `updated_at`, `deleted_at`, with `document.uploaded_at` as the document-specific lifecycle timestamp.
- [x] Confirmed Supabase prod run must use `application-prod.properties` and `secretKey-prod.properties`.
- [x] Confirmed Flyway should be run from a clean dropped schema for this reset pass, not by patching dirty old tables.
- [x] Rewrite in-scope Flyway baseline.
- [x] Update in-scope Java entities.
- [x] Update impacted services/repositories/DTO mappings.
- [x] Fix `V7__seed_sample_data.sql` to match final columns.
- [x] Imported additional seed data from `C:\Users\trand\Downloads\seed_data.sql` into `V7__seed_sample_data.sql`.
- [x] Transformed imported seed data to the current schema: role names, timestamp columns, publish/active flags, and renamed context-character join table.
- [x] Validated imported seed SQL no longer uses legacy executable columns/tables such as `uploaded_date`, `updated_date`, `before_tcn`, `is_draft`, or `character_historical_context`.
- [x] Fixed `V7__seed_sample_data.sql` historical context upsert to use `ON CONFLICT (context_id)` after Supabase reported duplicate `historical_context_pkey` on partially seeded data.
- [x] Run compile.
- [x] Run backend locally without Supabase destructive reset.
- [x] Verify local health endpoint returned `UP`.
- [x] Corrected Document lifecycle timestamp from `created_at` to `uploaded_at` and recompiled successfully.
- [x] 2026-05-23 rechecked ERD alignment for current scope: `mapping_id` added to `context_character_mapping`, `Document.uploaded_at` confirmed, compile passes.
- [x] Supabase Historical Talk prod schema reset completed by project owner.
- [!] Run backend with prod profile and Flyway enabled. Attempted twice on 2026-05-23 after freeing port 8080; blocked because Flyway could not obtain a PostgreSQL connection to Supabase prod from this machine/session.
- [!] User reproduced prod run on 2026-05-23. Root cause remains DB connectivity, not Spring Security: PostgreSQL driver throws `UnknownHostException` for the Supabase DB host before Flyway can migrate.
- [!] New Supabase connection attempt now reaches the database gateway/pooler but fails with `FATAL: (ENOIDENTIFIER) no tenant identifier provided`; likely the Supabase pooler connection string is missing the project tenant identifier in the username or is using the wrong pooler mode.
- [!] Verify Swagger and basic APIs. Blocked because prod backend did not finish startup.

## 1. Target Rules

### 1.1 Keep UUID Types

Do not change primary key or foreign key data types away from UUID.

All ID fields in the target schema should remain UUID in PostgreSQL and `UUID` in Java.

Examples:

```text
uid uuid
context_id uuid
character_id uuid
doc_id uuid
session_id uuid
message_id uuid
```

### 1.2 Standard Timestamp Columns

Use one naming convention everywhere, except `document.uploaded_at` because Document lifecycle is upload-specific:

```text
created_at
uploaded_at (document only)
updated_at
deleted_at
```

Do not use mixed names such as:

```text
created_date
updated_date
deleted_date
uploaded_date
```

Java entity field names should also be normalized:

```java
createdAt
uploadedAt
updatedAt
deletedAt
```

### 1.3 Excluded From This Migration Pass

Do not migrate these areas now:

```text
Payment
Tier
Order
Transaction
Quiz
Question
QuizSession
AnswerDetail
Token usage
AI cost
```

Reason:

- Payment/package business is not implemented yet.
- Transaction/order flow is not stable yet.
- Quiz business is being refactored.
- Token usage and AI cost calculation are not finalized.

## 2. Secret/Profile Rule

For Supabase Historical Talk production DB, use:

```text
src/main/resources/application-prod.properties
src/main/resources/secretKey-prod.properties
```

Do not accidentally use:

```text
src/main/resources/secretKey.properties
```

because that file is local/default development config.

Run with prod profile when targeting Historical Talk prod:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Or package and run:

```powershell
java -jar target/history-talk-backend-1.0.0.jar --spring.profiles.active=prod
```

Do not paste production secrets into docs or logs.

## 3. Flyway Strategy

The current Supabase DB has old Flyway checksum history. Some old migration files were changed after they had already been applied.

Because of that, normal startup with Flyway enabled can fail with checksum mismatch.

For this ERD alignment pass, use a clean reset strategy:

```text
Backup if needed
Drop current schema/tables in Supabase Historical Talk prod
Run Flyway from a clean database state
```

### 3.1 Do Not Patch Old Tables With Many ALTERs

For this reset pass, prefer drop-create style.

Do not build a long chain of `ALTER TABLE ... ADD/DROP/RENAME COLUMN` just to repair the existing dirty schema.

Target approach:

```text
Clean DB/schema
Flyway V1 creates target baseline tables
Flyway seed migration inserts data using correct columns
Application starts
```

### 3.2 Flyway Files To Review

Current migration files:

```text
V1__seed_roles.sql
V2__add_chat_session_fields.sql
V3__add_message_suggested_questions.sql
V4__upgrade_quiz_module.sql
V5__schema_updates.sql
V6__indexes_and_quiz_fk.sql
V7__seed_sample_data.sql
V8__draft_publish_and_trash.sql
```

For this pass:

- Make `V1` represent the clean baseline for all included non-quiz/non-payment tables.
- Keep or remove later migrations only if they are still needed after the new baseline.
- Fix `V7__seed_sample_data.sql` so seed data matches the final column names exactly.

Important:

- If the team chooses to reset Supabase prod, old checksums no longer matter after dropping schema/history.
- If the team does not reset Supabase prod, do not modify old applied migration files. Create a new migration instead.

## 4. Tables In Scope

These tables are in scope for this ERD alignment pass:

```text
user
historical_context
character
document
vector_chunk
context_character_mapping
chat_session
message
```

These tables are out of scope for now:

```text
tier
order
transaction
quiz
question
quiz_session
answer_detail
```

## 5. Entity/Table Checklist

### 5.1 User

Target table:

```text
historical_schema."user"
```

Required columns:

```text
uid uuid primary key
tier_id uuid null
user_name varchar
email varchar
password varchar
role varchar
token int
last_active_date timestamp
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `uid` as UUID.
- [x] Add `tier_id` only if `tier` table is included now. If tier is deferred, keep `tier_id` nullable or defer it too.
- [x] Add `token`.
- [x] Add `last_active_date`.
- [x] Add `is_active`.
- [x] Add `created_at`.
- [x] Add `updated_at`.
- [x] Keep `deleted_at`.
- [x] Change Java transient fields into real columns if needed.
- [x] Normalize Java field names to `createdAt`, `updatedAt`, `deletedAt`.
- [x] Confirm role values:

```text
CUSTOMER
CONTENT_ADMIN
SYSTEM_ADMIN
```

Temporary mapping from current backend:

```text
CUSTOMER -> CUSTOMER
STAFF -> CONTENT_ADMIN
ADMIN -> SYSTEM_ADMIN
```

### 5.2 HistoricalContext

Target table:

```text
historical_schema.historical_context
```

Required columns:

```text
context_id uuid primary key
created_by uuid
name varchar
description text
era varchar
year int
is_bc boolean
location varchar
image_url varchar
video_url varchar
is_published boolean
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `context_id` as UUID.
- [x] Keep `created_by` as UUID FK to user.
- [x] Rename/standardize `before_tcn` to target ERD field if the team wants `is_bc`.
- [x] Decide whether to keep current extra fields:

```text
category
start_year
end_year
```

- [x] Replace or map `is_draft` to `is_published`.
- [x] Add `is_active`.
- [x] Ensure timestamps use only `created_at`, `updated_at`, `deleted_at`.
- [x] Update Java field names to `createdAt`, `updatedAt`, `deletedAt`.

### 5.3 Character

Target table:

```text
historical_schema."character"
```

Required columns:

```text
character_id uuid primary key
created_by uuid
name varchar
title varchar
background text
image_url varchar
born_date date
death_date date
personality varchar/text
is_published boolean
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `character_id` as UUID.
- [x] Keep `created_by` as UUID FK to user.
- [x] Replace or map `is_draft` to `is_published`.
- [x] Add `is_active`.
- [x] Ensure timestamps use only `created_at`, `updated_at`, `deleted_at`.
- [x] Update Java field names to `createdAt`, `updatedAt`, `deletedAt`.

### 5.4 Document

Target table:

```text
historical_schema.document
```

Required columns:

```text
doc_id uuid primary key
uploaded_by uuid
entity_id uuid
entity_type varchar
title varchar
file_url varchar
content text
is_active boolean
uploaded_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `doc_id` as UUID.
- [x] Keep `uploaded_by` as UUID FK to user.
- [x] Keep `entity_id` as UUID.
- [x] Keep `entity_type` enum values:

```text
CONTEXT
CHARACTER
```

- [x] Replace `uploaded_date` with `uploaded_at`.
- [x] Replace `updated_date` with `updated_at`.
- [x] Keep `deleted_at`.
- [x] Add `is_active`.
- [x] Decide whether to keep current `document_type`. If retained, document it as an implementation extension.
- [x] Update Java entity field names.
- [x] Fix `V7__seed_sample_data.sql` to insert `uploaded_at`, not `uploaded_date`.

### 5.5 VectorChunk

Target table:

```text
historical_schema.vector_chunk
```

Required columns:

```text
chunk_id uuid primary key
doc_id uuid
entity_id uuid
content text
embedding vector/array/jsonb
sequence_number int
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Confirm embedding storage type with Supabase/Postgres extension:

```text
vector
jsonb
double precision[]
```

Decision for this pass: `DOUBLE PRECISION[]` is used in the Flyway baseline because no vector search runtime code is wired yet.

- [x] Keep `chunk_id`, `doc_id`, `entity_id` as UUID.
- [x] Add FK from `doc_id` to `document.doc_id`.
- [x] Add `is_active`.
- [x] Use `created_at`, `updated_at`, `deleted_at`.

### 5.6 ContextCharacterMapping

Target table:

```text
historical_schema.context_character_mapping
```

Current implementation:

```text
historical_schema.character_historical_context
```

Checklist:

- [x] Rename table only if possible:

```text
character_historical_context -> context_character_mapping
```

Decision for this pass: table name is changed to `context_character_mapping`, `mapping_id` is added as a UUID primary key, and `(context_id, character_id)` remains unique so the current Java `@ManyToMany` mapping can still write by pair.

- [x] Add `mapping_id` UUID primary key with DB default generation.
- [x] Keep relationship columns as UUID:

```text
context_id uuid
character_id uuid
```

- [x] Keep unique `(context_id, character_id)` for current JPA join-table behavior.
- [x] Do not change UUID data types.
- [x] Update Java `@JoinTable` name.

### 5.7 ChatSession

Target table:

```text
historical_schema.chat_session
```

Required columns:

```text
session_id uuid primary key
uid uuid
context_id uuid
character_id uuid
title varchar
last_message_at timestamp
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `session_id`, `uid`, `context_id`, `character_id` as UUID.
- [x] Add `is_active`.
- [x] Ensure timestamps use `created_at`, `updated_at`, `deleted_at`.
- [x] Update Java field names to `createdAt`, `updatedAt`, `deletedAt`.

### 5.8 Message

Target table:

```text
historical_schema.message
```

Required columns:

```text
message_id uuid primary key
session_id uuid
content text
is_from_ai boolean
suggested_question string[] or text/jsonb
is_active boolean
created_at timestamp
updated_at timestamp
deleted_at timestamp
```

Checklist:

- [x] Keep `message_id`, `session_id` as UUID.
- [x] Add `is_active`.
- [x] Use `created_at`, `updated_at`, `deleted_at`.
- [x] Decide storage type for `suggested_question`:

```text
text
text[]
jsonb
```

- [ ] Current code uses `suggested_questions` TEXT. Confirm whether to keep this or migrate to array/jsonb.

## 6. V7 Seed Data Checklist

`V7__seed_sample_data.sql` must be updated after schema alignment.

Checklist:

- [x] Use only existing final columns.
- [x] Replace `uploaded_date` with `uploaded_at`.
- [x] Replace `updated_date` with `updated_at`.
- [x] Use `deleted_at`.
- [x] Do not insert removed columns.
- [x] Do not insert payment/tier/order/transaction seed data.
- [x] Do not insert quiz seed data in this pass unless the quiz refactor is complete.
- [x] Seed user roles using final role names:

```text
CUSTOMER
CONTENT_ADMIN
SYSTEM_ADMIN
```

- [x] Keep all seeded IDs as UUID.

## 7. Supabase Reset Checklist

Only perform this after team approval. This is destructive.

Target project:

```text
Supabase project: Historical Talk prod
Schema: historical_schema
```

Steps:

1. Confirm the target DB is correct.
2. Backup data if needed.
3. Stop backend app.
4. In Supabase SQL Editor, drop the schema cleanly:

```sql
DROP SCHEMA IF EXISTS historical_schema CASCADE;
CREATE SCHEMA historical_schema;
```

5. If Flyway history exists outside the schema, drop/clean it only after verifying location.
6. Run backend with prod profile and Flyway enabled:

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

7. Verify Flyway runs from a clean state.
8. Verify app starts.
9. Verify Swagger:

```text
http://localhost:8080/Historical-tell/api/v1/swagger-ui
```

10. Verify basic APIs.

## 8. Validation Checklist

After migration:

- [x] `mvn -q -DskipTests compile` passes.
- [ ] `mvn spring-boot:run -Dspring-boot.run.profiles=prod` starts.
- [ ] Flyway has no checksum mismatch.
- [ ] Tables exist in `historical_schema`.
- [x] No in-scope table uses `created_date`, `updated_date`, `deleted_date`, `uploaded_date`, or legacy `updated_date`; `document.uploaded_at` is allowed by ERD.
- [x] All primary keys remain UUID.
- [x] All major FKs remain UUID.
- [x] `V7` seed data is aligned with final column names.
- [ ] Login works with seeded accounts.
- [ ] Swagger opens.

## 9. Implementation Order

Recommended order:

1. Confirm target role names.
2. Confirm whether `tier_id` is deferred with Tier or included as nullable UUID.
3. Confirm `is_bc` vs existing `before_tcn`.
4. Confirm `is_published` mapping from existing `is_draft`.
5. Confirm join table rename only.
6. Rewrite baseline Flyway schema for in-scope tables.
7. Fix Java entities to match target columns.
8. Fix repositories/services impacted by renamed fields/tables.
9. Fix `V7__seed_sample_data.sql`.
10. Run compile.
11. Reset Supabase Historical Talk prod schema.
12. Run backend with prod profile.
13. Verify startup and APIs.
