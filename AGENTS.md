# Repository Guidelines

## Codex Workspace Behavior
- Treat this file as the active project instruction source for this repository.
- Markdown files are allowed, but they must be created in the correct repository location instead of being scattered across the workspace.
- When creating Markdown documentation for a service, place it under `docs/services/history-talk-backend/` or `docs/services/history-talk-backend-ai/` as appropriate.
- When creating planning artifacts, place them under `docs/superpowers/plans/`.
- When creating repository-wide notes that are not service-specific, place them under `docs/`.
- Do not create Markdown files inside source-code directories unless the user explicitly asks for that exact location.
- If the correct Markdown destination is unclear, ask the user before creating the file.
- When the user asks for an explanation, review, or status, answer in chat by default unless a Markdown file is explicitly requested.
- Keep edits scoped to the requested task and avoid modifying unrelated files.
- Before editing files, inspect the relevant code and follow existing project structure.
- Run minimal validation for the changed scope when practical, and report any command that could not be run.
- Ask before destructive actions such as bulk deletes, force resets, or history rewrites.

## Project Structure & Module Organization
- Monorepo root: `Source-code/SWD392_FinalProject_HistoryTalk/`.
- Java backend: `history-talk-backend-Java/` (Spring Boot 3.2.x, Java 21, Maven).
- AI backend: `history-talk-backend-AI/` (FastAPI, Uvicorn, Pydantic settings, Supabase client).
- Monitoring: `monitoring/` (Prometheus/Grafana Docker Compose and dashboards).
- `wdp301-backend/` currently has no committed source files; verify before treating it as an active service.
- Shared docs: `docs/services/` and planning artifacts under `docs/superpowers/plans/`.
- Service-specific legacy docs may still exist under each service's `docs/`; new service docs should go under the root `docs/services/...` paths above.
- Java code uses package groups under `src/main/java/com/historytalk/`:
  - `controller/<domain>/` for REST controllers
  - `service/<domain>/` for business logic and integrations
  - `repository/` and `repository/<domain>/` for Spring Data repositories and projections
  - `entity/<domain>/` for JPA entities and enums
  - `dto/<domain>/` for request/response models
  - `mapper/<domain>/`, `config/`, `security/`, `exception/`, and `utils/` for supporting code
- Python code uses `src/history_talk_ai/` with equivalent `presentation/`, `application/`, `dataaccess/`, `common/` layers.

## Build, Test, and Development Commands
- Java commands must be run from `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`.
- Java compile check: `mvn -q -DskipTests compile`
- Java tests: `mvn test`
- Java full build: `mvn clean install`
- Run Java service: `mvn spring-boot:run`
- Optional Java helper scripts: `scripts/start-local.ps1` and `scripts/install-maven.ps1`.
- AI commands must be run from `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI/`.
- Python setup (AI service): `python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt`
- Run AI service:
  - `python main.py`
  - or `uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src`
- Monitoring stack: from `Source-code/SWD392_FinalProject_HistoryTalk/monitoring/`, run `docker compose -f docker-compose.monitoring.yml up`.

## Coding Style & Naming Conventions
- Use 4-space indentation in Java and Python.
- Java: `PascalCase` classes, `camelCase` members, lowercase packages (`com.historytalk...`).
- Python: modules/functions `snake_case`, classes `PascalCase`.
- Keep domain code in its layer; avoid placing business logic in controllers.
- No dedicated formatter/linter config is committed; follow existing file style and keep diffs focused.

## Testing Guidelines
- Java test stack is available via `spring-boot-starter-test` and `spring-security-test`.
- Java tests are committed under `history-talk-backend-Java/src/test/java/...`; add focused tests with each new Java feature/fix.
- No Python `tests/` suite is currently committed for the AI service; add pytest-style tests for new Python behavior when practical.
- Naming:
  - Java: `src/test/java/.../*Test.java`
  - Python: `tests/test_*.py` (pytest style)
- Minimum validation before PR: run the changed service's test/build command, run affected services when practical, and verify changed endpoints in Swagger (`http://localhost:8080/Historical-tell/api/v1/swagger-ui`, `http://localhost:8001/docs`).

## Commit & Pull Request Guidelines
- Prefer Conventional Commit style used in history: `type(scope): description` (e.g., `refactor(java): ...`, `docs: ...`, `fix(java): ...`).
- Create topic branches like `feature/<name>` or `fix/<name>`.
- PRs should include: concise summary, impacted modules, test evidence, and linked issue/task.
- Coordinate before editing shared-risk files such as `SecurityConfig`, `JwtAuthenticationFilter`, `application.properties`, and `pom.xml`.
