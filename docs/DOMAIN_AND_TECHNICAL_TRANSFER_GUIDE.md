# HistoryTalk Domain And Technical Transfer Guide

Last verified: 2026-05-28

This guide is for a developer joining the HistoryTalk project. It explains the business domain, the current service architecture, the Java Spring Boot package structure, the Python AI service, and the main operational rules that should be preserved.

## 1. Product Domain

HistoryTalk is a history learning platform. The core product idea is to let learners explore historical contexts, interact with historical characters, and validate learning through quizzes.

The platform has four main user groups:

| Actor | Main responsibility |
| --- | --- |
| Guest | Browse public historical content such as characters, contexts, documents, and quizzes. |
| Customer | Register, log in, chat with historical characters, start quiz sessions, submit answers, and view their own results. |
| Staff | Manage educational content such as historical contexts, characters, documents, and quizzes. |
| Admin | Higher privilege account used for administrative content actions where enabled by security rules. |

The product is content-first. Most technical modules are built around preparing trustworthy historical content, exposing it safely to learners, and using it to drive character roleplay conversations.

## 2. Domain Model

The current Java domain is centered on these concepts:

| Domain | What it means in business terms | Main Java package/files |
| --- | --- | --- |
| User and Authentication | Account identity, role, login, refresh token, logout, and account deactivation. | `entity/user`, `controller/authentication`, `service/authentication`, `security` |
| Historical Context | A historical event, period, campaign, dynasty, or topic that anchors learning content. | `entity/historicalContext`, `controller/historicalContext`, `service/historicalContext` |
| Historical Document | Source or explanatory material attached to a historical context. The current `DocumentType` enum supports text and markdown. | `HistoricalContextDocument`, `HistoricalContextDocumentController`, document processor strategies |
| Character | A historical figure who can be linked to one or more historical contexts and used for roleplay chat. | `entity/character`, `controller/character`, `service/character` |
| Character Document | Persisted supporting material attached to a character entity. There is no current public controller/repository surface dedicated to this entity. | `CharacterDocument`, `Character.documents` |
| Chat Session | A learner conversation with a character inside a historical context. | `entity/chat`, `controller/chat`, `service/chat` |
| Message | A single user or assistant message in a chat session, including suggested follow-up questions for AI replies. | `Message`, `MessageRepository`, `MessageService` |
| Quiz | A learner-facing assessment linked to historical material. Staff can create quizzes and questions. | `entity/quiz`, `controller/quiz`, `service/quiz` |
| Quiz Session and Result | A learner attempt, selected answers, score, duration, and result history. | `QuizSession`, `QuizResult`, `QuizAnswerDetail` |

Important shared enums:

```text
DocumentType
EventCategory
EventEra
MessageRole
UserRole
```

Most persisted rows use UUID identifiers. Many user-facing content tables use `deleted_at` for soft delete. Historical contexts and characters also use `is_draft`, so new code must handle draft, published, and soft-deleted content deliberately instead of assuming every row is public.

## 3. Business Workflows

### Browse Historical Content

Guests and authenticated users can read public content:

```text
GET /Historical-tell/api/v1/characters
GET /Historical-tell/api/v1/historical-contexts
GET /Historical-tell/api/v1/historical-documents
GET /Historical-tell/api/v1/quizzes
```

Content is organized by historical context. A context can have documents, characters, and quizzes. Characters can be linked to contexts, which lets the chat flow know which historical scenario the character should answer within.

### Manage Content

Staff and admin users manage most content mutations:

```text
/Historical-tell/api/v1/characters
/Historical-tell/api/v1/historical-contexts
/Historical-tell/api/v1/historical-documents
/Historical-tell/api/v1/staff/quizzes
```

The service uses method-level authorization such as `@PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")` on content mutations. When adding new write APIs, apply the same role policy unless the product owner explicitly defines a different rule.

### Chat With A Historical Character

The learner chat flow uses both services:

```text
Frontend
  -> Java backend /Historical-tell/api/v1/chat/...
  -> Python AI backend /v1/ai/chat
  -> configured LLM provider
```

Business behavior:

1. A customer opens or creates a chat session for a character and historical context.
2. Java validates the JWT and resolves the current user.
3. Java loads the character, context, prior messages, and session state.
4. Java sends a structured chat request to the Python AI service.
5. Python builds a roleplay prompt from character and context data.
6. Python calls the configured LLM.
7. Python returns the assistant message and up to three suggested questions.
8. Java persists the assistant response.
9. Java can request an AI-generated session title after the first exchange.

The frontend should call the Java backend. The Python AI service is an internal supporting service and should not become the public API for normal frontend flows.

### Take A Quiz

Learners use the customer quiz endpoints:

```text
GET  /Historical-tell/api/v1/quizzes
POST /Historical-tell/api/v1/quizzes/{quizId}/start
POST /Historical-tell/api/v1/quizzes/submit
GET  /Historical-tell/api/v1/quizzes/results/me
```

Staff manage quiz content through:

```text
/Historical-tell/api/v1/staff/quizzes
```

Quiz questions support ordering, explanations, soft delete, and answer detail history. Most staff quiz management endpoints allow staff or admin users, but staff quiz session soft-delete is staff-only in the controller.

## 4. Repository Structure

Root workspace:

```text
C:\Users\KHAI\Documents\Historical-talk\SWD392_FinalProject_HistoryTalk
```

Main service directories:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

Documentation directories:

```text
docs/
docs/services/history-talk-backend/
docs/services/history-talk-backend-ai/
docs/superpowers/
```

The Java backend has been restored to conventional Spring Boot package names. Do not move Java code back into `presentation`, `application`, or `dataaccess` roots. Those names may still appear in the Python AI service, where they are part of its current internal layering.

## 5. Java Backend Overview

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
PostgreSQL driver
MySQL driver
Flyway dependency
```

Runtime basics:

```text
Port: 8080
Servlet path: /Historical-tell
API prefix used by controllers: /api/v1
Swagger UI: /Historical-tell/api/v1/swagger-ui
OpenAPI docs: /Historical-tell/api/v1/api-docs
```

Java package root:

```text
src/main/java/com/historytalk
```

Current top-level package layout:

| Package | Responsibility |
| --- | --- |
| `config` | Spring Security, OpenAPI, Swagger, and request authentication handlers. |
| `controller` | REST API boundary. Controllers should stay thin and delegate business decisions to services. |
| `dto` | Request and response payload contracts. |
| `entity` | JPA entities and enums. |
| `exception` | Custom exceptions and the global exception handler. |
| `mapper` | DTO/entity mapping helpers. |
| `repository` | Spring Data JPA repositories. |
| `security` | JWT token provider, auth filter, principal model, and principal annotation. |
| `service` | Business logic, orchestration, transactions, and external AI service calls. |
| `utils` | Small utility helpers, including UUID/security helpers and JWT property utilities. |

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

Expected coding rule for Java:

```text
controller -> service -> repository -> entity/database
```

Controllers should not call repositories directly. Repositories should not contain business workflow decisions. Services own transactional business logic and coordination with the Python AI service.

## 6. Java API Surface

Main controller groups:

| Controller | Base path | Purpose |
| --- | --- | --- |
| `AuthController` | `/api/v1/auth` | Register, login, refresh token, logout, deactivate users. |
| `CharacterController` | `/api/v1/characters` | List, view, create, update, delete, soft-delete, and link characters to contexts. |
| `HistoricalContextController` | `/api/v1/historical-contexts` | List, view, create, update, delete, and soft-delete contexts. |
| `HistoricalContextDocumentController` | `/api/v1/historical-documents` | List, search, create, update, and delete context documents. |
| `ChatController` | `/api/v1/chat` | Manage chat sessions, messages, and chat history. |
| `QuizController` | `/api/v1/quizzes` | Learner quiz browse, start, submit, result history, and soft delete. |
| `StaffQuizController` | `/api/v1/staff/quizzes` | Staff quiz and question management. |

Public/authentication routes include:

```text
POST /Historical-tell/api/v1/auth/register
POST /Historical-tell/api/v1/auth/login
POST /Historical-tell/api/v1/auth/refresh-token
POST /Historical-tell/api/v1/auth/logout
```

Public read routes include GET access to characters, historical contexts, historical documents, and quizzes. Chat routes require authentication. Quiz start/submit/result operations require the customer role. Staff quiz management generally requires staff or admin authority, with the staff quiz session soft-delete endpoint restricted to staff.

## 7. Java Configuration

Main config file:

```text
history-talk-backend-Java/src/main/resources/application.properties
```

Important properties:

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

Required Java local values:

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

Optional Java value:

```text
AI_SERVICE_URL
```

`AI_SERVICE_URL` defaults to `http://localhost:8001` in `AiServiceClient`.

Sensitive local config:

```text
src/main/resources/secretKey.properties
```

Do not commit `.env`, `secretKey.properties`, API keys, database passwords, or local IDE/runtime output.

## 8. Database And Persistence

The Java backend uses JPA entities and Spring Data repositories. Hibernate schema mutation is disabled:

```properties
spring.jpa.hibernate.ddl-auto=none
```

Migration SQL files exist under:

```text
history-talk-backend-Java/src/main/resources/db/migration
```

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
V9__update_character_date_fields.sql
V10__add_payment_and_tier_tables.sql
V11__payment_order_unique_order_code.sql
V12__sync_quiz_tables_with_entities.sql
V13__lifecycle_trash_refactor.sql
V14__add_character_model_url.sql
```

Flyway is enabled in `application.properties`:

```properties
spring.flyway.enabled=true
```

Before running the application against any shared or production database, compare the live schema with the committed migration history. Do not let Flyway mutate a shared schema unless the migration plan is deliberate and reviewed.

Schema naming caveat:

```text
application.properties uses DB_SCHEMA
.env.example currently sets DB_SCHEMA=public
existing SQL migrations hard-code historical_schema
```

Choose one schema strategy before running migrations locally. `DB_SCHEMA` must match the target schema expected by the migrations and database. Do not point Flyway at a shared schema until the current schema state and migration history are verified.

## 9. Python AI Backend Overview

Path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

Current stack:

```text
Python 3.x
FastAPI
Uvicorn
Pydantic v2
pydantic-settings
httpx
LangChain
langchain-openai
langchain-google-genai
google-genai
```

Runtime basics:

```text
Default port: 8001
OpenAPI docs: http://localhost:8001/docs
Health: GET /health
AI route prefix: /v1/ai
```

Current Python package root:

```text
src/history_talk_ai
```

Important modules:

| Module | Responsibility |
| --- | --- |
| `main.py` | FastAPI app setup and health route. |
| `presentation/chat/router.py` | HTTP routes for chat, title generation, and diagnostics. |
| `presentation/chat/schemas.py` | Pydantic request and response schemas. |
| `application/chat/service.py` | LangChain orchestration and structured output handling. |
| `application/prompting/prompt_builder.py` | Builds roleplay and title prompts from Java data. |
| `dataaccess/java_backend/client.py` | HTTP client for Java backend diagnostics/fallback reads. |
| `dataaccess/java_backend/*_schema.py` | Schemas for Java character/context payloads. |
| `common/config/settings.py` | Environment-driven settings. |

AI endpoints:

```text
POST /v1/ai/chat
POST /v1/ai/generate-title
GET  /v1/ai/character/{characterId}
GET  /v1/ai/context/{contextId}
GET  /health
```

The AI prompt builder is currently focused on Vietnamese historical roleplay. Python can use character details such as name, title, background, personality, lifespan, and side. For context data, the normal Java-to-AI payload currently sends context id, name, description, era, year, and location. The Python schema also supports richer fields such as category, start year, end year, period, and year label, but those are not all supplied on the normal Java chat path today.

## 10. Python AI Configuration

Settings file:

```text
history-talk-backend-AI/src/history_talk_ai/common/config/settings.py
```

Important variables:

```text
JAVA_BACKEND_URL default http://localhost:8080/Historical-tell
CHARACTER_API_PATH default /api/v1/characters
CONTEXT_API_PATH default /api/v1/historical-contexts
JAVA_CLIENT_TIMEOUT default 10.0
LLM_PROVIDER openai or google, default openai
OPENAI_API_KEY
GOOGLE_API_KEY
LLM_MODEL default gemini-2.5-flash-lite
LLM_TEMPERATURE default 0.7
LLM_MAX_TOKENS default 1024
APP_HOST default 0.0.0.0
APP_PORT default 8001
DEBUG default false
```

There is no committed AI `.env.example` at the time this guide was written. New developers should create an AI `.env` manually from the variables above.

## 11. Local Setup Checklist

Java backend:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q test
mvn spring-boot:run
```

Python AI backend:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

Alternative AI run command:

```powershell
uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src
```

Recommended first local smoke test:

1. Start the database and confirm the schema exists.
2. Start the Java backend on port `8080`.
3. Start the Python AI backend on port `8001`.
4. Open Java Swagger at `/Historical-tell/api/v1/swagger-ui`.
5. Open AI Swagger at `http://localhost:8001/docs`.
6. Register or log in through Java.
7. Call a public content endpoint.
8. Create a chat session and send a message with a valid JWT.

## 12. Verification Commands

Java Maven verification:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q test
```

At the time this guide was written, the Java service has no `src/test` directory. `mvn -q test` is still useful as a Maven lifecycle and compilation check, but it is not meaningful business test coverage yet.

Java package regression check:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
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

Git push readiness:

```powershell
git status -sb
git log --oneline origin/main..HEAD
git log --oneline HEAD..origin/main
```

Only stage intended files. Do not stage local secrets, generated build output, `.codex/`, `.idea/`, or unrelated working-tree changes.

## 13. Change Guidelines

Use these rules when adding or changing code:

| Area | Guideline |
| --- | --- |
| Java structure | Keep the Spring Boot convention: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `security`, `exception`, `utils`. |
| Java layering | Controllers validate/route, services own business logic, repositories own persistence access. |
| API contracts | Treat DTOs, controller paths, auth rules, and AI request/response schemas as frontend/service contracts. |
| Soft delete | Check `deleted_at` behavior before adding queries or list endpoints. |
| Draft content | Check `is_draft` rules before exposing contexts or characters publicly. |
| Security | Use existing `SecurityConfig` and `@PreAuthorize` patterns. |
| Database | Add deliberate SQL migrations. Do not rely on Hibernate auto-DDL. |
| AI integration | Prefer Java as the public API; keep Python as the internal LLM service. |
| Secrets | Never commit `.env`, `secretKey.properties`, API keys, database passwords, or generated tokens. |

## 14. Known Caveats

The following caveats matter for new developers:

| Area | Caveat |
| --- | --- |
| Flyway | Flyway is enabled in current Java config; verify target schema state before running against shared data. |
| Payment scheduler | `PaymentExpiryScheduler` exists, but the main app currently has `@EnableAsync` and no `@EnableScheduling`, so scheduled expiry will not run until scheduling is enabled. |
| AI env sample | The AI backend does not currently include a committed `.env.example`. |
| Python legacy folders | Some old empty `app/` package folders may still exist, but runtime code uses `src/history_talk_ai`. |
| AI Dockerfile | The AI Dockerfile still references the old `app.main:app` module path. Update it before relying on Docker for the AI service. |
| Encoding | Some older README/comment text contains mojibake. Do not copy corrupted text into new docs. |
| LLM defaults | The AI settings default to `LLM_PROVIDER=openai` while the default model string is `gemini-2.5-flash-lite`; choose provider/model values deliberately in local `.env`. |

## 15. Fast Orientation For A New Developer

Start here:

1. Read this guide.
2. Read `docs/PROJECT_HANDOFF.md` for broader repo notes.
3. Read `history-talk-backend-Java/src/main/resources/application.properties`.
4. Read `history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java`.
5. Read one controller/service/repository flow, such as chat or quiz.
6. Read `history-talk-backend-AI/src/history_talk_ai/presentation/chat/router.py`.
7. Run the Java tests.
8. Start Java and AI locally.
9. Test login, one public content endpoint, and the chat flow.

The most important architectural rule is simple: keep Java as a conventional Spring Boot service with `controller`, `service`, and `repository` layers. Do not restore the old Java `presentation`, `application`, and `dataaccess` package structure.
