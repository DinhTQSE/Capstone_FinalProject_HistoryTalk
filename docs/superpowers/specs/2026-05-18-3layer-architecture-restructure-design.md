# 3-Layer Architecture Restructure Design (Java + Python Services)

Date: 2026-05-18  
Status: Approved for planning  
Scope: Structure and naming refactor only (no functional feature changes)

## 1. Objective

Standardize both independent backend services to a strict 3-layer architecture with professional package naming and centralized documentation management.

Services:
- Java service: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend`
- Python service: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI`

## 2. Constraints and Decisions

- The Java and Python codebases remain independent services.
- Each service uses strict 3-layer separation:
  - Presentation: controllers/routers + request/response DTOs/schemas
  - Application: business services/interfaces/use-case orchestration
  - Data Access: repositories/entities/persistence models/gateways
- Cross-cutting concerns stay outside the 3 layers in a dedicated shared area (`common` / `infrastructure`).
- Full naming normalization is required:
  - Java base package: `com.historyTalk` -> `com.historytalk`
  - Python package root: `app` -> `history_talk_ai`
  - Use lowercase semantic package names; use snake_case where needed for multi-word names.
- Documentation and instruction markdown files must be isolated under root `/docs`.

## 3. Current vs Proposed Structure

### 3.1 Java Service

Current (simplified):

```text
src/main/java/com/historyTalk/
  config/
  controller/{authentication,character,chat,historicalContext,quiz}
  dto/{authentication,character,chat,exception,historicalContext,quiz,user}
  entity/{character,chat,enums,historicalContext,quiz,user}
  exception/
  mapper/{character,user}
  repository/
  security/
  service/{authentication,character,chat,historicalContext,quiz}
  utils/{authentication,...}
```

Proposed (simplified):

```text
src/main/java/com/historytalk/
  presentation/
    authentication/{controller,dto}
    character/{controller,dto}
    chat/{controller,dto}
    historical_context/{controller,dto}
    quiz/{controller,dto}
    common/dto
  application/
    authentication/{service,mapper}
    character/{service,mapper}
    chat/service
    historical_context/{service,strategy}
    quiz/service
  dataaccess/
    user/{entity,repository}
    character/{entity,repository}
    chat/{entity,repository}
    historical_context/{entity,repository}
    quiz/{entity,repository}
    shared/entity/enums
  common/
    config/
    security/
    exception/
    util/
    integration/ai/
```

### 3.2 Python Service

Current (simplified):

```text
app/
  config.py
  main.py
  routers/chat.py
  models/{chat.py,character.py,historical_context.py}
  services/{java_client.py,llm_service.py,prompt_builder.py}
```

Proposed (simplified):

```text
src/history_talk_ai/
  presentation/
    chat/{router.py,schemas.py}
  application/
    chat/service.py
    prompting/prompt_builder.py
  dataaccess/
    java_backend/{client.py,schemas.py}
  common/
    config/settings.py
    errors/
  main.py
```

### 3.3 Documentation Location

Current:
- Java local docs: `history-talk-backend/docs/*.md`
- Python local docs: `history-talk-backend-AI/docs/*.md`
- Root docs: `docs/...`

Proposed:

```text
docs/
  services/
    history-talk-backend/
      *.md
    history-talk-backend-ai/
      *.md
  superpowers/
    specs/
```

## 4. Package Naming Standard

Rules:
- Use lowercase package/module names.
- Avoid camelCase package directories (`historicalContext` -> `historical_context`).
- Domain names stay cohesive and stable across all layers.
- Keep type names PascalCase in Java and class names in Python unchanged unless needed.

Expected major rename set:
- `com.historyTalk` -> `com.historytalk`
- `controller.historicalContext` -> `presentation.historical_context.controller`
- `service.historicalContext` -> `application.historical_context.service`
- `entity.historicalContext` -> `dataaccess.historical_context.entity`
- `app.*` imports -> `history_talk_ai.*`

## 5. Migration Strategy (Recommended: Incremental by Domain)

### Phase 0: Preparation

1. Create refactor branch: `refactor/3layer-structure`.
2. Capture baseline verification:
   - Java compile: `mvn -q -DskipTests compile`
   - Python import/startup check (current entrypoint).
3. Create rollback anchor tag: `pre-3layer-refactor`.

### Phase 1: Documentation Isolation (Low Risk)

1. Move Java markdown docs:
   - `history-talk-backend/docs/*.md` -> `docs/services/history-talk-backend/`
2. Move Python markdown docs:
   - `history-talk-backend-AI/docs/*.md` -> `docs/services/history-talk-backend-ai/`
3. Add index files:
   - `docs/services/history-talk-backend/README.md`
   - `docs/services/history-talk-backend-ai/README.md`

### Phase 2: Java Base Package Normalization

1. Rename source path: `com/historyTalk` -> `com/historytalk`.
2. Rewrite package declarations/imports accordingly.
3. Update `pom.xml` groupId:
   - `com.historyTalk` -> `com.historytalk`
4. Compile gate before deeper restructuring.

### Phase 3: Java Cross-Cutting Extraction

Move technical concerns first to reduce confusion during domain migration:
- `config/*` -> `common/config/*`
- `security/*` + auth utility classes -> `common/security/*`
- `utils/*` -> `common/util/*`
- `exception/*` -> `common/exception/*`
- outbound AI integration client -> `common/integration/ai/*`

Compile after this phase.

### Phase 4: Java Domain-by-Domain Layer Migration

Apply the same sequence per domain:
- Domain order: `authentication` -> `user` -> `character` -> `historical_context` -> `chat` -> `quiz`
- For each domain:
  1. Move presentation artifacts:
     - controllers + DTOs -> `presentation/<domain>/{controller,dto}`
  2. Move application artifacts:
     - services/interfaces/mappers -> `application/<domain>/...`
  3. Move data artifacts:
     - entities/repositories -> `dataaccess/<domain>/...`
  4. Update imports, component wiring, and package names.
  5. Run compile gate.

Shared/common structures:
- shared API wrappers (`ApiResponse`, `PaginatedResponse`, validation DTOs) -> `presentation/common/dto`
- enums -> `dataaccess/shared/entity/enums`

### Phase 5: Python Package and Layer Migration

1. Create new package root: `src/history_talk_ai/`.
2. Presentation moves:
   - `app/routers/chat.py` -> `presentation/chat/router.py`
   - API request/response models from `app/models/chat.py` -> `presentation/chat/schemas.py`
3. Data-access moves:
   - Java backend response models from `app/models/{character,historical_context}.py` -> `dataaccess/java_backend/schemas.py`
   - `app/services/java_client.py` -> `dataaccess/java_backend/client.py`
4. Application moves:
   - `app/services/llm_service.py` -> `application/chat/service.py`
   - `app/services/prompt_builder.py` -> `application/prompting/prompt_builder.py`
5. Cross-cutting moves:
   - `app/config.py` -> `common/config/settings.py`
   - define `common/errors/` for custom exceptions
6. App bootstrap:
   - move/update `app/main.py` to `history_talk_ai/main.py`
   - update root launcher `main.py` imports to new package root

### Phase 6: Transitional Compatibility and Cleanup

1. Optional short-lived forwarding modules for Python import compatibility (one commit window only).
2. Remove compatibility shims after validation.
3. Delete empty legacy folders (`app/` old paths, old Java package paths).

## 6. Verification Gates

Run at each checkpoint to prevent cascading failures:

- Java gate:
  - `mvn -q -DskipTests compile`
- Python gate:
  - startup/import check for new package root
  - health endpoint smoke check
- Integration gate:
  - AI service can still call Java character/context endpoints

No phase advances without passing its gate.

## 7. Commit Plan

Recommended commit slicing:

1. Docs relocation only.
2. Java base-package rename only.
3. Java cross-cutting extraction.
4. Java domain migration commits (one domain per commit).
5. Python structural migration.
6. Remove temporary compatibility layers.
7. Final cleanup and docs updates.

## 8. Risks and Controls

Risks:
- Import/package path breakage during renames.
- Spring component scanning misses moved classes.
- Python runtime import path mismatch after `src` layout transition.
- Hidden references in scripts/Docker docs.

Controls:
- Small commit slices with compile/startup gates.
- Domain-by-domain migration order.
- Temporary compatibility shims only where necessary.
- Explicit rollback anchor and branch isolation.

## 9. Success Criteria

- Both services compile/start cleanly after migration.
- Both services use strict 3-layer package boundaries.
- Naming is fully normalized (`com.historytalk`, `history_talk_ai`, lowercase packages).
- No instruction/plan markdown files remain inside source-code package trees.
- All documentation is centralized under root `/docs/services/...`.
