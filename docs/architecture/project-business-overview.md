# HistoryTalk Project Business Overview

## 1. Product Purpose

HistoryTalk is a historical learning platform where users can:

- Explore curated historical events, periods, and contexts.
- Learn about historical characters connected to those contexts.
- Chat with AI-powered historical characters in an in-character conversation.
- Take quizzes to assess their understanding of historical content.
- Review quiz results and chat history.

The platform is designed for both learners and content managers. Customers consume published learning content, while staff/admin users create, manage, publish, archive, and maintain historical content.

## 2. System Architecture

The project currently contains two independent backend services:

- Java backend: Spring Boot service responsible for business APIs, authentication, persistence, content management, chat orchestration, and quiz workflows.
- Python AI backend: FastAPI service responsible for LLM-based historical character replies and chat title generation.

Frontend clients must call the Java backend only. The Python AI service is an internal service and should be called by the Java backend when an LLM response is needed.

### Java Backend Structure

The Java backend follows a strict 3-layer architecture:

- `presentation`: controllers and request/response DTOs.
- `application`: business services, mappers, strategies, and orchestration.
- `dataaccess`: JPA entities, repositories, and persistence models.
- `common`: shared configuration, security, exceptions, utilities, and outbound integrations.

### Python AI Backend Structure

The Python AI backend also follows a layered structure:

- `presentation`: FastAPI routers and schemas.
- `application`: chat service and prompt-building logic.
- `dataaccess`: Java backend client and response schemas.
- `common`: settings and cross-cutting concerns.

## 3. User Roles

The system uses enum-based roles:

- `CUSTOMER`: normal learner account.
- `STAFF`: content manager account.
- `ADMIN`: privileged management account.

JWT tokens contain:

- `uid`: user UUID.
- `role`: `CUSTOMER`, `STAFF`, or `ADMIN`.

Business rule:

- Customers can consume only active, published, non-deleted content.
- Staff/Admin users can manage content and may access draft content.
- Mutating content endpoints require `STAFF` or `ADMIN` unless explicitly stated otherwise.

## 4. Authentication Business Rules

Supported flows:

- Register customer account.
- Login with email and password.
- Refresh JWT access token.
- Logout by blacklisting the current token.
- Deactivate current account.
- Staff-triggered user deactivation.

Important notes:

- User soft deletion uses `deletedAt`.
- A deactivated user must not be able to log in or refresh tokens.
- User deactivation cascades soft deletion to owned content and interaction data.
- Staff/Admin registration exists in service-level code but the controller endpoint is currently commented out, so the operational account-creation flow needs confirmation before future development.

Known limitation:

- Token blacklist is in-memory, so it is lost after service restart. Production should use Redis or persistent storage.

## 5. Historical Context Domain

A `HistoricalContext` represents a historical event, period, or situation.

Core fields include:

- `name`
- `description`
- `era`
- `category`
- `year`
- `startYear`
- `endYear`
- `beforeTCN`
- `location`
- `imageUrl`
- `videoUrl`
- `isDraft`
- `deletedAt`
- `createdBy`

Business rules:

- Historical contexts can be drafted, published, soft-deleted, restored, or permanently deleted depending on endpoint support.
- `isDraft = true` means the content is not visible to customers.
- `isDraft = false` means the content is published, unless soft-deleted.
- `deletedAt != null` means the record is inactive/trash.
- Customer-facing list/detail APIs should hide draft and deleted contexts.
- Staff/Admin-facing workflows may include draft and deleted records when appropriate.

Status mapping:

- `deletedAt != null` -> `INACTIVE`
- `deletedAt == null && isDraft == true` -> `DRAFT`
- `deletedAt == null && isDraft == false` -> `ACTIVE`

Computed fields:

- `period`: derived from `startYear` and `endYear`.
- `yearLabel`: derived from `year` and `beforeTCN`.

## 6. Historical Document Domain

Historical context documents store supporting material for a context.

Business rules:

- Documents belong to a historical context.
- Documents are created by a staff/admin user.
- Documents may have a document type.
- Document processing uses a strategy pattern, currently supporting document formats such as text/markdown.
- Customer visibility should follow the visibility of the owning historical context and the document deletion state.

## 7. Character Domain

A `Character` represents a historical figure that can be displayed and used in AI chat.

Core fields include:

- `name`
- `title`
- `background`
- `image`
- `personality`
- `lifespan`
- `side`
- `isDraft`
- `deletedAt`
- `createdBy`

Business rules:

- Character creation no longer requires a historical context.
- A character can exist independently.
- Character-context relationships are managed through explicit mapping APIs.
- Deprecated compatibility fields `contextId` and `contextIds` may still be accepted during character creation, but future development should prefer explicit mapping.
- Customers cannot view or chat with draft/deleted characters.
- Staff/Admin users may manage draft characters.

Status mapping is the same as historical contexts:

- `deletedAt != null` -> `INACTIVE`
- `isDraft = true` -> `DRAFT`
- otherwise -> `ACTIVE`

## 8. Character-Context Mapping

Characters and historical contexts have a many-to-many relationship.

Business rules:

- A character can be mapped to many historical contexts.
- A context can contain many characters.
- Adding an existing mapping should be idempotent or handled consistently.
- Removing a missing mapping should return a clear business error.
- Chat session creation must validate that the selected `(characterId, contextId)` pair is mapped.
- If the pair is not mapped, the backend should reject the chat session creation.

Recommended future flow:

1. Staff/Admin creates a character.
2. Staff/Admin creates or selects a historical context.
3. Staff/Admin maps the character to the context.
4. Customer can chat only when both records are active/published and the mapping exists.

## 9. Chat Domain

The chat module stores conversations between a user and a historical character in a historical context.

Main entities:

- `ChatSession`
- `Message`

Business rules:

- All chat APIs require authentication.
- A chat session belongs to exactly one user.
- A chat session is attached to one character and one historical context.
- Customers can create chat sessions only with active, published, non-deleted characters and contexts.
- Staff/Admin users may create sessions against draft content for testing/review.
- Session deletion should not leak whether another user's session exists.
- Soft-deleting a chat session should soft-delete its messages.

Message flow:

1. User sends a message to Java backend.
2. Java backend validates session ownership.
3. Java backend saves the user message.
4. Java backend builds character/context payloads.
5. Java backend calls Python AI service.
6. Java backend saves assistant message and suggested questions.
7. If this is the first user message, Java backend may asynchronously generate a chat title.

Greeting rule:

- When a session is created, Java attempts to call AI for a greeting message.
- AI greeting failure must not fail session creation.

Suggested questions:

- Stored on assistant messages as JSON.
- Returned from the latest assistant message when loading messages.

## 10. AI Service Domain

The Python AI service is the LLM brain for historical roleplay.

Main endpoints:

- `POST /v1/ai/chat`
- `POST /v1/ai/generate-title`
- `GET /v1/ai/character/{id}` for diagnostics.
- `GET /v1/ai/context/{id}` for diagnostics.
- `GET /health`

Business rules:

- Frontend must not call the AI service directly.
- Java backend should pre-fill `characterData` and `contextData` whenever possible.
- If pre-filled data is missing, the AI service may call Java backend to resolve character/context data.
- AI response should include:
  - in-character message
  - up to 3 suggested questions
- AI title generation should produce a short chat session title.

Prompt behavior:

- The assistant roleplays as the selected historical character.
- It uses character background, personality, title, side, lifespan, and context information.
- It should stay within the historical context.
- It should not reveal that it is an AI unless the product rules change.

## 11. Quiz Domain

The quiz module supports both staff/admin authoring and customer quiz-taking.

Main entities:

- `Quiz`
- `Question`
- `QuizSession`
- `QuizResult`
- `QuizAnswerDetail`

Staff/Admin workflows:

- List quizzes with pagination/filtering.
- Get quiz detail.
- Create quiz.
- Update quiz metadata.
- Delete or soft-delete quiz.
- Add/update/delete/soft-delete questions.
- Reorder questions.

Customer workflows:

- List available quizzes.
- View quiz detail.
- Start quiz.
- Submit quiz.
- View own quiz result history.
- Soft-delete own quiz result/session where supported.

Business rules:

- Starting a quiz creates a quiz session.
- Starting a quiz increments `playCount`.
- A quiz session has an expiration time.
- A submitted session cannot be submitted again.
- Submission must belong to the current user.
- Submission must not exceed the time limit.
- Backend calculates score by comparing submitted answers with stored correct answers.
- Quiz result stores score, duration, and answer details.

## 12. Soft Delete and Trash Rules

The project uses soft deletion across several entities through `deletedAt`.

General rules:

- `deletedAt == null`: active record.
- `deletedAt != null`: inactive/trash record.
- Customer queries should exclude soft-deleted records.
- Staff/Admin workflows may include trash management.
- Restore sets `deletedAt = null`.
- Permanent delete physically removes the record.

Important implementation note:

- Many current entities use explicit repository filters rather than Hibernate `@Where`.
- When adding new queries, explicitly check draft/deleted visibility rules.

Potential unique constraint issue:

- Soft-deleted records still occupy unique values such as names and emails.
- If reuse is required later, use partial unique indexes where `deleted_at IS NULL`.

## 13. Security and Authorization Rules

General API rules:

- Public GET endpoints may exist for published content.
- Chat APIs require authentication.
- Quiz start/submit requires customer authentication.
- Staff quiz management requires `STAFF` or `ADMIN`.
- Content mutations require `STAFF` or `ADMIN`.

Important implementation notes:

- Method-level `@PreAuthorize` is heavily used.
- Security configuration permits some URL patterns, but controller/service-level authorization still matters.
- `SecurityConfig`, JWT filter, application properties, and authentication logic are shared-risk files and should be edited carefully.

## 14. Data and Migration Rules

Current configuration notes:

- `spring.jpa.hibernate.ddl-auto=none`
- `spring.flyway.enabled=false`
- Flyway migration files exist under `src/main/resources/db/migration`

Development implication:

- Schema changes will not automatically run unless Flyway or manual migration execution is enabled.
- Before adding new fields/entities, confirm the DB migration strategy.
- Avoid relying on Hibernate auto-update unless project configuration is intentionally changed.

## 15. Future Microservice Direction

The docs define a future DDD/microservice split.

Planned bounded contexts:

- Identity & Access
- Content Catalog
- Quiz & Assessment
- Chat Orchestration
- AI Gateway
- Eventing Integration

Target database ownership:

- Identity service owns users.
- Content service owns contexts, characters, documents, and mappings.
- Quiz service owns quizzes, questions, sessions, results, answer details.
- Chat service owns chat sessions and messages.
- AI Gateway owns technical AI integration records only if needed.

Future communication style:

- Synchronous RPC with OpenFeign for validation and critical reads.
- RabbitMQ for domain events and asynchronous workflows.
- No cross-database foreign keys after service split.

This is future architecture guidance. The current implementation is still primarily the Java backend plus the separate Python AI service.

## 16. Current Development Guardrails

Before developing new modules or features:

- Treat source code as truth when docs conflict with implementation.
- Keep the 3-layer structure consistent.
- Do not put business logic in controllers.
- Keep customer visibility rules strict: no draft/deleted content.
- Validate ownership before accessing user-specific data.
- Validate character-context mapping before chat creation.
- Ensure Java remains the only caller-facing backend for AI chat.
- Add focused tests for new business rules.
- Be careful when editing shared security/config files.

High-priority technical checks:

- Confirm whether staff/admin registration should be re-enabled.
- Normalize role fallback values so `CUSTOMER` is used consistently instead of legacy `USER`.
- Decide how migrations should run in local/dev/staging.
- Add tests around draft visibility, soft-delete filtering, chat mapping validation, quiz submission, and account deactivation.

## 17. Recommended Next Development Order

1. Stabilize database migration strategy.
2. Resolve auth role inconsistencies and staff/admin account creation flow.
3. Complete or verify trash/restore/permanent-delete APIs for content.
4. Harden character-context mapping APIs.
5. Add regression tests for chat, quiz, draft/publish, and soft-delete rules.
6. Continue feature modules only after the above business foundations are stable.
