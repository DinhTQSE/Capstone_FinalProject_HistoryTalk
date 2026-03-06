# History Talk Backend – AI Guide

## Architecture & Modules
- Monolithic Spring Boot 3.2 service under `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend`; layering stays Controller → Service → Repository with DTOs between layers.
- Historical contexts live in [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/controller/HistoricalContextController.java](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/controller/HistoricalContextController.java) and documents in [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/controller/HistoricalContextDocumentController.java](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/controller/HistoricalContextDocumentController.java); mirror service layers drive business rules (`HistoricalContextService`, `HistoricalContextDocumentService`).
- Repositories expose custom helpers (`findAllSimple`, `findAllWithSearch`, full-text search) used directly in services—respect those names before adding new queries.
- Shared configuration (security, exception handling) sits in `config/` and `exception/`; treat them as cross-module contracts.

## Request/Response Patterns
- All HTTP responses should wrap payloads with `ApiResponse.success(...)` or `ApiResponse.error(...)` from [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/dto/ApiResponse.java](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/dto/ApiResponse.java);
  paginated endpoints return `PaginatedResponse<T>`.
- Validation errors must bubble through `GlobalExceptionHandler` which returns `ValidationErrorResponse` objects ([Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/exception/GlobalExceptionHandler.java](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/exception/GlobalExceptionHandler.java)); do not short-circuit with ad-hoc responses.
- Services enforce ownership checks via `StaffRepository`; reuse helpers already ensuring creator/Admin permissions, and throw `ForbiddenException` plus domain-specific messages when adding new mutations.

## Security & Authentication
- `SecurityConfig` ([Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/config/SecurityConfig.java](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/config/SecurityConfig.java)) is a shared file—GETs on `/v1/**` remain public, mutating verbs require JWT-authenticated STAFF/ADMIN roles.
- Controllers default missing headers (`X-Staff-Id`, `X-Staff-Role`) for Swagger testing; production flows extract data from JWT via `JwtAuthenticationFilter`. Keep those defaults for manual testing but never rely on them in new business logic.
- CORS is wide-open (`*`) with stateless sessions; if you add new auth endpoints, register them explicitly in `authorizeHttpRequests()` before restricting anything else.

## Data & Validation Rules
- Historical context names must be unique; duplicate checks already exist in `HistoricalContextService`. Reuse `DuplicateResourceException` for any new uniqueness guard.
- Document content size is capped at 10 MB and validated via UTF-8 byte length inside `HistoricalContextDocumentService`; follow the same pattern for any text/blob inputs.
- PostgreSQL schema is tracked in [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/database-setup.sql](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/database-setup.sql); align entity changes with this script plus JPA annotations.

## Developer Workflow
- Build/test locally with `mvn clean install`; run the service via `mvn spring-boot:run` (see [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/README.md](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/README.md) for quick-start, curl samples, and Swagger URLs).
- Configuration relies on env vars defined in `application.properties` (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`). Never hardcode credentials; use `.env` only for local work and keep it ignored.
- API contracts live in [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/openapi.yaml](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/openapi.yaml); synchronize controller changes with this spec and the README endpoint tables.

## Collaboration Guardrails
- Follow the shared-file policy and branch/PR checklist documented in [Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/MERGE_STRATEGY.md](Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/MERGE_STRATEGY.md): coordinate before touching `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `application.properties`, `pom.xml`, or `GlobalExceptionHandler.java`.
- New modules should mimic the structure outlined in the merge strategy (entity → repository → service → controller plus DTOs) and register their routes in `SecurityConfig` using the `/v1/...` prefix.
- Always run Swagger smoke tests for new endpoints and document headers/roles required in controller annotations so QA can replay requests quickly.
