# Monolith to Microservices Design (DDD + Database-per-Service)

- Date: 2026-05-18
- Project: `SWD392_FinalProject_HistoryTalk`
- Scope: Java monolith decomposition + AI Gateway introduction
- Constraints selected:
  - DDD with Database-per-Service
  - Sync RPC: Spring Cloud OpenFeign
  - Async communication: RabbitMQ
  - Migration style: Hybrid
    - Big-bang now: Identity & Access, Content Catalog
    - Strangler: Quiz & Assessment, Chat Orchestration
  - Chat split priority: after Quiz stabilizes
  - Keep current Python AI service structure unchanged

## 1) Inferred Business Domain & DDD Bounded Context Mapping

### 1.1 Inferred Core Business Domain
`HistoryTalk` is a historical learning platform where users:
- Explore curated historical contexts and related source documents.
- Interact with historical characters through AI-assisted chat.
- Take quizzes associated with historical contexts.
- Track quiz sessions and results.

### 1.2 Ubiquitous Language (extracted from code/schema)
- `HistoricalContext`
- `Character`
- `HistoricalContextDocument`
- `CharacterDocument`
- `ChatSession`
- `Message`
- `Quiz`
- `Question`
- `QuizSession`
- `QuizResult`
- `QuizAnswerDetail`
- `User` / `Role`
- `Draft` / `Active` / `Inactive`

### 1.3 Subdomain Classification
- Core domain:
  - Learning experience around context + character + conversation + assessment.
- Supporting subdomains:
  - Quiz assessment workflow.
  - Conversation orchestration workflow.
  - Content curation workflow (draft/publish/trash).
- Generic subdomain:
  - Identity and access management.

### 1.4 Bounded Contexts
1. Identity & Access BC (Big-bang)
- Responsibilities:
  - User account lifecycle, authentication, authorization claims.
- Existing assets:
  - `user` entity/table, auth controllers/services/JWT components.

2. Content Catalog BC (Big-bang)
- Responsibilities:
  - Historical contexts, characters, context/character mapping, documents, publish/draft/trash state.
- Existing assets:
  - `historical_context`, `character`, `historical_context_document`, `character_document`, `character_historical_context`.

3. Quiz & Assessment BC (Strangler)
- Responsibilities:
  - Quiz authoring, questions, quiz sessions, quiz results, answer details.
- Existing assets:
  - `quiz`, `question`, `quiz_session`, `quiz_result`, `quiz_answer_detail`.

4. Chat Orchestration BC (Strangler, later phase)
- Responsibilities:
  - Chat session lifecycle, message history, ownership checks, coordination with AI Gateway.
- Existing assets:
  - `chat_session`, `message`, current chat services.

5. AI Gateway BC (introduced now)
- Responsibilities:
  - Stable service contract for AI calls, retries/timeouts/circuit-breakers, observability.
- Constraints:
  - No change to Python AI internals in this phase.

6. Eventing Integration BC (cross-cutting)
- Responsibilities:
  - RabbitMQ exchange conventions, routing keys, event envelope/versioning, retry and DLQ patterns.

---

## 2) Database Migration Plan (Database-per-Service)

### 2.1 Current Monolith Tables
- `historical_schema.user`
- `historical_schema.historical_context`
- `historical_schema.historical_context_document`
- `historical_schema.character`
- `historical_schema.character_document`
- `historical_schema.character_historical_context`
- `historical_schema.chat_session`
- `historical_schema.message`
- `historical_schema.quiz`
- `historical_schema.question`
- `historical_schema.quiz_session`
- `historical_schema.quiz_result`
- `historical_schema.quiz_answer_detail`

### 2.2 Target Databases

#### A) `identity_db` (Identity & Access Service)
- Owns:
  - `user`

#### B) `content_db` (Content Catalog Service)
- Owns:
  - `historical_context`
  - `historical_context_document`
  - `character`
  - `character_document`
  - `character_historical_context`

#### C) `quiz_db` (Quiz & Assessment Service, strangler)
- Owns:
  - `quiz`
  - `question`
  - `quiz_session`
  - `quiz_result`
  - `quiz_answer_detail`

#### D) `chat_db` (Chat Orchestration Service, strangler later)
- Owns:
  - `chat_session`
  - `message`

#### E) `ai_gateway_db` (optional technical storage only)
- Owns:
  - Optional technical tables (idempotency/retry/audit), no business content tables.

### 2.3 Mapping Matrix

| Monolith Table | Target Service | Target DB | Phase | Type |
|---|---|---|---|---|
| `user` | Identity & Access | `identity_db` | Phase 1 | Big-bang |
| `historical_context` | Content Catalog | `content_db` | Phase 1 | Big-bang |
| `historical_context_document` | Content Catalog | `content_db` | Phase 1 | Big-bang |
| `character` | Content Catalog | `content_db` | Phase 1 | Big-bang |
| `character_document` | Content Catalog | `content_db` | Phase 1 | Big-bang |
| `character_historical_context` | Content Catalog | `content_db` | Phase 1 | Big-bang |
| `quiz` | Quiz & Assessment | `quiz_db` | Phase 2+ | Strangler |
| `question` | Quiz & Assessment | `quiz_db` | Phase 2+ | Strangler |
| `quiz_session` | Quiz & Assessment | `quiz_db` | Phase 2+ | Strangler |
| `quiz_result` | Quiz & Assessment | `quiz_db` | Phase 2+ | Strangler |
| `quiz_answer_detail` | Quiz & Assessment | `quiz_db` | Phase 2+ | Strangler |
| `chat_session` | Chat Orchestration | `chat_db` | Phase 3+ | Strangler |
| `message` | Chat Orchestration | `chat_db` | Phase 3+ | Strangler |

### 2.4 Cross-Service Data Rules
- No cross-database foreign keys across services.
- Replace DB joins with:
  - RPC validation for write-critical checks.
  - Event-driven denormalized read data where needed.
- Keep IDs as UUID references:
  - `userId`, `contextId`, `characterId`, `quizId`, `sessionId`.

### 2.5 Consistency Policy
- Immediate consistency (OpenFeign):
  - Validation checks before write commit when correctness depends on latest state.
- Eventual consistency (RabbitMQ):
  - Replicate status/name snapshots for local reads and filtering.
- Soft delete model:
  - Keep `deleted_at` and publish status-change events.

### 2.6 Phased Migration
- Phase 1 (Big-bang):
  - Move Identity + Content to dedicated DB/services.
  - Introduce AI Gateway.
- Phase 2 (Strangler):
  - Split Quiz and migrate traffic in controlled steps.
- Phase 3 (Strangler):
  - Split Chat after Quiz stabilizes.

---

## 3) Integration Specification

### 3.1 OpenFeign (Synchronous RPC)

#### Use cases for RPC
- Content write validation against user status/permissions.
- Quiz creation/update validation against context existence/status.
- Chat session creation validation for character-context eligibility.
- Real-time AI message generation (Chat -> AI Gateway).

#### Identity internal client
```java
@FeignClient(name = "identity-service", path = "/internal/v1/users")
public interface IdentityInternalClient {
    @GetMapping("/{userId}/status")
    UserStatusResponse getUserStatus(@PathVariable("userId") UUID userId);

    @GetMapping("/{userId}/permissions")
    UserPermissionResponse getPermissions(@PathVariable("userId") UUID userId);
}

public record UserStatusResponse(
    UUID userId,
    boolean exists,
    boolean active,
    String role,
    Instant deletedAt
) {}

public record UserPermissionResponse(
    UUID userId,
    boolean canManageContent,
    boolean canManageQuiz,
    boolean canModerate
) {}
```

#### Content internal client
```java
@FeignClient(name = "content-service", path = "/internal/v1/content")
public interface ContentInternalClient {
    @GetMapping("/contexts/{contextId}/status")
    ContextStatusResponse getContextStatus(@PathVariable("contextId") UUID contextId);

    @GetMapping("/characters/{characterId}/status")
    CharacterStatusResponse getCharacterStatus(@PathVariable("characterId") UUID characterId);

    @GetMapping("/characters/{characterId}/contexts/{contextId}/eligibility")
    CharacterContextEligibilityResponse checkCharacterContextEligibility(
        @PathVariable("characterId") UUID characterId,
        @PathVariable("contextId") UUID contextId
    );
}

public record ContextStatusResponse(
    UUID contextId,
    boolean exists,
    boolean active,
    boolean draft,
    String name,
    String era
) {}

public record CharacterStatusResponse(
    UUID characterId,
    boolean exists,
    boolean active,
    boolean draft,
    String name
) {}

public record CharacterContextEligibilityResponse(
    UUID characterId,
    UUID contextId,
    boolean eligible
) {}
```

#### AI Gateway client
```java
@FeignClient(name = "ai-gateway-service", path = "/internal/v1/ai")
public interface AiGatewayClient {
    @PostMapping("/chat/reply")
    AiReplyResponse generateReply(@RequestBody AiReplyRequest request);

    @PostMapping("/chat/title-requests")
    TitleRequestAccepted enqueueTitleGeneration(@RequestBody TitleGenerationRequest request);
}

public record AiReplyRequest(
    UUID chatSessionId,
    UUID characterId,
    UUID contextId,
    String userMessage,
    List<MessageHistoryItem> messageHistory
) {}

public record AiReplyResponse(String message, List<String> suggestedQuestions) {}

public record TitleGenerationRequest(
    UUID chatSessionId,
    UUID characterId,
    UUID contextId,
    String firstUserMessage,
    String firstAssistantMessage
) {}

public record TitleRequestAccepted(UUID requestId, String status) {}
```

### 3.2 RabbitMQ (Asynchronous)

#### Exchanges
- `domain.events` (topic, durable)
- `ai.commands` (topic, durable)
- `domain.dlx` (dead-letter exchange)

#### Event envelope
```json
{
  "eventId": "uuid",
  "eventType": "content.context.status-changed",
  "eventVersion": 1,
  "occurredAt": "2026-05-18T10:30:00Z",
  "producer": "content-service",
  "correlationId": "uuid",
  "causationId": "uuid",
  "payload": {}
}
```

#### Key event schemas

1) `identity.user.deactivated.v1`  
Routing key: `identity.user.deactivated.v1`
```json
{
  "payload": {
    "userId": "uuid",
    "role": "CUSTOMER|STAFF|ADMIN",
    "deactivatedAt": "2026-05-18T10:30:00Z",
    "reason": "SELF|ADMIN_ACTION|POLICY"
  }
}
```

2) `identity.user.restored.v1`  
Routing key: `identity.user.restored.v1`
```json
{
  "payload": {
    "userId": "uuid",
    "restoredAt": "2026-05-18T11:00:00Z"
  }
}
```

3) `content.context.status-changed.v1`  
Routing key: `content.context.status-changed.v1`
```json
{
  "payload": {
    "contextId": "uuid",
    "status": "DRAFT|ACTIVE|INACTIVE",
    "isDraft": false,
    "deletedAt": null,
    "name": "Battle of Dien Bien Phu",
    "era": "CONTEMPORARY"
  }
}
```

4) `content.character.status-changed.v1`  
Routing key: `content.character.status-changed.v1`
```json
{
  "payload": {
    "characterId": "uuid",
    "status": "DRAFT|ACTIVE|INACTIVE",
    "isDraft": false,
    "deletedAt": null,
    "name": "Vo Nguyen Giap"
  }
}
```

5) `quiz.submitted.v1`  
Routing key: `quiz.submitted.v1`
```json
{
  "payload": {
    "resultId": "uuid",
    "sessionId": "uuid",
    "quizId": "uuid",
    "userId": "uuid",
    "score": 8,
    "totalQuestions": 10,
    "percentage": 80.0,
    "durationSeconds": 420,
    "submittedAt": "2026-05-18T10:45:00Z"
  }
}
```

6) `ai.title.generation.requested.v1`  
Routing key: `ai.title.generation.requested.v1` (exchange `ai.commands`)
```json
{
  "payload": {
    "requestId": "uuid",
    "chatSessionId": "uuid",
    "characterId": "uuid",
    "contextId": "uuid",
    "firstUserMessage": "string",
    "firstAssistantMessage": "string"
  }
}
```

7) `ai.title.generated.v1`  
Routing key: `ai.title.generated.v1`
```json
{
  "payload": {
    "requestId": "uuid",
    "chatSessionId": "uuid",
    "title": "Generated short title",
    "generatedAt": "2026-05-18T10:46:00Z"
  }
}
```

### 3.3 Reliability and Error Handling
- Delivery: at-least-once for all domain events.
- Consumer idempotency key: `eventId`.
- Retry model: delayed retries then DLQ.
- RPC safeguards: timeout, retry policy, circuit breaker.
- Validation RPC behavior:
  - Fail closed for write-critical validation.
  - Fail open only for non-critical enrichments.

---

## 4) Technology Setup Recommendation for Microservices

### 4.1 Core platform stack
- Java: Spring Boot 3.x
- Service discovery/config:
  - Spring Cloud Config (optional early)
  - Prefer explicit service URLs initially if team is small
- RPC:
  - Spring Cloud OpenFeign
- Async messaging:
  - Spring AMQP + RabbitMQ
- API edge:
  - Spring Cloud Gateway
- Security:
  - JWT issued by Identity service
- Data:
  - PostgreSQL per service database
- Observability:
  - OpenTelemetry + Prometheus + Grafana

### 4.2 AI path
- Keep existing Python FastAPI AI service unchanged.
- Introduce Java `ai-gateway-service` as the only caller-facing integration point.
- Chat service calls AI Gateway via OpenFeign.

### 4.3 Repo structure direction
- Service-per-folder with independent builds:
  - `identity-service`
  - `content-service`
  - `quiz-service`
  - `chat-service`
  - `ai-gateway-service`
  - `api-gateway`
  - `platform` (docker-compose, RabbitMQ, shared infra templates)

---

## 5) Non-Goals (for this phase)
- Rewriting Python AI internals.
- Full event sourcing.
- Cross-service distributed transactions.
- Immediate full split of Chat domain.

---

## 6) Acceptance Criteria for This Design
- Clear bounded contexts and ownership boundaries.
- Deterministic table-to-service migration mapping.
- Concrete OpenFeign contracts for critical sync operations.
- Concrete RabbitMQ schemas for critical async flows.
- Migration sequencing aligned to selected hybrid rollout constraints.
