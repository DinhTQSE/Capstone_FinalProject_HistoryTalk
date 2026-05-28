# HistoryTalk Project Handoff

Last verified: 2026-05-28

This document summarizes the current repository state, service architecture, operational commands, and caveats. For business/domain context, read `docs/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md`. For the frontend/backend API contract, read `docs/API_CONTRACT.md`.

## 1. Repository Overview

Root:

```text
C:\Users\KHAI\Documents\Historical-talk\SWD392_FinalProject_HistoryTalk
```

Main services:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

Important note: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend` is not the active Java source directory. It currently only contains local environment material. Use `history-talk-backend-Java` for the Spring Boot backend.

Documentation:

```text
docs/
docs/services/history-talk-backend/
docs/services/history-talk-backend-ai/
docs/superpowers/
```

The Java backend uses conventional Spring Boot packages. Do not reintroduce Java package roots named `presentation`, `application`, `dataaccess`, or `common`. Those names are still used in the Python AI service package layout.

## 2. Git Working Rules

Remote:

```text
origin https://github.com/DinhTQSE/SWD392_FinalProject_HistoryTalk.git
```

Verified working state on 2026-05-28:

```text
branch: refactor/KhaiVDD
working tree: clean
```

Use `git status -sb` before staging and pushing. Stage only intended files, and do not stage unrelated local edits.

Do not commit local-only or generated files:

```text
.env
secretKey.properties
API keys
database passwords
.codex/
.idea/
target/ build output
```

Some legacy `target/` artifacts may already be tracked. Do not stage new build output during normal work; cleanup of existing tracked artifacts should be a separate deliberate change.

## 3. Runtime Architecture

```text
Frontend
  -> Java backend, Spring Boot, port 8080, servlet path /Historical-tell
  -> Python AI backend, FastAPI, port 8001
  -> LLM provider, OpenAI or Google Gemini
```

The frontend should call the Java backend. The Python AI service is an internal service called by Java for chat generation, title generation, and diagnostic character/context lookups.

## 4. Java Backend

Path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Current stack:

```text
Java 21
Spring Boot 3.2.5
Maven
Spring Web
Spring Security
Spring Data JPA
Spring Validation
Springdoc OpenAPI
JJWT
Lombok
PostgreSQL runtime driver
MySQL runtime driver
Flyway
PayOS Java SDK
Spring Boot Actuator
Micrometer Prometheus
Spring Security OAuth2 Client
```

Build and test:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
mvn -q test
```

Verified on 2026-05-28:

```text
mvn -q -DskipTests compile -> exit code 0
mvn -q test                -> exit code 0
```

Mockito/ByteBuddy may print JVM dynamic-agent warnings on newer JDKs. Those warnings are not test failures.

Run:

```powershell
mvn spring-boot:run
```

Runtime paths:

```text
Java port: 8080
Servlet path: /Historical-tell
Swagger UI: /Historical-tell/api/v1/swagger-ui
OpenAPI docs: /Historical-tell/api/v1/api-docs
```

## 5. Java Package Structure

Package root:

```text
src/main/java/com/historytalk
```

Top-level packages:

```text
config       Spring, OpenAPI, Swagger, PayOS, OAuth2, security handlers
controller   REST controllers
dto          Request/response DTOs
entity       JPA entities and enums
exception    Custom exceptions and global exception handler
mapper       DTO/entity mapping helpers
repository   Spring Data JPA repositories
security     JWT filter, token provider, principal model, OAuth2 handlers
service      Business services, payment, dashboard, AI client
utils        UUID/security/JWT utility helpers
```

Current class counts by top-level package:

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

## 6. Main Java Domains

Authentication:

- Controller: `controller/authentication/AuthController.java`
- Services: `service/authentication/*`
- Security: `security/*`, `security/oauth2/*`, `utils/authentication/*`
- Endpoints under `/api/v1/auth`
- Google OAuth browser redirect flow is implemented.

Characters:

- Controllers: `controller/character/CharacterController.java`, `CharacterDocumentController.java`
- Services: `service/character/*`
- Entity: `entity/character/Character.java`
- Repository: `repository/CharacterRepository.java`
- Endpoints under `/api/v1/characters` and `/api/v1/character-documents`
- Character responses include `modelUrl`.

Historical contexts and documents:

- Controllers: `controller/historicalContext/*`
- Services: `service/historicalContext/*`
- Document parsing strategies: `service/historicalContext/strategy/*`
- Entities: `entity/historicalContext/*`, `entity/document/Document.java`
- Repositories: `HistoricalContextRepository`, `DocumentRepository`
- Endpoints under `/api/v1/historical-contexts` and `/api/v1/historical-documents`

Chat:

- Controller: `controller/chat/ChatController.java`
- Services: `service/chat/*`
- Entities: `entity/chat/*`
- Repositories: `ChatSessionRepository`, `MessageRepository`
- AI integration: `service/chat/AiServiceClient.java`
- Endpoints under `/api/v1/chat`

Quiz:

- Controllers: `controller/quiz/QuizController.java`, `StaffQuizController.java`
- Services: `service/quiz/*`
- Entities: `entity/quiz/*`
- Repositories: `QuizRepository`, `QuestionRepository`, `QuizSessionRepository`
- Public endpoints under `/api/v1/quizzes`
- Staff endpoints under `/api/v1/staff/quizzes`

Payment:

- Controllers: `controller/payment/PaymentController.java`, `PayOSWebhookController.java`
- Services: `service/payment/*`
- Entities: `entity/payment/*`
- Repositories: `repository/payment/*`
- Endpoints under `/api/v1/payments`

System dashboard and trash:

- Controllers: `controller/dashboard/SystemDashboardController.java`, `controller/trash/SystemTrashController.java`
- Services: `service/dashboard/*`, `service/trash/*`
- Endpoints under `/api/v1/system-admin/dashboard` and `/api/v1/system/trash`

## 7. Java Configuration

Main config file:

```text
src/main/resources/application.properties
```

Important current properties:

```properties
spring.application.name=history-talk-backend
server.port=8080
spring.mvc.servlet.path=/Historical-tell
spring.config.import=optional:classpath:secretKey.properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.hikari.schema=${DB_SCHEMA}
spring.jpa.properties.hibernate.default_schema=${DB_SCHEMA}
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.schemas=${DB_SCHEMA}
spring.flyway.out-of-order=true
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION_MS}
jwt.refreshExpiration=${JWT_REFRESH_EXPIRATION_MS}
```

Required Java runtime values:

```text
DB_URL
DB_USER
DB_PASSWORD
DB_SCHEMA
JWT_SECRET
JWT_EXPIRATION_MS
JWT_REFRESH_EXPIRATION_MS
PAYOS_CLIENT_ID
PAYOS_API_KEY
PAYOS_CHECKSUM_KEY
PAYOS_RETURN_URL
PAYOS_CANCEL_URL
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
FRONTEND_OAUTH_SUCCESS_URL
FRONTEND_OAUTH_FAILURE_URL
```

Optional value:

```text
AI_SERVICE_URL defaults to http://localhost:8001
```

Sensitive local config:

```text
src/main/resources/secretKey.properties
```

This file is intentionally ignored by git through `**/secretKey.properties`.

## 8. Java Security Rules

Security config:

```text
src/main/java/com/historytalk/config/SecurityConfig.java
```

Current access policy:

- Actuator health is public.
- Actuator Prometheus is restricted by allowed IPs.
- Swagger/OpenAPI endpoints are public.
- `/api/v1/auth/**`, OAuth2 authorization, and OAuth2 callbacks are public.
- Payment tiers and PayOS webhook endpoints are public.
- GET requests for characters, character documents, historical contexts, historical documents, and quizzes are public.
- `/api/v1/chat/**` requires authentication.
- Quiz mutation endpoints require authentication.
- All other endpoints require authentication.

Method-level annotations still enforce content/admin roles for content management endpoints.

## 9. Database And Migrations

Migration files currently committed:

```text
V1__seed_roles.sql
V2__add_chat_session_fields.sql
V3__add_message_suggested_questions.sql
V4__upgrade_quiz_module.sql
V5__schema_updates.sql
V6__indexes_and_quiz_fk.sql
V7__seed_sample_data.sql
V8__draft_publish_and_trash.sql
V9__update_character_date_fields.sql
V10__add_payment_and_tier_tables.sql
V11__payment_order_unique_order_code.sql
V12__sync_quiz_tables_with_entities.sql
V13__lifecycle_trash_refactor.sql
V14__add_character_model_url.sql
```

Important notes:

- `spring.jpa.hibernate.ddl-auto=none`, so Hibernate should not mutate schema automatically.
- Flyway is enabled in the current Java config.
- `DB_SCHEMA` must match the target schema. Current migrations are written for the project schema strategy; verify database state before running against any shared database.
- PostgreSQL is the expected local database from current config examples.

## 10. Content Lifecycle

Current content lifecycle for characters, historical contexts, and quizzes:

```text
deletedAt != null -> INACTIVE
deletedAt == null && isPublished == false -> DRAFT
deletedAt == null && isPublished == true -> ACTIVE
```

Main list APIs exclude soft-deleted records. Trash APIs expose deleted records for restore or hard-delete workflows.

Soft-delete endpoints:

```text
PATCH /Historical-tell/api/v1/characters/{characterId}/soft-delete
PATCH /Historical-tell/api/v1/historical-contexts/{contextId}/soft-delete
PATCH /Historical-tell/api/v1/staff/quizzes/{quizId}/soft-delete
```

Central trash endpoints:

```text
/Historical-tell/api/v1/system/trash
```

## 11. Python AI Backend

Path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

Runtime:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python main.py
```

Alternative:

```powershell
uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src
```

Current Python package root:

```text
src/history_talk_ai
```

Important modules:

```text
main.py
presentation/chat/router.py
presentation/chat/schemas.py
application/chat/service.py
application/prompting/prompt_builder.py
dataaccess/java_backend/client.py
common/config/settings.py
```

The AI service has no committed `.env.example`. Create `.env` manually from settings in `src/history_talk_ai/common/config/settings.py`.

## 12. Java To AI Chat Flow

1. Frontend calls Java `/Historical-tell/api/v1/chat/...`.
2. Java validates JWT and resolves the current user.
3. Java loads character, context, session, and message data.
4. Java builds `characterData` and `contextData`.
5. Java calls AI service `POST /v1/ai/chat`.
6. AI service calls the configured LLM and returns assistant message plus suggested questions.
7. Java persists the assistant message.
8. Java may call `POST /v1/ai/generate-title` asynchronously for the first exchange.

AI diagnostic endpoints:

```text
GET /v1/ai/character/{characterId}
GET /v1/ai/context/{contextId}
```

## 13. Key API Areas

```text
/Historical-tell/api/v1/auth
/Historical-tell/api/v1/characters
/Historical-tell/api/v1/character-documents
/Historical-tell/api/v1/historical-contexts
/Historical-tell/api/v1/historical-documents
/Historical-tell/api/v1/chat
/Historical-tell/api/v1/quizzes
/Historical-tell/api/v1/staff/quizzes
/Historical-tell/api/v1/system/trash
/Historical-tell/api/v1/system-admin/dashboard
/Historical-tell/api/v1/payments
```

Python AI endpoints:

```text
POST /v1/ai/chat
POST /v1/ai/generate-title
GET /v1/ai/character/{characterId}
GET /v1/ai/context/{contextId}
GET /health
```

## 14. Verification Commands

Java compile:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
```

Java tests:

```powershell
mvn -q test
```

Java structure check:

```powershell
rg "com\.historytalk\.(presentation|application|dataaccess|common)" src/main/java
```

Expected result: no matches.

Python import smoke check:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python -c "import sys; sys.path.insert(0, 'src'); from history_talk_ai.main import app; print('python-import-ok')"
```

Python router import smoke check:

```powershell
python -c "import sys; sys.path.insert(0, 'src'); from history_talk_ai.presentation.chat.router import router; print('python-router-import-ok')"
```

## 15. Current Known Caveats

- Keep local-only files and generated output out of commits.
- Java local secrets are currently loaded through `src/main/resources/secretKey.properties` or environment variables.
- Flyway is enabled. Verify target database/schema state before running the application against shared data.
- Payment expiry scheduler class exists, but `HistoryTalkApplication` currently has `@EnableAsync` and does not have `@EnableScheduling`; scheduled expiry will not run until scheduling is enabled.
- The AI service has no `.env.example`.
- Legacy empty AI `app/` folders may remain after the `src/history_talk_ai` migration.
- The AI Dockerfile may still reference the old `app.main:app` path. Check before relying on Docker for the AI service.
- Some older README/comment text contains mojibake. Avoid copying corrupted text into new docs.

## 16. New Developer Start

1. Read this handoff, then `docs/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md`.
2. Read `docs/API_CONTRACT.md`.
3. Create Java local secrets/env values.
4. Create AI `.env` values.
5. Run `mvn -q -DskipTests compile`.
6. Run `mvn -q test`.
7. Run Python import smoke checks.
8. Start PostgreSQL and verify target schema.
9. Start Java on port 8080 and AI on port 8001.
10. Open Java Swagger and AI Swagger.
11. Test login, one public content endpoint, and one chat flow.
