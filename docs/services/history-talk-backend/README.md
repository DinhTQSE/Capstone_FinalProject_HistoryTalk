# HistoryTalk Java Backend Docs

Last verified: 2026-05-28

This folder contains Java backend architecture, implementation, and integration notes. Treat this folder plus `docs/PROJECT_HANDOFF.md` and `docs/API_CONTRACT.md` as the current documentation entry point.

Older plan files in this folder are historical artifacts unless their header says they were verified recently. Prefer the handoff, API contract, and implementation summaries for current behavior.

## Current Backend

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

The active backend is Java 21 + Spring Boot 3.2.5. The older `history-talk-backend` directory is not the active Java source directory.

Runtime paths:

```text
Base URL: http://localhost:8080/Historical-tell
API prefix: /api/v1
Swagger UI: /Historical-tell/api/v1/swagger-ui
OpenAPI docs: /Historical-tell/api/v1/api-docs
```

## Current Source Snapshot

```text
config 8
controller 12
dto 58
entity 23
exception 8
mapper 6
repository 14
security 6
service 36
utils 4
```

Validation last run:

```powershell
mvn -q -DskipTests compile
mvn -q test
```

Both commands passed on 2026-05-28.

## Current Feature Areas

- Auth, JWT, user deactivation, and Google OAuth.
- Character and character-document APIs.
- Historical context and historical-document APIs.
- Chat session/message APIs with Java-to-AI integration.
- Quiz customer and staff/admin APIs.
- Content lifecycle and trash APIs.
- PayOS payment checkout, history, tiers, and webhook handling.
- System dashboard APIs.
- Actuator health and Prometheus metrics.

## Current Lifecycle Model

For characters, historical contexts, and quizzes:

```text
deletedAt != null -> INACTIVE
deletedAt == null && isPublished == false -> DRAFT
deletedAt == null && isPublished == true -> ACTIVE
```

Main list APIs hide soft-deleted records. Trash APIs expose deleted records for restore and hard-delete flows.

Soft-delete endpoints:

```text
PATCH /api/v1/characters/{characterId}/soft-delete
PATCH /api/v1/historical-contexts/{contextId}/soft-delete
PATCH /api/v1/staff/quizzes/{quizId}/soft-delete
```

Trash endpoints:

```text
/api/v1/system/trash
```

## Current Migration Status

Flyway is enabled in `src/main/resources/application.properties`.

Current migrations run from `V1__seed_roles.sql` through `V14__add_character_model_url.sql`.

Important caveat: verify `DB_SCHEMA` and the target database schema state before running against shared data.

## Status Notes

- The root handoff is `docs/PROJECT_HANDOFF.md`.
- The API contract is `docs/API_CONTRACT.md`.
- The old Java README inside `history-talk-backend-Java/README.md` contains stale historical content and should not be treated as the source of truth until rewritten.
- `PaymentExpiryScheduler` exists, but scheduling is not active until `@EnableScheduling` is added to application configuration.
