# HistoryTalk — Implementation Plan: Question Bank, Matching & Fill in the Blank

**Document type:** Implementation plan for the developer/agent who will implement the feature  
**Project:** HistoryTalk  
**Primary backend:** Java Spring Boot 3, PostgreSQL (`historical_schema`)  
**Business source reviewed:** `historytalk-overall-business-spec.md` (BUS-SPEC-OVERALL-01, v2.0)  
**Technical source reviewed:** `historytalk-overall-technical-guide.md` (TECH-GUIDE-OVERALL-01, v2.0)  
**Status:** Planning document; source-code inspection is required before implementation.

---

## 1. Goal

Extend the existing HistoryTalk quiz feature without rewriting the current quiz APIs from scratch. Add:

1. A reusable **Question Bank** so a question can be linked to more than one quiz.
2. **Fill in the Blank** questions.
3. **Matching** questions.
4. A typed, explicit Java model for every JSONB payload introduced by this feature. Do not model JSONB domain data as plain `Object`, `Map<String, Object>`, or an undocumented generic JSON tree.
5. A new Flyway migration to evolve the existing database while preserving existing quiz and answer data.

The existing multiple-choice quiz flow must continue working. New types should be added behind the existing quiz/session workflow wherever the current code allows it.

## 2. Source-derived architecture and business constraints

The two supplied Markdown documents establish the following:

- HistoryTalk's primary business backend is Java Spring Boot 3; it owns quiz domain logic, authorization, and persistence. The Python FastAPI service is for AI roleplay/RAG and should not be used to implement quiz business logic. See the technical guide, sections 1 and 1.1.
- The Java package convention is `com.historytalk` with `controller`, `dto`, `entity`, `enums`, `exception`, `repository`, `security`, `service`, and `util`. Follow the repository's actual existing sub-package conventions rather than imposing a new architecture.
- The business specification describes quizzes as bound to a `historical_context`, with instant scoring, historical explanations, XP rewards, and contribution to quests.
- RBAC includes quiz participation for customers, school students, teachers, staff/content admins, and admins; content management roles must be checked against existing authorization patterns.
- Soft deletion uses `deleted_at` and **manual service-layer filtering**. Do not assume `@Where` / `@SQLDelete` automatically filters records.
- PostgreSQL stores core quiz data in `historical_schema`.

These documents do **not** provide actual Java source files, exact endpoint signatures, current DTO names, current Flyway version, JPA/Hibernate JSONB mapping configuration, the format of the existing `question.options` text, the current `correct_answer` indexing convention, or the exact XP/quest award implementation. Those items are explicitly marked for source-code inspection below. Do not guess them.

## 3. Current database baseline

The schema supplied in the conversation currently includes:

### `historical_schema.quiz`
- `quiz_id` UUID primary key
- `title`, `context_id`, `created_by`, `level`, `is_published`
- `created_at`, `updated_at`, `deleted_at`

### `historical_schema.question`
- `question_id` UUID primary key
- `quiz_id` UUID **NOT NULL**, FK to `quiz`
- `content` TEXT **NOT NULL**
- `options` TEXT
- `correct_answer` INTEGER
- `explanation` TEXT
- `created_at`, `updated_at`, `deleted_at`

### `historical_schema.quiz_session`
- `session_id`, `quiz_id`, `uid`
- `limited_time`, `start_time`, `end_time`, `score`
- timestamps and `deleted_at`

### `historical_schema.quiz_answer_detail`
- `detail_id`, `question_id`, `session_id`
- `selected_option` INTEGER **NOT NULL**
- `is_correct` BOOLEAN
- `created_at`, `deleted_at`

The current schema links each question directly to one quiz and stores a selected option as an integer. These shapes do not naturally represent reusable questions, text answers, or multiple matching pairs. The migration below adds a many-to-many link table and typed JSONB payload columns while retaining legacy columns for compatibility.

## 4. Required source-code inspection before coding

The implementer must inspect the actual repository and record findings in the PR/implementation notes before finalizing changes.

### 4.1 Existing quiz APIs and service flow
Find and read:
- Current quiz controller endpoints and route naming.
- Current question creation/update/list endpoints.
- Current start-session, submit-answer, finish-session, and result endpoints.
- Existing request/response DTOs and validation rules.
- Whether answers are submitted one at a time or in batches.
- Where score is calculated and persisted.
- Where XP and quest progress are awarded, and whether that flow is idempotent.
- Whether result APIs expose correct answers/explanations immediately or only after submission/completion.

**Implementation rule:** Reuse existing endpoints, session lifecycle, authentication, authorization, and response envelopes when practical. Add fields compatibly and branch by `question_type`; do not create a second parallel quiz engine unless the existing architecture makes reuse impossible. If a new endpoint is necessary, explain why in code comments/PR notes.

### 4.2 Persistence and JSONB mapping
Inspect:
- `pom.xml` dependencies and versions.
- Spring Boot and Hibernate versions actually resolved.
- Existing JSONB fields/entities, if any, and how they are mapped.
- Flyway configuration and migration directory.
- The latest applied and latest checked-in migration version.

Use the project's established JSONB mapping mechanism if it supports typed payload classes. If none exists, choose a mapping compatible with the actual Hibernate version. Do not add a JSON library or Hibernate type dependency until checking what is already present.

**Hard requirement:** JSONB data must have named, typed Java payload classes and explicit fields. Do not use `Object`, `Map<String, Object>`, or an untyped JSON tree as the business model. A JSON library may be used internally for serialization, but it must deserialize/serialize to the explicit classes below.

### 4.3 Legacy question data
Inspect how `question.options` is serialized and how `correct_answer` indexes that structure. Confirm:
- Whether options are JSON, comma-separated text, or another format.
- Whether `correct_answer` is zero-based or one-based.
- How empty options, deleted questions, and historical answer records are handled.

Do not automatically transform legacy `options` into the new `question_data` until the actual stored format and index convention are verified. The first migration intentionally preserves legacy fields and data.

### 4.4 Existing conventions
Inspect entity naming, Lombok/Java record usage, enum naming, validation style, exception handling, API versioning, soft-delete filters, transaction boundaries, and test structure. Follow existing project conventions.

## 5. Target data model

### 5.1 Keep existing tables and add compatible fields

Add to `question`:
- `question_type VARCHAR(40) NOT NULL DEFAULT 'MULTIPLE_CHOICE'`
- `question_data JSONB NULL` initially, for typed per-type payload data
- Make `quiz_id` nullable to permit a question to exist in the bank without being assigned to a quiz. Existing rows retain their current `quiz_id`; legacy APIs may continue to use it during migration.

Keep `options` and `correct_answer` during the compatibility period. Do not remove them in this feature rollout.

Add to `quiz_answer_detail`:
- `answer_data JSONB NULL` — typed answer payload
- `score_earned NUMERIC(8,2) NULL` — earned points for partial-credit scoring
- `response_time_ms INTEGER NULL` — optional response duration
- `attempt_number INTEGER NOT NULL DEFAULT 1` — only if the source-code review confirms that multiple attempts are needed; otherwise omit it from the migration and plan. The initial implementation should not invent retry behavior.

Change `quiz_answer_detail.selected_option` to nullable only after verifying old application code and old data. Keep the column for legacy multiple-choice submissions during transition.

Add to `quiz_question`:
- `quiz_id UUID NOT NULL` FK to `quiz`
- `question_id UUID NOT NULL` FK to `question`
- `order_index INTEGER NOT NULL DEFAULT 0`
- `points NUMERIC(8,2) NOT NULL DEFAULT 1`
- `created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP`
- Primary key `(quiz_id, question_id)` so a question can be attached only once to a given quiz.

The migration should backfill `quiz_question` from existing non-deleted `question` rows (or all rows if needed to preserve historic relationships; confirm the project's soft-delete and quiz display semantics first). For safe historical preservation, prefer copying all rows with a non-null `quiz_id`, including soft-deleted rows, then keep soft-delete filtering in the service layer. Use a deterministic `order_index` based on `created_at, question_id` for the initial backfill; the implementer must confirm whether an existing ordering field or explicit order convention already exists.

### 5.2 Typed JSONB payloads — mandatory Java classes

Use the exact project package conventions, likely under `com.historytalk.entity.quizdata` or a similarly scoped package after inspection. The names below are target concepts; adapt package paths to the existing project.

#### Question type enum
`QuestionType`:
- `MULTIPLE_CHOICE`
- `FILL_IN_BLANK`
- `MATCHING`

Do not add Ordering, True/False, or other types in this task unless already present and explicitly included in scope. The enum should be persisted as a string.

#### `MultipleChoiceQuestionData`
Explicit fields:
- `List<QuestionOption> options`
- `List<String> correctOptionIds` or another strongly typed representation selected after checking legacy answer conventions.

`QuestionOption`:
- `String id`
- `String text`

For the first migration, keep the existing legacy `options` and `correct_answer` fields as the source of truth for old questions until the conversion is implemented and verified. The class describes the target typed payload, not an assumption about the current stored text format.

#### `FillInBlankQuestionData`
Explicit fields:
- `List<String> acceptedAnswers`
- `boolean caseSensitive`
- `boolean trimWhitespace`
- Optional explicit normalization policy if required, represented by a named enum rather than free-form `Object`.

Initial scope is one blank/one answer field per question unless source requirements confirm multi-blank support. If multi-blank is needed, model it explicitly, for example `List<BlankDefinition> blanks`, where `BlankDefinition` has `String id`, `List<String> acceptedAnswers`, `boolean caseSensitive`, and `boolean trimWhitespace`. Do not silently mix single-blank and multi-blank shapes.

#### `MatchingQuestionData`
Explicit fields:
- `List<MatchingItem> leftItems`
- `List<MatchingItem> rightItems`
- `List<MatchingPair> correctPairs`

`MatchingItem`:
- `String id`
- `String text`

`MatchingPair`:
- `String leftId`
- `String rightId`

Validate that item IDs are unique within each side, every correct pair references an existing item, and pair definitions follow the intended one-to-one/many-to-one rule. Default to one-to-one only if that is the product decision confirmed by the implementer/PO.

#### Typed answer payloads
Do not store answer JSON as a generic object. Define:
- `MultipleChoiceAnswerData`: `List<String> selectedOptionIds`
- `FillInBlankAnswerData`: `List<String> answers` (single-blank questions contain exactly one value)
- `MatchingAnswerData`: `List<MatchingPair> pairs`

If the existing Jackson/Hibernate setup supports polymorphic JSONB mapping, use a typed sealed interface or interface with explicit Jackson subtype metadata. Otherwise, map the database JSONB column through a project-compatible converter/mapper that dispatches by `question_type` and returns one of the explicit classes. Keep persistence mapping details consistent with the resolved Hibernate version.

#### Example payloads for API documentation and tests

Fill in the Blank question data:
```json
{
  "acceptedAnswers": ["938", "năm 938"],
  "caseSensitive": false,
  "trimWhitespace": true
}
```

Matching question data:
```json
{
  "leftItems": [
    {"id": "L1", "text": "Ngô Quyền"},
    {"id": "L2", "text": "Trần Hưng Đạo"}
  ],
  "rightItems": [
    {"id": "R1", "text": "Chiến thắng Bạch Đằng năm 938"},
    {"id": "R2", "text": "Kháng chiến chống Nguyên-Mông"}
  ],
  "correctPairs": [
    {"leftId": "L1", "rightId": "R1"},
    {"leftId": "L2", "rightId": "R2"}
  ]
}
```

Matching answer data:
```json
{
  "pairs": [
    {"leftId": "L1", "rightId": "R1"},
    {"leftId": "L2", "rightId": "R2"}
  ]
}
```

These are payload examples, not evidence that the existing API currently uses these shapes. Document the final request/response contract after inspecting existing DTOs.

### 5.3 Question bank relationship rules
- A bank question can have `question.quiz_id = NULL`.
- A quiz uses questions through `quiz_question`.
- During transition, old questions may retain their original `question.quiz_id`; the new link table is the canonical relationship for new bank-aware code.
- When adding an existing question to a quiz, validate permission to manage that quiz and question, question not soft-deleted, question/context scope compatibility, and duplicate link.
- The business spec says quizzes are context-bound. Decide whether a bank question's `context_id` must equal `quiz.context_id`, or whether cross-context reuse is allowed. Do not infer this rule from the schema; inspect existing business validation and get a product decision.
- Removing a question from a quiz should delete/unlink `quiz_question`, not delete the bank question.
- Deleting a bank question should follow the project's `deleted_at` convention and must not cascade-delete historical answer details or sessions.

## 6. Flyway migration plan

### 6.1 Migration file
Create a new SQL migration in the actual Flyway migration directory, named with the next unused version, for example:

`V<next-version>__quiz_question_bank_and_new_question_types.sql`

Do **not** use the example literally. Inspect the latest migration and choose the next version that follows the project's versioning convention. Do not edit an already-applied migration.

The accompanying draft migration file is named `V202610151400__quiz_question_bank_and_new_question_types.sql` only as a proposal. Before applying it, rename its version to the next valid version in the repository.

### 6.2 Intended migration operations
1. Add `question_type` with default `MULTIPLE_CHOICE`, so existing rows remain classified as multiple-choice.
2. Add nullable `question_data JSONB`.
3. Create `quiz_question` with FKs, primary key, points, order, and creation timestamp.
4. Backfill `quiz_question` from current `question.quiz_id` relationships.
5. Make `question.quiz_id` nullable to support unassigned Question Bank entries, after confirming no database/view/application constraints prohibit it.
6. Add nullable `answer_data JSONB`, nullable `score_earned NUMERIC(8,2)`, and nullable `response_time_ms INTEGER` to `quiz_answer_detail`.
7. Keep legacy `options`, `correct_answer`, and `selected_option` for compatibility. Make `selected_option` nullable only if source inspection confirms the current code and constraints can tolerate it; otherwise defer this alteration to a later migration when the new submit-answer flow is deployed.
8. Add sensible check constraints for `question_type`, non-negative `order_index`, and non-negative `points` if consistent with project conventions. Ensure `question_data` is not required for old rows during the compatibility stage.
9. Add indexes for `quiz_question(question_id)`, `quiz_question(quiz_id, order_index)`, and any query patterns confirmed from repository inspection.
10. Keep all changes in `historical_schema`; qualify schema names if that is the project's established migration convention.

### 6.3 Migration safety
- Review Flyway `baseline`, schema, and migration naming configuration before running.
- Test against a disposable database restored from a recent schema/data snapshot.
- Verify row counts before and after backfill.
- Verify every existing question with a non-null `quiz_id` has a corresponding `quiz_question` row.
- Verify all existing answer details and sessions remain present.
- Do not add `ON DELETE CASCADE` to historical records without reviewing the project's data-retention rules.
- Migration rollback should be handled through a new forward migration if necessary; do not assume Flyway supports automatic undo.

## 7. API strategy — reuse first

Exact paths must be taken from source code. The following describes behavior, not a mandate to rename current endpoints.

### 7.1 Reuse current quiz management endpoints
- Extend existing create/update question requests to accept `questionType` and a typed `questionData` payload.
- Preserve current multiple-choice fields and defaults for old clients during a compatibility period.
- Reuse existing quiz list/detail APIs; include question type and type-specific question content while excluding answer keys from learner-facing responses.
- Reuse the existing quiz creation/editing flow to attach bank questions. Add a dedicated “attach existing question” endpoint only if the current API cannot represent this action cleanly.
- Reuse existing session start, submit-answer, finish, and result endpoints.
- Dispatch answer validation and grading by `question_type` in the service layer. Do not let the frontend submit `isCorrect`, score, or authoritative answer keys.

### 7.2 Minimum new/extended behavior
- Search/list Question Bank with filters such as `questionType`, context, and text query, using the existing pagination/filter convention.
- Create and update bank questions without requiring a quiz assignment.
- Attach an existing question to a quiz.
- Unlink a question from a quiz without deleting the question.
- Submit typed answer data for Fill in the Blank and Matching.
- Return per-question earned points and explanation according to the existing result contract.

Do not create duplicate “v2” endpoints merely to change DTO shape unless a breaking contract is unavoidable. If a breaking change is required, keep the old route/contract operational and document the migration path.

## 8. Scoring and validation

### Fill in the Blank
- Trim surrounding whitespace when configured.
- Apply case normalization only when configured.
- Compare against explicit accepted answers.
- Do not automatically remove Vietnamese diacritics unless explicitly selected as a product rule.
- Do not use fuzzy/AI grading in the first release; it is non-deterministic and may accept historically incorrect answers.
- Return correctness and explanation according to the current feedback timing policy.

### Matching
- Validate submitted item IDs belong to the question.
- Reject malformed/duplicate pair IDs as appropriate.
- Calculate partial credit per correct pair if the product chooses partial credit; otherwise use all-or-nothing. Confirm this decision before implementation.
- Do not accept client-submitted score or correctness.

### Shared rules
- Ensure the question is attached to the quiz in the current session.
- Ensure the session belongs to the authenticated user unless the existing product explicitly supports delegated/teacher review flows.
- Reject answers after session completion or expiry according to existing session rules.
- Keep scoring and persistence transactional.
- Prevent duplicate submissions from awarding duplicate score/XP/quest progress.
- Preserve existing XP and quest behavior. Extend it only through the existing award mechanism.

## 9. Frontend contract (coordinate with the existing frontend owner)

The supplied technical guide says the frontend communicates with Java Spring Boot, not directly with the Python AI service. It does not specify frontend framework or current component architecture.

Minimum UI:
- Question Bank list/search/filter.
- Create/edit form that renders fields based on question type.
- Quiz editor action to attach an existing bank question.
- Fill in the Blank input component.
- Matching interaction (select pairs or drag-and-drop; choose based on current UI library and accessibility constraints).
- Shared feedback/result view.

Frontend must render only the learner-safe question representation. Correct answers must not be present in browser payloads before the allowed reveal point.

## 10. Testing checklist

### Database and migration
- Migration runs on a database with existing quiz data.
- Backfill creates links for all legacy questions with `quiz_id`.
- Legacy data and answer history remain intact.
- New bank questions can exist with no quiz link.
- Soft-deleted questions are filtered consistently with the current service-layer convention.

### Multiple Choice regression
- Existing API clients can still create/play/submit/finish old multiple-choice quizzes.
- Legacy option indexing is preserved.
- Old result history is readable.

### Fill in the Blank
- Exact match, case-insensitive match, whitespace normalization, multiple accepted answers, blank answer, invalid payload, and Vietnamese diacritics behavior.
- API does not reveal accepted answers prematurely.

### Matching
- All pairs correct, some pairs correct, all wrong, missing pair, duplicate pair, unknown item ID, and cross-question item ID.
- Confirm one-to-one versus many-to-one and partial-credit policy.

### Question Bank
- Create unassigned question.
- Attach question to quiz.
- Prevent duplicate link.
- Unlink without deleting source question.
- Permission/context validation.
- Reuse one question in two quizzes.
- Editing a shared question follows the decided shared-edit/versioning policy.

### Session, XP and quests
- Score is persisted correctly.
- Session finish is idempotent.
- XP and quest progress are awarded once, not once per retry.
- Concurrent/duplicate submit requests do not double-score.

## 11. Suggested implementation order

1. **Repository reconnaissance:** Inspect the source-code items in section 4; document endpoint names, DTOs, entity relationships, JSONB mapping and Flyway version.
2. **Data contract decision:** Confirm single-blank vs multi-blank, matching one-to-one vs many-to-one, partial credit, cross-context reuse, shared edit/versioning, and feedback timing.
3. **Flyway migration:** Add fields/link table and preserve legacy data. Test migration before changing application code.
4. **Typed Java payloads:** Add `QuestionType`, explicit question-data classes, explicit answer-data classes, serializer/deserializer and validation.
5. **Question Bank services:** Add search, create/update, attach/unlink behavior by extending current service/controller patterns.
6. **Session integration:** Extend current answer submission and grading paths for Fill in the Blank and Matching; retain Multiple Choice behavior.
7. **Frontend integration:** Reuse current quiz pages and result flow; add type-specific renderers.
8. **Regression/integration tests:** Validate migration, old API compatibility, security, scoring, XP and quests.
9. **Staged rollout:** Deploy migration first where compatible, then application changes; verify production-like data before considering legacy-column cleanup.
10. **Future cleanup (not part of this release):** Only after usage is stable, consider a later migration to remove legacy `options`, `correct_answer`, or `question.quiz_id`. Do not remove them in this task.

## 12. Decisions and unknowns that must remain explicit

The implementation agent must not silently guess any item below. Read the source code and, where business intent is not encoded, record the decision or ask the product owner.

- **Flyway version/path:** Not provided in the Markdown guides; inspect repository.
- **Current endpoints/DTOs:** Not provided; inspect controllers and DTOs.
- **Legacy options serialization and index convention:** Not provided; inspect data and code before conversion.
- **JSONB mapping dependency/version:** Not provided; inspect `pom.xml`, Hibernate version and existing entities.
- **Question ordering:** No ordering field exists in the supplied `question` table; inspect frontend/API behavior.
- **Context reuse rule:** Business docs say quizzes are context-bound but do not specify cross-context question reuse.
- **Question edit semantics:** Shared edits vs immutable versioning/snapshot is not specified.
- **Matching scoring:** Partial credit vs all-or-nothing is not specified.
- **Matching cardinality:** One-to-one vs one-to-many is not specified.
- **Blank count:** One blank vs multiple blanks is not specified; default to one for MVP unless requirements say otherwise.
- **Feedback timing:** Whether correct answers are revealed immediately or after the quiz is not specified.
- **XP/quest award idempotency:** Business docs say quiz completion awards XP and contributes to quests, but implementation details are absent; inspect the actual service flow.
- **School/tenant scoping:** Supplied schema has `school_id` on contexts and users but not directly on quiz/question. Inspect existing service-level scope checks before permitting cross-school reuse.
- **Question snapshots:** The required degree of historical result reproducibility is not specified. Decide whether to snapshot question content/answer key for a session or completed attempt.
- **Soft-delete behavior:** Follow current manual service-layer filtering and verify how quiz/question/session queries handle deleted rows.
- **Frontend framework and drag/drop library:** Not specified; inspect the frontend repository and reuse its conventions.
- **Current DB schema vs supplied schema:** Treat the SQL in the user request as a schema reference, not a guarantee that the deployed database exactly matches it. Check actual migrations and database before applying changes.

## 13. Definition of Done

- Existing Multiple Choice flows continue to work without a forced client rewrite.
- Questions can be managed in a bank and attached to multiple quizzes.
- Fill in the Blank and Matching can be created, delivered, answered, graded, and included in results.
- Every JSONB payload has explicit typed Java classes with documented fields and validation.
- The migration preserves legacy questions, answer details, and quiz sessions.
- Correct answers are not exposed before the allowed reveal point.
- RBAC, soft-delete, context/school scope, XP, and quest behaviors follow existing implementation patterns.
- Unit, integration, migration, and regression tests pass.
- All unknowns in section 12 have been resolved or documented as accepted decisions.
