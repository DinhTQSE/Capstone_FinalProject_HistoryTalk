# Active List APIs And Character Model URL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hide soft-deleted records from get-all APIs for character, quiz, and historical context, remove `deletedAt` from those get-all response DTOs, and add `modelUrl` support to character.

**Architecture:** Keep soft-delete state in entities and trash APIs, but force public/admin list APIs to use `deleted_at IS NULL`. Add `model_url` to the character table and carry it through entity, create/update DTOs, response DTO, and service mapping.

**Tech Stack:** Spring Boot 3.2.5, Java 21, Spring Data JPA, Lombok, Flyway, JUnit 5, Mockito, Jackson.

---

### Task 1: Add Regression Tests For List Filtering And DTO Serialization

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/character/CharacterServiceImplTest.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/historicalContext/HistoricalContextServiceImplTest.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/test/java/com/historytalk/service/quiz/QuizServiceImplTest.java`

- [ ] **Step 1: Add Character service tests**

Add tests that verify staff/admin get-all calls pass `includeDeleted=false`, `CharacterResponse` JSON omits `deletedAt`, and `modelUrl` is mapped from the entity.

- [ ] **Step 2: Run Character tests and verify red**

Run:

```powershell
mvn -q -Dtest=CharacterServiceImplTest test
```

Expected: FAIL because current get-all passes `includeDeleted=true`, DTO still serializes `deletedAt`, and `modelUrl` does not exist yet.

- [ ] **Step 3: Add HistoricalContext service tests**

Add tests that verify paginated and simple get-all calls pass `includeDeleted=false`, and `HistoricalContextResponse` JSON omits `deletedAt`.

- [ ] **Step 4: Run HistoricalContext tests and verify red**

Run:

```powershell
mvn -q -Dtest=HistoricalContextServiceImplTest test
```

Expected: FAIL because staff/admin context list currently includes deleted records and DTO still serializes `deletedAt`.

- [ ] **Step 5: Add Quiz service tests**

Add tests that verify staff get-all calls pass `includeDeleted=false`, and `QuizStaffResponse` JSON omits `deletedAt`.

- [ ] **Step 6: Run Quiz tests and verify red**

Run:

```powershell
mvn -q -Dtest=QuizServiceImplTest test
```

Expected: FAIL because staff quiz list currently passes `includeDeleted=true` and DTO still serializes `deletedAt`.

### Task 2: Implement Active-Only Get-All Behavior

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/character/CharacterServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/HistoricalContextServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/quiz/QuizServiceImpl.java`

- [ ] **Step 1: Force character get-all endpoints to exclude deleted records**

Set `includeDeleted` to `false` in `getAllCharacters()` and `getCharactersByContext()`.

- [ ] **Step 2: Force historical context get-all endpoints to exclude deleted records**

Set `includeDeleted` to `false` in `getAllContexts()` and `getAllContextsSimple()`.

- [ ] **Step 3: Force quiz staff get-all endpoint to exclude deleted records**

Pass `false` as the `includeDeleted` parameter to `quizRepository.findAllForStaff(...)`.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
mvn -q -Dtest=CharacterServiceImplTest,HistoricalContextServiceImplTest,QuizServiceImplTest test
```

Expected: filtering tests pass; serialization/modelUrl tests still fail until Task 3 and Task 4 are implemented.

### Task 3: Remove `deletedAt` From Main Get-All DTOs

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/character/CharacterResponse.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/historicalContext/HistoricalContextResponse.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/quiz/QuizStaffResponse.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/character/CharacterServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/HistoricalContextServiceImpl.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/quiz/QuizServiceImpl.java`

- [ ] **Step 1: Remove DTO fields**

Remove `deletedAt` from `CharacterResponse`, `HistoricalContextResponse`, and `QuizStaffResponse`.

- [ ] **Step 2: Remove builder mappings**

Remove `.deletedAt(...)` calls from the three service mappers.

- [ ] **Step 3: Keep trash API behavior unchanged**

Do not modify `TrashItemResponse` or `TrashServiceImpl`; trash remains the place to expose deletion timestamps.

### Task 4: Add `modelUrl` To Character

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/character/Character.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/character/CreateCharacterRequest.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/character/UpdateCharacterRequest.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/character/CharacterResponse.java`
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/character/CharacterServiceImpl.java`
- Create: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V14__add_character_model_url.sql`

- [ ] **Step 1: Add entity field**

Add `private String modelUrl;` mapped to `model_url` with length 500.

- [ ] **Step 2: Add request fields**

Add `modelUrl` to create/update request DTOs with `@JsonProperty("modelUrl")` and `@Size(max = 500)`.

- [ ] **Step 3: Add response field**

Add `modelUrl` to `CharacterResponse` with `@JsonProperty("modelUrl")`.

- [ ] **Step 4: Map create/update/response**

Set `modelUrl` during create, update it when provided, and include it in `mapToResponse()`.

- [ ] **Step 5: Add Flyway migration**

Create:

```sql
ALTER TABLE historical_schema."character"
    ADD COLUMN IF NOT EXISTS model_url VARCHAR(500);
```

### Task 5: Validate And Document Implementation

**Files:**
- Create: `docs/services/history-talk-backend/active-list-and-character-model-url-implementation-summary.md`

- [ ] **Step 1: Run focused tests**

Run:

```powershell
mvn -q -Dtest=CharacterServiceImplTest,HistoricalContextServiceImplTest,QuizServiceImplTest test
```

Expected: PASS.

- [ ] **Step 2: Run compile validation**

Run:

```powershell
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 3: Write implementation summary**

Document changed behavior, touched modules, migration, and validation evidence in `docs/services/history-talk-backend/active-list-and-character-model-url-implementation-summary.md`.

