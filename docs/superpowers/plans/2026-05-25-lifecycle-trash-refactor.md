# Lifecycle Trash Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `is_active`, use `is_published` for draft/published visibility, use `deleted_at` for trash/soft delete, and add content-management trash restore/hard-delete APIs.

**Architecture:** Content lifecycle will be derived from persisted fields instead of a separate active flag: `deleted_at != null` means `INACTIVE`, `is_published=false` means `DRAFT`, and `is_published=true` means `ACTIVE`. `Character` and `HistoricalContext` keep `is_published`; `Quiz` gains `is_published` because its current `is_active=false` creation flow is acting as unpublished visibility. Trash operations are centralized under a SYSTEM_ADMIN-only controller/service.

**Tech Stack:** Spring Boot, Java 21, Spring Data JPA, Flyway, PostgreSQL.

---

### Task 1: Flyway Lifecycle Migration

**Files:**
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V13__lifecycle_trash_refactor.sql`

- [ ] Add `is_published BOOLEAN NOT NULL DEFAULT FALSE` to `historical_schema.quiz`.
- [ ] Backfill quiz publish state from current active state: `is_published = COALESCE(is_active, false)`.
- [ ] Drop `is_active` from lifecycle tables after backfill.
- [ ] Add trash indexes on `deleted_at`.
- [ ] Add case-insensitive lookup indexes for content names/titles that include trashed rows, while enforcing duplicate prevention in service validation.

### Task 2: Add Managed Status Enum

**Files:**
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/enums/ContentStatus.java`

- [ ] Add enum values `ACTIVE`, `DRAFT`, `INACTIVE`.
- [ ] Use this enum in response DTOs instead of ad hoc strings.
- [ ] Keep JSON values unchanged for frontend compatibility.

### Task 3: Refactor Entities and DTOs

**Files:**
- Modify: `Character.java`, `HistoricalContext.java`, `Quiz.java`, related child entities with `is_active`
- Modify: `CharacterResponse.java`, `HistoricalContextResponse.java`, `QuizStaffResponse.java`
- Modify: `CreateQuizRequest.java`, `UpdateQuizRequest.java`

- [ ] Remove `isActive` fields.
- [ ] Add `isPublished` to `Quiz`.
- [ ] Add `status: ContentStatus` to response DTOs.
- [ ] Keep `deletedAt` in staff/admin responses.

### Task 4: Refactor Query Rules

**Files:**
- Modify: `CharacterRepository.java`
- Modify: `HistoricalContextRepository.java`
- Modify: `QuizRepository.java`
- Modify: supporting repositories/count queries

- [ ] Customer queries use `deletedAt IS NULL AND isPublished = true`.
- [ ] Staff management queries use `deletedAt IS NULL` and include draft content.
- [ ] Trash queries use `deletedAt IS NOT NULL`.
- [ ] Duplicate checks do not filter out trashed rows.

### Task 5: Refactor Services

**Files:**
- Modify: `CharacterServiceImpl.java`
- Modify: `HistoricalContextServiceImpl.java`
- Modify: `QuizServiceImpl.java`
- Modify: service interfaces

- [ ] Remove toggle-active lifecycle logic.
- [ ] Soft delete only sets `deletedAt = now`.
- [ ] Restore only clears `deletedAt`.
- [ ] Derive status via `deletedAt` and `isPublished`.
- [ ] Do not soft-delete or hard-delete `Character` when deleting a `HistoricalContext`; characters are independent and related through `context_character_mapping`.

### Task 6: Add SYSTEM_ADMIN Trash Flow

**Files:**
- Create: `dto/trash/BulkTrashActionRequest.java`
- Create: `dto/trash/BulkTrashActionResponse.java`
- Create: `controller/trash/SystemTrashController.java`
- Create: `service/trash/TrashService.java`
- Create: `service/trash/TrashServiceImpl.java`

- [ ] Add list trash endpoints for characters, historical contexts, and quizzes.
- [ ] Add restore endpoints for one or multiple ids.
- [ ] Add hard-delete endpoints for one or multiple ids.
- [ ] Require `hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')`.
- [ ] Only restore/hard-delete items where `deletedAt IS NOT NULL`.

### Task 7: Hard Delete Rules

**Historical Context:**
- Delete context documents/vector chunks.
- Delete context-character mapping rows.
- Delete quizzes under context and their quiz children.
- Delete chat sessions/messages pointing to the context.
- Do not delete mapped characters.

**Character:**
- Delete character documents/vector chunks.
- Delete context-character mapping rows.
- Delete chat sessions/messages pointing to the character.
- Do not delete historical contexts.

**Quiz:**
- Delete quiz answer details, sessions, questions, then quiz.

### Task 8: Validation

- [ ] `mvn -q test`
- [ ] `mvn -q -DskipTests compile`
- [ ] Verify duplicate creation fails even when matching content is in trash.
- [ ] Verify customer lists hide draft and trashed content.
- [ ] Verify SYSTEM_ADMIN can restore and hard-delete trashed items.
