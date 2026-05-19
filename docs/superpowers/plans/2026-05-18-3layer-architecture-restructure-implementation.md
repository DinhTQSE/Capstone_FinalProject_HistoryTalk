# 3-Layer Architecture Refactor Implementation Plan

> **Superseded on 2026-05-19:** Do not use this plan for the Java backend. The Java package structure has been restored to the Spring Boot convention documented in `docs/superpowers/specs/2026-05-18-3layer-architecture-restructure-design.md`: `controller`, `service`, `repository`, plus top-level supporting packages. This file is retained only as historical record of the rejected refactor.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure both backend services into strict 3-layer architecture, normalize naming (`com.historytalk`, `history_talk_ai`), and centralize markdown documentation under root `docs/services` without behavior changes.

**Architecture:** Execute in three tracks: shared docs relocation, Java service refactor, and Python service refactor. Use incremental commits with compile/startup gates after each task. Keep cross-cutting concerns in `common` and move domain code into `presentation`, `application`, and `dataaccess`.

**Tech Stack:** Java 21, Spring Boot 3.2.x, Maven, Python 3.x, FastAPI, Pydantic, Uvicorn, PowerShell, Git.

---

## Scope Decomposition

This spec spans two independent subsystems. Execute as three serial workstreams in this single plan:
1. Shared docs relocation (safe, low-risk)
2. Java service package/layer migration
3. Python service package/layer migration

## File Structure Lock-In

### Shared docs target
- `docs/services/history-talk-backend/`
- `docs/services/history-talk-backend-ai/`

### Java service root
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend`

### Python service root
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI`

### New package targets
- Java base: `src/main/java/com/historytalk/`
- Python base: `src/history_talk_ai/`

---

### Task 1: Baseline Snapshot and Safety Guard

**Files:**
- Create: `docs/superpowers/plans/artifacts/2026-05-18-3layer-baseline.md`

- [ ] **Step 1: Capture current git state before refactor**

Run:
```powershell
git status --short
```
Expected: existing unrelated changes are visible; do not revert them.

- [ ] **Step 2: Run Java baseline compile**

Run:
```powershell
mvn -q -DskipTests compile
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend
```
Expected: Maven compile succeeds.

- [ ] **Step 3: Run Python baseline import/startup smoke check**

Run:
```powershell
python -c "from app.main import app; print('baseline-python-ok')"
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```
Expected: `baseline-python-ok` printed.

- [ ] **Step 4: Write baseline artifact file**

```markdown
# Baseline Snapshot - 2026-05-18

- Java compile: PASS
- Python import smoke check: PASS
- Git status captured before refactor
- Note: unrelated local changes preserved
```

- [ ] **Step 5: Commit baseline artifact**

Run:
```bash
git add docs/superpowers/plans/artifacts/2026-05-18-3layer-baseline.md
git commit -m "chore: add baseline snapshot for 3-layer refactor"
```

### Task 2: Relocate Service Markdown Docs to Root Docs

**Files:**
- Create: `docs/services/history-talk-backend/README.md`
- Create: `docs/services/history-talk-backend-ai/README.md`
- Modify (move):
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/authentication-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/chat-messages-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/design-pattern-review.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/document-processor-strategy-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/enum-fields-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/implementation-summary.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/plan-character-context-decoupling.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/plan-draft-publish-trash-character-context.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/plan-fix-quiz-context.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/quiz-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/soft-delete-fk-reference-fix-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/soft-delete-implementation-plan.md`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/docs/PLAN.md`

- [ ] **Step 1: Verify docs are currently inside service folders (expected fail against target rule)**

Run:
```powershell
if (Get-ChildItem Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs -File -ErrorAction SilentlyContinue) { Write-Output "java-docs-still-inside-service" }
if (Get-ChildItem Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/docs -File -ErrorAction SilentlyContinue) { Write-Output "python-docs-still-inside-service" }
```
Expected: both markers print.

- [ ] **Step 2: Move Java docs into root docs area**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs/*.md docs/services/history-talk-backend/
```

- [ ] **Step 3: Move Python docs into root docs area**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/docs/*.md docs/services/history-talk-backend-ai/
```

- [ ] **Step 4: Create docs index files**

`docs/services/history-talk-backend/README.md`
```markdown
# History Talk Backend Docs

This folder contains architecture and implementation documents moved out of Java source packages.
```

`docs/services/history-talk-backend-ai/README.md`
```markdown
# History Talk Backend AI Docs

This folder contains architecture and implementation documents moved out of Python source packages.
```

- [ ] **Step 5: Verify relocation rule passes**

Run:
```powershell
$javaDocs = Get-ChildItem Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/docs -File -ErrorAction SilentlyContinue
$pyDocs = Get-ChildItem Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/docs -File -ErrorAction SilentlyContinue
if ($javaDocs -or $pyDocs) { throw "Service-local docs still exist" } else { Write-Output "docs-relocation-pass" }
```
Expected: `docs-relocation-pass`.

- [ ] **Step 6: Commit docs relocation**

```bash
git add docs/services
git commit -m "refactor(docs): centralize service markdown files under root docs"
```

### Task 3: Java Base Package Normalization (`com.historyTalk` -> `com.historytalk`)

**Files:**
- Modify: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/pom.xml`
- Modify (move + package updates): `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk/**`

- [ ] **Step 1: Verify uppercase package exists (expected fail against target rule)**

Run:
```powershell
rg -n "com\.historyTalk" Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java
```
Expected: matches found.

- [ ] **Step 2: Rename base Java directory**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historyTalk Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk
```

- [ ] **Step 3: Rewrite Java package declarations and imports**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
Get-ChildItem $root -Recurse -Filter *.java | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("com.historyTalk", "com.historytalk")
  Set-Content $_.FullName $content
}
```

- [ ] **Step 4: Update Maven groupId**

Run:
```powershell
$pom = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/pom.xml"
$content = Get-Content $pom -Raw
$content = $content.Replace("<groupId>com.historyTalk</groupId>", "<groupId>com.historytalk</groupId>")
Set-Content $pom $content
```

- [ ] **Step 5: Compile Java service**

Run:
```powershell
mvn -q -DskipTests compile
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend
```
Expected: compile succeeds.

- [ ] **Step 6: Commit base package normalization**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/pom.xml
git commit -m "refactor(java): normalize base package to com.historytalk"
```
### Task 4: Java Cross-Cutting Extraction to `common`

**Files:**
- Modify (move):
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/config/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/security/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/exception/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/SecurityUtils.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/UuidUtils.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/authentication/JwtProperties.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/authentication/JwtUtils.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/chat/AiServiceClient.java`

- [ ] **Step 1: Create target directories**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common"
New-Item -ItemType Directory -Force "$root/config","$root/security","$root/exception","$root/util","$root/integration/ai" | Out-Null
```

- [ ] **Step 2: Move cross-cutting files**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/config Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/config
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/security Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/security
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/exception Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/exception
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/SecurityUtils.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/util/SecurityUtils.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/UuidUtils.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/util/UuidUtils.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/authentication/JwtProperties.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/security/JwtProperties.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/utils/authentication/JwtUtils.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/security/JwtUtils.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/chat/AiServiceClient.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/common/integration/ai/AiServiceClient.java
```

- [ ] **Step 3: Rewrite package declarations for moved Java files**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
Get-ChildItem $root -Recurse -Filter *.java | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("com.historytalk.config", "com.historytalk.common.config")
  $content = $content.Replace("com.historytalk.security", "com.historytalk.common.security")
  $content = $content.Replace("com.historytalk.exception", "com.historytalk.common.exception")
  $content = $content.Replace("com.historytalk.utils.authentication", "com.historytalk.common.security")
  $content = $content.Replace("com.historytalk.utils", "com.historytalk.common.util")
  $content = $content.Replace("com.historytalk.service.chat.AiServiceClient", "com.historytalk.common.integration.ai.AiServiceClient")
  Set-Content $_.FullName $content
}
```

- [ ] **Step 4: Compile Java service**

Run:
```powershell
mvn -q -DskipTests compile
```
Expected: compile succeeds.

- [ ] **Step 5: Commit cross-cutting extraction**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk
git commit -m "refactor(java): move cross-cutting components into common package"
```

### Task 5: Java Domain Migration (Authentication, User, Character)

**Files:**
- Modify (move):
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/authentication/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/authentication/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/authentication/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/user/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/mapper/user/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/user/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/UserRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/character/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/character/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/character/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/mapper/character/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/character/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/CharacterRepository.java`

- [ ] **Step 1: Create layer directories for the three domains**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
New-Item -ItemType Directory -Force `
  "$root/presentation/authentication/controller", "$root/presentation/authentication/dto", "$root/application/authentication/service", `
  "$root/presentation/user/dto", "$root/application/user/mapper", "$root/dataaccess/user/entity", "$root/dataaccess/user/repository", `
  "$root/presentation/character/controller", "$root/presentation/character/dto", "$root/application/character/service", "$root/application/character/mapper", "$root/dataaccess/character/entity", "$root/dataaccess/character/repository" | Out-Null
```

- [ ] **Step 2: Move authentication files**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/authentication Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/authentication/controller
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/authentication Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/authentication/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/authentication Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/authentication/service
```

- [ ] **Step 3: Move user and character files**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/user Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/user/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/mapper/user Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/user/mapper
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/user Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/user/entity
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/UserRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/user/repository/UserRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/character Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/character/controller
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/character Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/character/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/character Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/character/service
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/mapper/character Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/character/mapper
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/character Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/character/entity
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/CharacterRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/character/repository/CharacterRepository.java
```

- [ ] **Step 4: Rewrite Java package references for these domains**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
Get-ChildItem $root -Recurse -Filter *.java | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("com.historytalk.controller.authentication", "com.historytalk.presentation.authentication.controller")
  $content = $content.Replace("com.historytalk.dto.authentication", "com.historytalk.presentation.authentication.dto")
  $content = $content.Replace("com.historytalk.service.authentication", "com.historytalk.application.authentication.service")
  $content = $content.Replace("com.historytalk.dto.user", "com.historytalk.presentation.user.dto")
  $content = $content.Replace("com.historytalk.mapper.user", "com.historytalk.application.user.mapper")
  $content = $content.Replace("com.historytalk.entity.user", "com.historytalk.dataaccess.user.entity")
  $content = $content.Replace("com.historytalk.repository.UserRepository", "com.historytalk.dataaccess.user.repository.UserRepository")
  $content = $content.Replace("com.historytalk.controller.character", "com.historytalk.presentation.character.controller")
  $content = $content.Replace("com.historytalk.dto.character", "com.historytalk.presentation.character.dto")
  $content = $content.Replace("com.historytalk.service.character", "com.historytalk.application.character.service")
  $content = $content.Replace("com.historytalk.mapper.character", "com.historytalk.application.character.mapper")
  $content = $content.Replace("com.historytalk.entity.character", "com.historytalk.dataaccess.character.entity")
  $content = $content.Replace("com.historytalk.repository.CharacterRepository", "com.historytalk.dataaccess.character.repository.CharacterRepository")
  Set-Content $_.FullName $content
}
```

- [ ] **Step 5: Compile Java service**

Run:
```powershell
mvn -q -DskipTests compile
```
Expected: compile succeeds.

- [ ] **Step 6: Commit domain migration set 1**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk
git commit -m "refactor(java): migrate authentication user and character to 3-layer packages"
```

### Task 6: Java Domain Migration (Historical Context, Chat, Quiz, Shared DTO/Enums)

**Files:**
- Modify (move):
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/historicalContext/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/historicalContext/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/historicalContext/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/historicalContext/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/HistoricalContextRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/HistoricalContextDocumentRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/chat/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/chat/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/chat/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/chat/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/ChatSessionRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/MessageRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/quiz/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/quiz/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/quiz/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/quiz/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuestionRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizResultRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizSessionRepository.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/enums/**`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/ApiResponse.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/PaginatedResponse.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/ValidationErrorResponse.java`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/exception/InvalidArgumentResponse.java`

- [ ] **Step 1: Create target layer directories**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
New-Item -ItemType Directory -Force `
  "$root/presentation/historical_context/controller", "$root/presentation/historical_context/dto", "$root/application/historical_context/service", "$root/application/historical_context/strategy", "$root/dataaccess/historical_context/entity", "$root/dataaccess/historical_context/repository", `
  "$root/presentation/chat/controller", "$root/presentation/chat/dto", "$root/application/chat/service", "$root/dataaccess/chat/entity", "$root/dataaccess/chat/repository", `
  "$root/presentation/quiz/controller", "$root/presentation/quiz/dto", "$root/application/quiz/service", "$root/dataaccess/quiz/entity", "$root/dataaccess/quiz/repository", `
  "$root/presentation/common/dto", "$root/dataaccess/shared/entity/enums" | Out-Null
```

- [ ] **Step 2: Move historical_context and chat files**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/historicalContext Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/historical_context/controller
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/historicalContext Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/historical_context/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/historicalContext/strategy Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/historical_context/strategy
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/historicalContext/*.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/historical_context/service/
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/historicalContext Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/historical_context/entity
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/HistoricalContextRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/historical_context/repository/HistoricalContextRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/HistoricalContextDocumentRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/historical_context/repository/HistoricalContextDocumentRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/chat Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/chat/controller
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/chat Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/chat/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/chat/*.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/chat/service/
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/chat Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/chat/entity
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/ChatSessionRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/chat/repository/ChatSessionRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/MessageRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/chat/repository/MessageRepository.java
```

- [ ] **Step 3: Move quiz, shared dto, and enums**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/controller/quiz Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/quiz/controller
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/quiz Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/quiz/dto
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/service/quiz Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/application/quiz/service
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/quiz Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/quiz/entity
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuestionRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/quiz/repository/QuestionRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/quiz/repository/QuizRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizResultRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/quiz/repository/QuizResultRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/repository/QuizSessionRepository.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/quiz/repository/QuizSessionRepository.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/entity/enums Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dataaccess/shared/entity/enums
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/ApiResponse.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/common/dto/ApiResponse.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/PaginatedResponse.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/common/dto/PaginatedResponse.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/ValidationErrorResponse.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/common/dto/ValidationErrorResponse.java
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/dto/exception/InvalidArgumentResponse.java Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk/presentation/common/dto/InvalidArgumentResponse.java
```

- [ ] **Step 4: Rewrite package references for migrated domains**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk"
Get-ChildItem $root -Recurse -Filter *.java | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("com.historytalk.controller.historicalContext", "com.historytalk.presentation.historical_context.controller")
  $content = $content.Replace("com.historytalk.dto.historicalContext", "com.historytalk.presentation.historical_context.dto")
  $content = $content.Replace("com.historytalk.service.historicalContext.strategy", "com.historytalk.application.historical_context.strategy")
  $content = $content.Replace("com.historytalk.service.historicalContext", "com.historytalk.application.historical_context.service")
  $content = $content.Replace("com.historytalk.entity.historicalContext", "com.historytalk.dataaccess.historical_context.entity")
  $content = $content.Replace("com.historytalk.repository.HistoricalContextRepository", "com.historytalk.dataaccess.historical_context.repository.HistoricalContextRepository")
  $content = $content.Replace("com.historytalk.repository.HistoricalContextDocumentRepository", "com.historytalk.dataaccess.historical_context.repository.HistoricalContextDocumentRepository")
  $content = $content.Replace("com.historytalk.controller.chat", "com.historytalk.presentation.chat.controller")
  $content = $content.Replace("com.historytalk.dto.chat", "com.historytalk.presentation.chat.dto")
  $content = $content.Replace("com.historytalk.service.chat", "com.historytalk.application.chat.service")
  $content = $content.Replace("com.historytalk.entity.chat", "com.historytalk.dataaccess.chat.entity")
  $content = $content.Replace("com.historytalk.repository.ChatSessionRepository", "com.historytalk.dataaccess.chat.repository.ChatSessionRepository")
  $content = $content.Replace("com.historytalk.repository.MessageRepository", "com.historytalk.dataaccess.chat.repository.MessageRepository")
  $content = $content.Replace("com.historytalk.controller.quiz", "com.historytalk.presentation.quiz.controller")
  $content = $content.Replace("com.historytalk.dto.quiz", "com.historytalk.presentation.quiz.dto")
  $content = $content.Replace("com.historytalk.service.quiz", "com.historytalk.application.quiz.service")
  $content = $content.Replace("com.historytalk.entity.quiz", "com.historytalk.dataaccess.quiz.entity")
  $content = $content.Replace("com.historytalk.repository.QuestionRepository", "com.historytalk.dataaccess.quiz.repository.QuestionRepository")
  $content = $content.Replace("com.historytalk.repository.QuizRepository", "com.historytalk.dataaccess.quiz.repository.QuizRepository")
  $content = $content.Replace("com.historytalk.repository.QuizResultRepository", "com.historytalk.dataaccess.quiz.repository.QuizResultRepository")
  $content = $content.Replace("com.historytalk.repository.QuizSessionRepository", "com.historytalk.dataaccess.quiz.repository.QuizSessionRepository")
  $content = $content.Replace("com.historytalk.entity.enums", "com.historytalk.dataaccess.shared.entity.enums")
  $content = $content.Replace("com.historytalk.dto.ApiResponse", "com.historytalk.presentation.common.dto.ApiResponse")
  $content = $content.Replace("com.historytalk.dto.PaginatedResponse", "com.historytalk.presentation.common.dto.PaginatedResponse")
  $content = $content.Replace("com.historytalk.dto.ValidationErrorResponse", "com.historytalk.presentation.common.dto.ValidationErrorResponse")
  $content = $content.Replace("com.historytalk.dto.exception.InvalidArgumentResponse", "com.historytalk.presentation.common.dto.InvalidArgumentResponse")
  Set-Content $_.FullName $content
}
```

- [ ] **Step 5: Compile Java service**

Run:
```powershell
mvn -q -DskipTests compile
```
Expected: compile succeeds.

- [ ] **Step 6: Commit domain migration set 2**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java/com/historytalk
git commit -m "refactor(java): migrate historical context chat quiz and shared models"
```

### Task 7: Python `src/history_talk_ai` Skeleton and File Moves

**Files:**
- Modify (move):
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/main.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/config.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/routers/chat.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/chat.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/character.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/historical_context.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/java_client.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/llm_service.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/prompt_builder.py`
- Create:
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/**/__init__.py`

- [ ] **Step 1: Create new directory tree**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai"
New-Item -ItemType Directory -Force `
  "$root/presentation/chat", "$root/application/chat", "$root/application/prompting", "$root/dataaccess/java_backend", "$root/common/config", "$root/common/errors" | Out-Null
```

- [ ] **Step 2: Move files into layer packages**

Run:
```bash
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/main.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/main.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/config.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/common/config/settings.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/routers/chat.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/chat/router.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/chat.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/chat/schemas.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/character.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/java_backend/character_schema.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/models/historical_context.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/java_backend/historical_context_schema.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/java_client.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/java_backend/client.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/llm_service.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/chat/service.py
git mv Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/app/services/prompt_builder.py Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/prompting/prompt_builder.py
```

- [ ] **Step 3: Create package init files**

Run:
```powershell
$files = @(
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/chat/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/chat/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/prompting/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/java_backend/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/common/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/common/config/__init__.py",
"Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/common/errors/__init__.py"
)
foreach ($f in $files) { if (-not (Test-Path $f)) { Set-Content $f "" } }
```

- [ ] **Step 4: Commit Python file moves**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src
git commit -m "refactor(python): move modules into 3-layer src package layout"
```

### Task 8: Python Import Rewire and Entrypoint Update

**Files:**
- Modify:
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/main.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/chat/router.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/presentation/chat/schemas.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/dataaccess/java_backend/client.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/chat/service.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/application/prompting/prompt_builder.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/main.py`
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/README.md`

- [ ] **Step 1: Apply import replacement map**

Run:
```powershell
$root = "Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai"
Get-ChildItem $root -Recurse -Filter *.py | ForEach-Object {
  $content = Get-Content $_.FullName -Raw
  $content = $content.Replace("from app.config import settings", "from history_talk_ai.common.config.settings import settings")
  $content = $content.Replace("from app.models.chat", "from history_talk_ai.presentation.chat.schemas")
  $content = $content.Replace("from app.models.character", "from history_talk_ai.dataaccess.java_backend.character_schema")
  $content = $content.Replace("from app.models.historical_context", "from history_talk_ai.dataaccess.java_backend.historical_context_schema")
  $content = $content.Replace("from app.services import java_client, llm_service", "from history_talk_ai.dataaccess.java_backend import client as java_client`nfrom history_talk_ai.application.chat import service as llm_service")
  $content = $content.Replace("from app.services.java_client", "from history_talk_ai.dataaccess.java_backend.client")
  $content = $content.Replace("from app.services.prompt_builder", "from history_talk_ai.application.prompting.prompt_builder")
  Set-Content $_.FullName $content
}
```

- [ ] **Step 2: Update package app entrypoint**

`Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/src/history_talk_ai/main.py`
```python
"""FastAPI application entry point."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from history_talk_ai.common.config.settings import settings
from history_talk_ai.presentation.chat import router as chat_router

app = FastAPI(
    title="HistoryTalk AI Service",
    description=(
        "LangChain-powered roleplay API. "
        "Receives a user message + conversation history, fetches character and "
        "historical-context data from the Java backend, then invokes the LLM to produce "
        "an in-character response."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router.router)


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok", "llm_provider": settings.LLM_PROVIDER, "llm_model": settings.LLM_MODEL}
```

- [ ] **Step 3: Update root launcher for `src` layout**

`Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/main.py`
```python
"""Convenience entry-point: python main.py"""

from pathlib import Path
import sys

import uvicorn

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))

from history_talk_ai.common.config.settings import settings  # noqa: E402

if __name__ == "__main__":
    uvicorn.run(
        "history_talk_ai.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=settings.DEBUG,
    )
```

- [ ] **Step 4: Update README runtime command**

Change this line in `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/README.md`:
```text
uvicorn app.main:app --reload --port 8001
```
To:
```text
uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src
```

- [ ] **Step 5: Run Python smoke checks**

Run:
```powershell
python -c "import sys; sys.path.insert(0, 'src'); from history_talk_ai.main import app; print('python-refactor-import-ok')"
python -c "import sys; sys.path.insert(0, 'src'); from history_talk_ai.presentation.chat.router import router; print('python-router-import-ok')"
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```
Expected: both markers printed.

- [ ] **Step 6: Commit Python import rewiring**

```bash
git add Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
git commit -m "refactor(python): rewire imports and entrypoints for history_talk_ai package"
```
### Task 9: Final Repository-Wide Verification and Cleanup

**Files:**
- Modify: none required unless fixes are needed from verification

- [ ] **Step 1: Verify no old Java package references remain**

Run:
```powershell
rg -n "com\.historyTalk" Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/java
```
Expected: no matches.

- [ ] **Step 2: Verify no old Python `app.` imports remain**

Run:
```powershell
rg -n "from app\.|import app\." Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```
Expected: no matches.

- [ ] **Step 3: Verify no service-local markdown docs remain**

Run:
```powershell
rg --files Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI | rg "docs/.*\.md$"
```
Expected: no matches.

- [ ] **Step 4: Compile Java service**

Run:
```powershell
mvn -q -DskipTests compile
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend
```
Expected: compile succeeds.

- [ ] **Step 5: Python startup smoke check**

Run:
```powershell
python -c "import sys; sys.path.insert(0, 'src'); from history_talk_ai.main import app; print('final-python-ok')"
```
Working directory:
```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
```
Expected: `final-python-ok` printed.

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "refactor: enforce 3-layer architecture for java and python services"
```

### Task 10: Post-Refactor Audit Note

**Files:**
- Create: `docs/superpowers/plans/artifacts/2026-05-18-3layer-post-check.md`

- [ ] **Step 1: Write post-refactor audit summary**

```markdown
# Post-Refactor Audit - 2026-05-18

- Java package base normalized to com.historytalk
- Python package base normalized to history_talk_ai
- 3-layer package separation completed for both services
- Cross-cutting concerns moved to common packages
- Service-level docs moved to root docs/services
- Final verification gates passed
```

- [ ] **Step 2: Commit audit note**

```bash
git add docs/superpowers/plans/artifacts/2026-05-18-3layer-post-check.md
git commit -m "docs: add post-refactor architecture audit note"
```

---

## Plan Self-Review

### Spec coverage
- 3-layer restructuring for Java: covered by Tasks 3-6.
- 3-layer restructuring for Python: covered by Tasks 7-8.
- Naming normalization: covered by Tasks 3 and 8.
- Documentation isolation under root docs: covered by Task 2.
- Safe incremental migration with verification gates: covered by Tasks 1, 3, 4, 5, 6, 8, 9.

### Placeholder scan
- No unresolved placeholder markers remain in the plan.
- Each task includes executable commands and expected verification output.

### Type/signature consistency
- Java package root consistently uses `com.historytalk` after Task 3.
- Python package root consistently uses `history_talk_ai` with `src` import path handling in launcher.


