# Plan: Fix Quiz–HistoricalContext 1:N and Soft Delete

Objective: Ensure each HistoricalContext owns many Quizzes (required FK), keep Quiz soft delete with `deleted_at`, and align APIs/DB.

## Steps
1) Entity mapping
- HistoricalContext: confirm `@OneToMany(mappedBy = "historicalContext", fetch = LAZY)` for quizzes.
- Quiz: keep `@ManyToOne @JoinColumn(name = "context_id", nullable = false)` to HistoricalContext.
- Remove accidental cascade from context to quizzes beyond needed; rely on FK + service rules.

2) Soft delete on Quiz
- Add `deletedAt TIMESTAMP` field if missing; annotate Quiz with `@SQLDelete` and `@Where(clause = "deleted_at IS NULL")` (similar to Question).
- Migration adds column if absent and leaves values NULL.

3) Repository alignment
- QuizRepository relies on `@Where` for filtering; ensure no native/JPQL bypass soft delete.
- Add optional method to list by context: `findByHistoricalContextContextId(UUID contextId, Pageable)` using implicit `deleted_at IS NULL`.

4) Service layer
- QuizServiceImpl: validate context exists on create/update; enforce ownership or ADMIN on update/delete; ensure list/detail use non-deleted quizzes only; optionally expose list-by-context.
- Delete uses repository.delete(quiz) to trigger soft delete.

5) Controllers & DTOs
- StaffQuizController: POST/PUT require `contextId`; consider GET `/staff/contexts/{contextId}/quizzes` if needed.
- QuizController (public): list/detail only non-deleted quizzes.
- DTOs (CreateQuizRequest, QuizStaffResponse, QuizCustomerResponse): keep `contextId/contextTitle`; do not expose deletedAt.

6) Migration (Flyway)
- Create `V6__quiz_soft_delete_and_context_fk.sql`:
  - `ALTER TABLE historical_schema.quiz ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;`
  - Backfill NULL.
  - Ensure `context_id` is NOT NULL and FK references `historical_context` (decide ON DELETE CASCADE/RESTRICT per current policy; default cascade aligns with orphanRemoval on entity).
  - Add index on `(context_id, deleted_at)` for list-by-context.

7) Verification
- Build: `mvn -q -DskipTests compile`.
- Manual:
  1. Create quiz with valid context → success; invalid context → 404.
  2. Delete quiz → no longer appears in staff/public lists/detail; record remains with deleted_at set.
  3. Ensure questions stay soft-deleted with quiz; queries respect @Where.
  4. List by context returns only non-deleted quizzes.
  5. Start/submit quiz fails with 404 if quiz soft-deleted.

## Decisions
- Keep Quiz→HistoricalContext required (NOT NULL FK).
- Soft delete marker on Quiz via `deleted_at`; reuse existing Question soft delete.
- Public/staff listings implicitly filter deleted via entity @Where.
- Optional endpoint list-by-context depending on FE need.
