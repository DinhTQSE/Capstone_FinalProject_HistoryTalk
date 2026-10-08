# OpenAPI Gap Fix Tracker

Last verified: 2026-05-28

This tracker is retained as context for earlier FE/BE contract work. The current API source of truth is:

```text
docs/API_CONTRACT.md
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller
```

## Current Contract Status

The previous `toggle-active` contract is obsolete. Current content lifecycle uses:

```text
deletedAt != null -> INACTIVE
deletedAt == null && isPublished == false -> DRAFT
deletedAt == null && isPublished == true -> ACTIVE
```

Current soft-delete endpoints:

```text
PATCH /api/v1/characters/{characterId}/soft-delete
PATCH /api/v1/historical-contexts/{contextId}/soft-delete
PATCH /api/v1/staff/quizzes/{quizId}/soft-delete
```

Deleted content is managed through:

```text
/api/v1/system/trash
```

## Current Known Drift

`docs/openapi.json` is older than the Java controllers and still contains `toggle-active` paths. Do not use it as the current contract until it is regenerated from the running backend or replaced with an updated OpenAPI export.

Known backend areas that should be included in a regenerated OpenAPI document:

| Area | Current backend path |
| --- | --- |
| Auth and Google OAuth helper | `/api/v1/auth/**` |
| Characters | `/api/v1/characters/**` |
| Character documents | `/api/v1/character-documents/**` |
| Historical contexts | `/api/v1/historical-contexts/**` |
| Historical documents | `/api/v1/historical-documents/**` |
| Chat | `/api/v1/chat/**` |
| Customer quiz | `/api/v1/quizzes/**` |
| Staff quiz | `/api/v1/staff/quizzes/**` |
| Trash | `/api/v1/system/trash/**` |
| System dashboard | `/api/v1/system-admin/dashboard/**` |
| Payments | `/api/v1/payments/**` |
