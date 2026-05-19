# Repository Guidelines

## Project Structure & Module Organization
- Monorepo root: `Source-code/SWD392_FinalProject_HistoryTalk/`.
- Java backend: `history-talk-backend/` (Spring Boot, Java 21).
- AI backend: `history-talk-backend-AI/` (FastAPI, LangChain).
- Shared docs: `docs/services/` and planning artifacts under `docs/superpowers/plans/`.
- Java code follows 3-layer layout in `src/main/java/com/historytalk/`:
  - `presentation/<domain>/` (controllers + DTOs)
  - `application/<domain>/` (services + mappers)
  - `dataaccess/<domain>/` (entities + repositories)
  - `common/` (cross-cutting config, security, exceptions, utils)
- Python code uses `src/history_talk_ai/` with equivalent `presentation/`, `application/`, `dataaccess/`, `common/` layers.

## Build, Test, and Development Commands
- Java compile check: `mvn -q -DskipTests compile`
- Java full build: `mvn clean install`
- Run Java service: `mvn spring-boot:run`
- Python setup (AI service): `python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt`
- Run AI service:
  - `python main.py`
  - or `uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src`
- Optional helper scripts: `history-talk-backend/scripts/start-local.ps1` and `install-maven.ps1`.

## Coding Style & Naming Conventions
- Use 4-space indentation in Java and Python.
- Java: `PascalCase` classes, `camelCase` members, lowercase packages (`com.historytalk...`).
- Python: modules/functions `snake_case`, classes `PascalCase`.
- Keep domain code in its layer; avoid placing business logic in controllers.
- No dedicated formatter/linter config is committed; follow existing file style and keep diffs focused.

## Testing Guidelines
- Java test stack is available via `spring-boot-starter-test` and `spring-security-test`.
- Current repo has no committed `src/test` or `tests/` suites; add tests with each new feature/fix.
- Naming:
  - Java: `src/test/java/.../*Test.java`
  - Python: `tests/test_*.py` (pytest style)
- Minimum validation before PR: `mvn clean install`, run both services, verify changed endpoints in Swagger (`:8080/Historical-tell/api/v1/swagger-ui`, `:8001/docs`).

## Commit & Pull Request Guidelines
- Prefer Conventional Commit style used in history: `type(scope): description` (e.g., `refactor(java): ...`, `docs: ...`, `fix(java): ...`).
- Create topic branches like `feature/<name>` or `fix/<name>`.
- PRs should include: concise summary, impacted modules, test evidence, and linked issue/task.
- Coordinate before editing shared-risk files such as `SecurityConfig`, `JwtAuthenticationFilter`, `application.properties`, and `pom.xml`.
