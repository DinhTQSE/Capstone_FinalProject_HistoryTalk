# HistoryTalk Project Handoff

Last updated: 2026-05-19

This document summarizes repository state, service architecture, local setup, important modules, operational commands, and caveats. For the business-domain and durable technical transfer guide, read `docs/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md`.

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

Documentation:

```text
docs/
docs/services/history-talk-backend/
docs/services/history-talk-backend-ai/
docs/superpowers/
```

The Java backend was restored to the conventional Spring Boot package structure on 2026-05-19. Do not reintroduce `presentation`, `application`, or `dataaccess` package roots for Java.

## 2. Git Working Rules

Remote:

```text
origin https://github.com/DinhTQSE/SWD392_FinalProject_HistoryTalk.git
```

Current branch:

```text
main
```

Use `git status -sb` before staging and pushing. Stage only intended files, and do not stage unrelated local edits.

Do not commit new local-only or generated files:

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

The active Java backend directory is:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

## 3. System Architecture

Runtime flow:

```text
Frontend
  -> Java backend, Spring Boot, port 8080
  -> Python AI backend, FastAPI, port 8001
  -> LLM provider, OpenAI or Google Gemini
```

The frontend should call the Java backend. The Python AI service is an internal service called by the Java backend for chat generation and title generation.

Java backend responsibilities:

- Authentication and JWT issuance.
- User, character, historical context, document, chat, and quiz APIs.
- Persistence through Spring Data JPA repositories.
- Database schema managed by SQL migration files.
- Calling the AI service for roleplay chat.

Python AI backend responsibilities:

- Receive chat requests from Java.
- Build roleplay prompts from character and historical context data.
- Call the configured LLM through LangChain.
- Return assistant message, suggested questions, and generated chat titles.
- Provide diagnostic proxy endpoints for Java character/context lookup.

## 4. Java Backend

Path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
```

Tech stack:

- Java 21
- Spring Boot 3.2.5
- Maven
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL runtime driver
- MySQL runtime driver is also present
- Flyway dependency is present
- Lombok
- Springdoc OpenAPI
- JJWT

Build and compile:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
```

Run:

```powershell
mvn spring-boot:run
```

Configured port:

```text
8080
```

Servlet path:

```text
/Historical-tell
```

Swagger/OpenAPI paths from current config:

```text
/Historical-tell/api/v1/swagger-ui
/Historical-tell/api/v1/api-docs
```

### Java Package Structure

Current Java package root:

```text
src/main/java/com/historytalk
```

Current package layout:

```text
config       Spring config, OpenAPI config, security config
controller   REST controllers
dto          Request/response DTOs
entity       JPA entities and enums
exception    Custom exceptions and global exception handler
mapper       DTO/entity mapping helpers
repository   Spring Data JPA repositories
security     JWT filter, token provider, authenticated principal
service      Business services and AI service client
utils        Utility helpers and auth utility classes
```

Current class counts by top-level package:

```text
config 5
controller 7
dto 43
entity 17
exception 8
mapper 4
repository 10
security 4
service 24
utils 4
```

### Main Java Domains

Authentication:

- Controller: `controller/authentication/AuthController.java`
- Services: `service/authentication/*`
- Security: `security/*`, `utils/authentication/*`
- Endpoints under `/api/v1/auth`

Characters:

- Controller: `controller/character/CharacterController.java`
- Service: `service/character/*`
- Entity: `entity/character/*`
- Repository: `repository/CharacterRepository.java`
- Endpoints under `/api/v1/characters`

Historical contexts and documents:

- Controllers:
  - `controller/historicalContext/HistoricalContextController.java`
  - `controller/historicalContext/HistoricalContextDocumentController.java`
- Services: `service/historicalContext/*`
- Document parsing strategies: `service/historicalContext/strategy/*`
- Entities: `entity/historicalContext/*`
- Repositories:
  - `repository/HistoricalContextRepository.java`
  - `repository/HistoricalContextDocumentRepository.java`
- Endpoints under:
  - `/api/v1/historical-contexts`
  - `/api/v1/historical-documents`

Chat:

- Controller: `controller/chat/ChatController.java`
- Services: `service/chat/*`
- Entities: `entity/chat/*`
- Repositories:
  - `repository/ChatSessionRepository.java`
  - `repository/MessageRepository.java`
- AI integration: `service/chat/AiServiceClient.java`
- Endpoints under `/api/v1/chat`

Quiz:

- Controllers:
  - `controller/quiz/QuizController.java`
  - `controller/quiz/StaffQuizController.java`
- Services: `service/quiz/*`
- Entities: `entity/quiz/*`
- Repositories:
  - `repository/QuizRepository.java`
  - `repository/QuestionRepository.java`
  - `repository/QuizSessionRepository.java`
  - `repository/QuizResultRepository.java`
- Public endpoints under `/api/v1/quizzes`
- Staff endpoints under `/api/v1/staff/quizzes`

Shared enums:

- `entity/enums/DocumentType.java`
- `entity/enums/EventCategory.java`
- `entity/enums/EventEra.java`
- `entity/enums/MessageRole.java`
- `entity/enums/UserRole.java`

## 5. Java Configuration

Main config file:

```text
src/main/resources/application.properties
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
spring.flyway.enabled=false
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION_MS}
jwt.refreshExpiration=${JWT_REFRESH_EXPIRATION_MS}
```

Required Java runtime environment/config values:

```text
DB_URL
DB_USER
DB_PASSWORD
DB_SCHEMA
JWT_SECRET
JWT_EXPIRATION_MS
JWT_REFRESH_EXPIRATION_MS
AI_SERVICE_URL optional, defaults to http://localhost:8001
```

Sensitive local config:

```text
src/main/resources/secretKey.properties
```

This file is intentionally ignored by git through `**/secretKey.properties`.

Note: `.env.example` in the Java service lists the expected local database, JWT, and AI service variables. Runtime loading still depends on the team's chosen local launch method.

## 6. Java Security Rules

Security config:

```text
src/main/java/com/historytalk/config/SecurityConfig.java
```

Current access policy:

- Swagger/OpenAPI endpoints are public.
- `/api/v1/auth/**` is public.
- GET requests for characters, historical contexts, historical documents, and quizzes are public.
- Chat endpoints require authentication.
- Quiz mutation/session/result soft-delete endpoints require authentication.
- All other endpoints require authentication.

JWT filter:

```text
src/main/java/com/historytalk/security/JwtAuthenticationFilter.java
```

Principal model:

```text
src/main/java/com/historytalk/security/UserPrincipal.java
src/main/java/com/historytalk/security/AuthenticatedPrincipal.java
```

## 7. Database And Migrations

Migration files:

```text
src/main/resources/db/migration/V1__seed_roles.sql
src/main/resources/db/migration/V2__add_chat_session_fields.sql
src/main/resources/db/migration/V3__add_message_suggested_questions.sql
src/main/resources/db/migration/V4__upgrade_quiz_module.sql
src/main/resources/db/migration/V5__schema_updates.sql
src/main/resources/db/migration/V6__indexes_and_quiz_fk.sql
src/main/resources/db/migration/V7__seed_sample_data.sql
src/main/resources/db/migration/V8__draft_publish_and_trash.sql
src/main/resources/db/migration/V9__add_document_type_to_historical_context_document.sql
```

Important notes:

- `spring.jpa.hibernate.ddl-auto=none`, so Hibernate should not mutate schema automatically.
- `spring.flyway.enabled=false` in current config, even though Flyway dependency and migration files exist.
- Before enabling Flyway in any environment, confirm the existing database schema and migration history.
- PostgreSQL is the expected local database from README/config examples.

## 8. Python AI Backend

Path:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```

Tech stack:

- Python 3.x
- FastAPI
- Uvicorn
- Pydantic v2
- pydantic-settings
- httpx
- LangChain
- OpenAI and Google GenAI integrations

Install:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

Run:

```powershell
python main.py
```

Alternative run command:

```powershell
uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src
```

Configured/default port:

```text
8001
```

Docs:

```text
http://localhost:8001/docs
```

Health:

```text
GET /health
```

### Python Package Structure

Current Python package root:

```text
src/history_talk_ai
```

Important modules:

```text
main.py                                  FastAPI app setup
presentation/chat/router.py              AI endpoints
presentation/chat/schemas.py             Request/response schemas
application/chat/service.py              LLM orchestration
application/prompting/prompt_builder.py  Roleplay prompt construction
dataaccess/java_backend/client.py         Java backend client
dataaccess/java_backend/*_schema.py       Java response schemas
common/config/settings.py                Environment settings
common/errors/                           Reserved for custom errors
```

There are still legacy empty `app/` package folders. Current runtime code uses `src/history_talk_ai`.

### Python Environment

Settings source:

```text
src/history_talk_ai/common/config/settings.py
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
LLM_MODEL default gemini-2.5-flash-lite in current code
LLM_TEMPERATURE default 0.7
LLM_MAX_TOKENS default 1024
APP_HOST default 0.0.0.0
APP_PORT default 8001
DEBUG default false
```

There is no AI `.env.example` in the current AI service. New developers should create `.env` manually based on the variables above.

## 9. Java To AI Chat Flow

Java client:

```text
history-talk-backend-Java/src/main/java/com/historytalk/service/chat/AiServiceClient.java
```

AI routes:

```text
history-talk-backend-AI/src/history_talk_ai/presentation/chat/router.py
```

Primary flow:

1. Frontend calls Java `/Historical-tell/api/v1/chat/...`.
2. Java validates JWT and resolves the current user.
3. Java loads character/context/session/message data from the database.
4. Java builds `characterData` and `contextData` payloads.
5. Java calls AI service `POST /v1/ai/chat`.
6. AI service uses supplied payloads to avoid callback loops.
7. AI service calls the configured LLM and returns:
   - assistant message
   - suggested questions
8. Java persists the assistant message.
9. Java may asynchronously call `POST /v1/ai/generate-title` for the first exchange.

AI service diagnostic endpoints:

```text
GET /v1/ai/character/{characterId}
GET /v1/ai/context/{contextId}
```

Use these only for debugging service-to-service connectivity.

## 10. Key API Areas

Java public/auth endpoints:

```text
POST /Historical-tell/api/v1/auth/register
POST /Historical-tell/api/v1/auth/login
POST /Historical-tell/api/v1/auth/refresh-token
POST /Historical-tell/api/v1/auth/logout
```

Java content endpoints:

```text
/Historical-tell/api/v1/characters
/Historical-tell/api/v1/historical-contexts
/Historical-tell/api/v1/historical-documents
```

Java chat endpoints:

```text
/Historical-tell/api/v1/chat/sessions
/Historical-tell/api/v1/chat/messages
/Historical-tell/api/v1/chat/history
```

Java quiz endpoints:

```text
/Historical-tell/api/v1/quizzes
/Historical-tell/api/v1/staff/quizzes
```

Python AI endpoints:

```text
POST /v1/ai/chat
POST /v1/ai/generate-title
GET /v1/ai/character/{characterId}
GET /v1/ai/context/{contextId}
GET /health
```

## 11. Verification Commands

Java compile:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
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

Git remote status check:

```powershell
git fetch --all --prune
git status -sb
git log --oneline origin/main..HEAD
git log --oneline HEAD..origin/main
```

## 12. Current Known Caveats

Git/worktree:

- Keep local-only files and generated output out of commits.
- Do not stage `.codex/`, `.idea/`, service `.env` files, `secretKey.properties`, or new build output.

Java environment:

- `secretKey.properties` is the current local secret config path.
- `.env.example` documents the expected local database, JWT, and AI service variables, but runtime loading still depends on the team's chosen local launch method.

Database:

- Flyway migrations exist, but Flyway is disabled.
- Do not enable migrations in a shared environment without checking schema state.

Python:

- The AI service has no `.env.example`.
- Legacy empty `app/` folders remain after the `src/history_talk_ai` migration.
- The AI Dockerfile still references the old `app.main:app` module path. Update it before relying on Docker for the AI service.

Encoding:

- Some existing README/comment text has mojibake from earlier encoding corruption. Avoid copying that text into new docs without fixing it.

## 13. Recommended First Day For A New Developer

1. Pull latest `main`.
2. Create local Java secret config with database/JWT values.
3. Create AI `.env` with LLM and Java backend values.
4. Run Java compile or tests.
5. Run Python import smoke check.
6. Start PostgreSQL and verify database schema.
7. Start Java backend on port 8080.
8. Start AI backend on port 8001.
9. Open Java Swagger and AI Swagger.
10. Test auth login, a public content endpoint, and chat session flow.

## 14. Change Guidelines

- Keep Java packages in the Spring Boot convention: `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `security`, `exception`, `utils`.
- Do not move Java back to `presentation`, `application`, or `dataaccess`.
- Keep API behavior stable unless the frontend contract is updated.
- Treat `SecurityConfig`, `application.properties`, database migrations, and AI request/response schemas as shared-contract files.
- Run compile/import checks before pushing.
- Never commit `.env`, `secretKey.properties`, API keys, database passwords, or `.codex/`.
