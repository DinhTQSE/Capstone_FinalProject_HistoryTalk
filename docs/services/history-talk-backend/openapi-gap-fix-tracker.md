# OpenAPI Gap Fix Tracker

Source contract: `docs/openapi.json`  
Backend checked: `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java` 

## Fixed in this pass

| Gap | Contract endpoint/config | Backend change | Status |
| --- | --- | --- | --- |
| Servlet path mismatch | Server base is `/api/v1`; backend added `/Historical-tell` before it | Set `spring.mvc.servlet.path=/` in `application.properties` and `application-prod.properties` | Fixed |
| Content admin registration missing | `POST /auth/register-content-admin` | Added `POST /api/v1/auth/register-content-admin`, protected by `SYSTEM_ADMIN`, using existing `AuthService.registerStaff` | Fixed |
| Character toggle-active missing | `PATCH /characters/{id}/toggle-active` | Added `PATCH /api/v1/characters/{characterId}/toggle-active` and `CharacterService.toggleActiveCharacter` | Fixed |
| Historical context toggle-active missing | `PATCH /historical-contexts/{id}/toggle-active` | Added `PATCH /api/v1/historical-contexts/{contextId}/toggle-active` and `HistoricalContextService.toggleActiveContext` | Fixed |
| Staff quiz toggle-active missing | `PATCH /staff/quizzes/{quizId}/toggle-active` | Added `PATCH /api/v1/staff/quizzes/{quizId}/toggle-active` and `QuizService.toggleActiveQuiz` | Fixed |

## Compatibility notes

- Existing `soft-delete` endpoints were kept. The new toggle endpoints are contract-facing aliases with reversible active/inactive behavior.
- Quiz currently has no `is_active` database column. Its active state is derived from `deleted_at == null`, and `QuizStaffResponse.isActive` now exposes that state.
- Character and historical context toggles flip `is_active` and clear/set `deleted_at` accordingly.

## Known remaining OpenAPI drift to review separately

| Backend-only endpoint | Note |
| --- | --- |
| `/api/v1/historical-documents/**` | Present in backend, not in current `docs/openapi.json` |
| `/api/v1/characters/{characterId}/contexts` | Present in backend, not in current `docs/openapi.json` |
| `DELETE /api/v1/characters/{characterId}/contexts/{contextId}` | Present in backend, not in current `docs/openapi.json` |
| `PUT /api/v1/staff/quizzes/{quizId}/questions/reorder` | Present in backend, not in current `docs/openapi.json` |
