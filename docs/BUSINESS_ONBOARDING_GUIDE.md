# HistoryTalk Business Onboarding Guide

Last updated: 2026-05-19

This document helps a new developer understand the HistoryTalk product from the business side before going deep into controllers, services, repositories, or AI integration. It is based on the service documents under:

```text
docs/services/history-talk-backend
docs/services/history-talk-backend-ai
```

Use this as the business companion to `docs/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md`.

## 1. Product Summary

HistoryTalk is a history learning platform. The product lets learners browse curated historical material, talk with historical characters in context, and test their understanding through quizzes.

The core learning loop is:

```text
Discover historical content
  -> choose a historical character and context
  -> chat with the character in roleplay mode
  -> follow suggested questions
  -> take quizzes and review results
```

The platform is not only a chat application. Chat is one learning activity supported by a wider content system: historical contexts, characters, documents, quizzes, and user progress.

## 2. Main Actors

| Actor | Business meaning | What they usually do |
| --- | --- | --- |
| Guest | A public visitor who has not logged in. | Browse public historical content and public quiz information. |
| Customer | A learner account. | Chat with historical characters, start quiz sessions, submit answers, and view personal results. |
| Staff | A content operator or educator. | Create and manage contexts, characters, documents, mappings, and quizzes. |
| Admin | A higher privilege operator. | Perform staff-level actions and selected administrative actions where supported. |

Current code roles are:

```text
CUSTOMER
STAFF
ADMIN
```

Older service plans sometimes use `REGISTERED` for learner users or `/v1` for routes. Current Java code uses the roles above and controller paths under `/api/v1`, with servlet path `/Historical-tell`.

## 3. Product Domains

### Historical Context

A historical context is the anchor for learning. It represents a historical event, period, campaign, dynasty, topic, or scenario.

Examples from the service docs include Vietnamese historical events such as Bach Dang and Dien Bien Phu.

Business fields include:

| Field | Meaning |
| --- | --- |
| `name` | User-facing name of the event or topic. |
| `description` | Historical explanation used for browsing and AI prompt context. |
| `era` | Broad time period such as ancient, medieval, modern, or contemporary. |
| `category` | Type of content such as war, politics, or culture. |
| `year`, `startYear`, `endYear`, `beforeTCN` | Time information used for display and prompt construction. |
| `location` | Where the event or period is associated. |
| `isDraft` | Whether the content is still being prepared. |
| `deletedAt` | Whether the content is in trash or archived. |

Business rule: public learners should only see content that is active and published. Staff and admins may need to see draft or internal content depending on endpoint purpose.

### Historical Document

Historical documents are supporting material attached to a historical context. They provide source or explanation content that can be shown to users and may later support AI retrieval.

Current document types are:

```text
TEXT
MARKDOWN
```

The backend docs define a strategy/factory model for document processing:

```text
DocumentType
  -> DocumentProcessorFactory
  -> TextProcessorStrategy or MarkdownProcessorStrategy
  -> validated/sanitized content
  -> save document
```

Business rule: document content should be validated and sanitized before storage. Markdown content needs basic protection against unsafe HTML/script content.

### Character

A character is a historical figure that users can inspect and chat with.

Business fields include:

| Field | Meaning |
| --- | --- |
| `name` | Historical figure name. |
| `title` | Role, position, or honorific. |
| `background` | Biographical and historical background. |
| `personality` | Style used by the AI roleplay prompt. |
| `lifespan` | Year range or life period. |
| `side` | Faction, side, movement, dynasty, or affiliation. |
| `image` | Display image URL. |
| `isDraft` | Whether the character is still being prepared. |
| `deletedAt` | Whether the character is in trash or archived. |

Characters and historical contexts are managed independently, then connected through a mapping. This is important for business flexibility: the team can create a character first, create contexts separately, and later decide which contexts are valid for roleplay.

Business rule: creating a character should not require a context, but starting a chat session should require a valid `(characterId, contextId)` pair.

### Character-Context Mapping

This mapping says which characters are valid in which historical contexts.

Business meaning:

```text
Tran Hung Dao can be mapped to a Bach Dang context.
Vo Nguyen Giap can be mapped to a Dien Bien Phu context.
One character can appear in multiple contexts.
One context can include multiple characters.
```

Why this matters:

| Scenario | Business outcome |
| --- | --- |
| Character is not mapped to a context | The user should not be able to start a chat for that invalid pair. |
| Character is mapped to multiple contexts | The same figure can teach different moments in history. |
| Context has several characters | Learners can explore multiple perspectives on the same event. |

Mapping actions are staff/admin operations.

### Chat Session

A chat session is a learner-owned conversation with one character inside one historical context.

The session exists so the product can preserve conversation history, show the latest messages, group chat history by context, and generate a readable title.

Business fields include:

| Field | Meaning |
| --- | --- |
| `sessionId` | Unique conversation id. |
| `uid` | Owner learner. |
| `characterId` | Character being roleplayed. |
| `contextId` | Historical context for the conversation. |
| `title` | Human-readable session title, generated after the first exchange. |
| `lastMessageAt` | Used for sorting history. |
| `deletedAt` | Used for soft-deleting or hiding a session. |

Business rule: users own their own chat sessions. A user should not be able to read, delete, or message through another user's session.

### Message

A message is one user or assistant entry in a chat session.

Roles:

```text
USER
ASSISTANT
```

Assistant messages can include `suggestedQuestions`, stored as a JSON array string in Java. Suggested questions help the learner continue the conversation without needing to invent the next prompt.

Business rule: the frontend sends only `sessionId` and message `content` when continuing a chat. Java resolves the character and context from the session so clients cannot silently change the learning scenario mid-conversation.

### Quiz

A quiz is an assessment connected to historical learning content.

Business fields include:

| Field | Meaning |
| --- | --- |
| `title` | Quiz title. |
| `description` | Explanation of what the quiz covers. |
| `grade` | School grade or target level. |
| `chapterNumber`, `chapterTitle` | Curriculum organization. |
| `era` | Historical era filter. |
| `durationSeconds` | Time limit or expected duration. |
| `playCount` | How many times learners have started it. |
| `rating` | Quality or popularity signal. |
| `contextId` | Historical context the quiz belongs to. |
| `deletedAt` | Soft-delete state. |

Staff/admin manage quizzes and questions. Customers browse quizzes, start attempts, submit answers, and view their own result history.

### Question

A question belongs to a quiz.

Business fields include:

| Field | Meaning |
| --- | --- |
| `content` | Question text. |
| `options` | Multiple-choice options, stored as JSON text. |
| `correctAnswer` | Correct option index or value, depending on current DTO/entity mapping. |
| `orderIndex` | Display order inside the quiz. |
| `explanation` | Optional explanation shown after submission. |
| `deletedAt` | Soft-delete state. |

Business rule: staff can reorder questions without recreating the quiz.

### Quiz Session And Result

A quiz session tracks a learner attempt before and during submission. A quiz result stores the completed outcome.

Key business rules:

| Rule | Meaning |
| --- | --- |
| Start quiz creates a session | The backend returns a `sessionId` for the attempt. |
| Start increases `playCount` | The quiz records learner engagement. |
| Submit checks session validity | The session must exist, belong to the user, not be expired, and not already submitted. |
| Backend scores answers | The client should not be trusted to score itself. |
| Result history is learner-owned | Customers view their own results through `results/me`. |

## 4. Business Workflows

### Workflow A: New Learner Starts Using The Product

```text
1. Learner registers.
2. Learner logs in and receives access/refresh tokens.
3. Learner browses public contexts, characters, documents, and quizzes.
4. Learner opens a character in a historical context.
5. Learner creates a chat session or starts a quiz.
```

Important implementation ownership:

| Step | Owner service |
| --- | --- |
| Register/login/logout/refresh | Java backend |
| Public content browsing | Java backend |
| Chat persistence and authorization | Java backend |
| AI generation | Python AI service, called by Java |
| Quiz scoring and result storage | Java backend |

### Workflow B: Staff Creates Learning Content

```text
1. Staff creates a historical context.
2. Staff attaches historical documents.
3. Staff creates historical characters.
4. Staff maps characters to valid contexts.
5. Staff creates quizzes and questions for the context.
6. Staff publishes content when ready.
```

Business decisions to preserve:

| Decision | Reason |
| --- | --- |
| Context and character can be created independently | Content teams may prepare data in different orders. |
| Mapping is explicit | Prevents invalid chat pairings. |
| Draft content is hidden from customers | Learners should not see incomplete material. |
| Documents are processed by type | Plain text and markdown have different validation/sanitization needs. |

### Workflow C: Learner Chats With A Historical Character

Normal production flow:

```text
Frontend
  -> Java backend chat endpoint
  -> Java validates JWT and session ownership
  -> Java loads character/context/message history
  -> Java sends pre-filled data to Python AI
  -> Python builds Vietnamese roleplay prompt
  -> Python calls LLM provider
  -> Python returns assistant message and suggested questions
  -> Java saves assistant message and updates session
  -> Frontend receives saved response
```

Business rules:

| Rule | Why it exists |
| --- | --- |
| Frontend does not call AI directly | Java owns authentication, persistence, and domain validation. |
| AI receives character/context data | The roleplay answer must stay grounded in the selected learning scenario. |
| Java pre-fills data when possible | Reduces service round trips and avoids callback loops. |
| First user message can trigger title generation | Chat history becomes easier to scan. |
| Suggested questions come from assistant replies | Learners get guided next steps. |

### Workflow D: Learner Takes A Quiz

```text
1. Learner browses quiz list.
2. Learner opens quiz detail.
3. Learner starts quiz.
4. Java creates `QuizSession` and returns `sessionId`.
5. Learner submits answers with `sessionId`.
6. Java validates the session and scores the attempt.
7. Java saves `QuizResult` and `QuizAnswerDetail`.
8. Learner views result history.
```

Business rules:

| Rule | Why it exists |
| --- | --- |
| Session required for submit | Prevents detached answer submissions. |
| Session cannot be submitted twice | Avoids duplicate scores. |
| Backend scores answers | Prevents client-side tampering. |
| Result history belongs to the customer | Personal learning record should not leak to others. |

### Workflow E: Content Is Archived Or Restored

The docs describe a trash/archive model using `deleted_at`.

Business states:

```text
deleted_at IS NULL      -> active row
deleted_at IS NOT NULL  -> soft-deleted / trash row
```

For contexts and characters, draft state adds another layer:

```text
deleted_at IS NOT NULL              -> INACTIVE
deleted_at IS NULL AND isDraft=true -> DRAFT
deleted_at IS NULL AND isDraft=false -> ACTIVE
```

Business rules:

| Rule | Meaning |
| --- | --- |
| Customer content lists should hide deleted rows | Learners should only see usable content. |
| Staff/admin may need deleted rows in management views | Operators need trash review and recovery. |
| Parent soft-delete should cascade to children | If a context is archived, its related documents/quizzes should not stay public accidentally. |
| Hard delete is irreversible | Use only when the business intends permanent removal. |

The service docs include an important technical lesson: global Hibernate `@Where` filters can break foreign-key lazy loading when child records point to soft-deleted parents. The preferred business behavior is role-aware filtering in repositories/services, plus null-safe response mapping.

## 5. Access And Visibility Rules

| Business area | Guest | Customer | Staff | Admin |
| --- | --- | --- | --- | --- |
| Public characters/contexts/documents | Read active/published | Read active/published | Read, plus management views | Read, plus management views |
| Create/update content | No | No | Yes | Yes |
| Character-context mapping | No | No | Yes | Yes |
| Chat | No | Own sessions only | Allowed where endpoints permit | Allowed where endpoints permit |
| Start/submit quiz | No | Yes | Usually management only | Usually management only |
| Manage quizzes/questions | No | No | Yes | Yes |
| Account deactivation | No | Own account | Own account and selected staff endpoint | Depends on endpoint support |

Current security implementation combines broad HTTP rules in `SecurityConfig` with method-level `@PreAuthorize` annotations in controllers. When changing access behavior, check both places.

## 6. AI Business Role

The Python AI service is the LLM brain for character roleplay. It is not the main product API.

AI responsibilities:

| Responsibility | Business value |
| --- | --- |
| Build a roleplay system prompt | Keeps the assistant in character. |
| Use character data | Gives the assistant identity, background, personality, and side. |
| Use historical context data | Grounds answers in the selected event or period. |
| Return suggested questions | Helps learners continue exploring. |
| Generate session titles | Makes chat history readable. |

The prompt is currently designed for Vietnamese historical roleplay. It instructs the assistant to answer as the selected character, stay inside the historical setting, and avoid revealing itself as AI.

Future AI opportunities from the service docs:

| Opportunity | Product value |
| --- | --- |
| Streaming responses | Better chat feel for long answers. |
| Long-term memory or summaries | Better continuity in longer sessions. |
| Retrieval from documents | Answers can cite or use curated documents. |
| Multi-language support | Wider learner audience. |
| Rate limiting | Protects cost and platform stability. |

## 7. Important Business Constraints

### Use Java As The Public Backend

Frontend should call Java. Java calls Python AI when LLM generation is needed.

Do not design new frontend flows that call Python AI directly unless the product architecture changes deliberately.

### Preserve Role Separation

Content creation and quiz management are staff/admin concerns. Learning activity is customer concern. Public browsing is guest/customer concern.

When a feature mixes these roles, clarify whether it is a learner workflow or an operator workflow before writing code.

### Preserve Ownership Checks

Chat sessions, quiz sessions, and quiz results are user-owned. Use "not found" style responses where appropriate so APIs do not leak that another user's private record exists.

### Treat Draft And Trash As Business State

Draft and deleted rows are not just technical flags. They control whether content is safe to show to learners.

### Keep Historical Content Reusable

Characters and contexts are reusable learning objects. Avoid designs that make a character permanently dependent on a single context unless the business explicitly asks for it.

## 8. Source Docs Read For This Guide

Java backend docs:

```text
docs/services/history-talk-backend/authentication-plan.md
docs/services/history-talk-backend/chat-messages-plan.md
docs/services/history-talk-backend/design-pattern-review.md
docs/services/history-talk-backend/document-processor-strategy-plan.md
docs/services/history-talk-backend/enum-fields-plan.md
docs/services/history-talk-backend/implementation-summary.md
docs/services/history-talk-backend/plan-character-context-decoupling.md
docs/services/history-talk-backend/plan-draft-publish-trash-character-context.md
docs/services/history-talk-backend/plan-fix-quiz-context.md
docs/services/history-talk-backend/quiz-plan.md
docs/services/history-talk-backend/README.md
docs/services/history-talk-backend/soft-delete-fk-reference-fix-plan.md
docs/services/history-talk-backend/soft-delete-implementation-plan.md
```

AI backend docs:

```text
docs/services/history-talk-backend-ai/PLAN.md
docs/services/history-talk-backend-ai/README.md
```

Notes on interpreting these docs:

| Source pattern | How to read it |
| --- | --- |
| Older `/v1/...` endpoint examples | Treat as historical plan text; current Java controllers use `/api/v1/...` behind `/Historical-tell`. |
| `REGISTERED` learner role | Treat as historical terminology; current role enum uses `CUSTOMER`. |
| `@Where` soft-delete strategy | Some plans proposed it, later plans explain why manual role-aware filtering is safer. |
| AI `app/` package examples | Historical AI plan structure; current runtime package is `src/history_talk_ai`. |

## 9. First-Day Business Reading Path

Recommended order for a new developer:

1. Read this guide to understand the business model.
2. Read `docs/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md` for the technical structure.
3. Read `docs/services/history-talk-backend/implementation-summary.md` for module history.
4. Read `docs/services/history-talk-backend-ai/PLAN.md` for the chat AI business flow.
5. Inspect current Java controllers to confirm live API paths.
6. Trace one workflow end to end:
   - auth login
   - public content browse
   - chat session and message
   - quiz start and submit

## 10. Questions To Ask Before Changing Features

Use these questions before modifying business behavior:

| Question | Why it matters |
| --- | --- |
| Is this a learner workflow or staff/admin workflow? | Determines permissions and visibility. |
| Should draft content be visible here? | Prevents incomplete content from leaking to customers. |
| Should soft-deleted content be visible here? | Separates public view from management/trash view. |
| Does this action belong to Java or Python AI? | Java owns business rules; Python owns LLM generation. |
| Does this require a valid character-context mapping? | Prevents invalid roleplay sessions. |
| Is the record owned by the current user? | Protects private chat and quiz data. |
| Does this change a frontend contract? | DTO/API changes may require FE coordination. |
| Does this need a migration? | Business fields must be represented safely in DB. |

## 11. One-Screen Mental Model

```text
HistoryTalk teaches history through curated content and AI roleplay.

Content team:
  creates contexts, documents, characters, mappings, and quizzes.

Learner:
  browses public content, chats with mapped characters, and takes quizzes.

Java backend:
  owns users, roles, content, sessions, messages, quizzes, persistence, and API contracts.

Python AI backend:
  owns prompt construction and LLM calls for character replies and chat titles.

Database:
  stores reusable content, learner interactions, audit-friendly soft-delete state, and quiz results.
```

If a future change violates this model, stop and clarify the business requirement before implementing it.
