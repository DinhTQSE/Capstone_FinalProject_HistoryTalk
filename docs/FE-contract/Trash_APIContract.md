# Trash Module API Contract

> **Base URL:** `{BACKEND_BASE_URL}/api/v1`  
> **Auth:** `Authorization: Bearer <accessToken>`  
> **Required role:** `CONTENT_ADMIN` | `SYSTEM_ADMIN`  
> **Response wrapper:** `{ success: boolean, message: string, data: T, timestamp: string }`

## 1. Types

### `ContentStatus`

```typescript
type ContentStatus = "ACTIVE" | "DRAFT" | "INACTIVE"
```

For trash responses, `status` is always `"INACTIVE"`.

### `TrashItem`

```typescript
type TrashItemType = "CHARACTER" | "HISTORICAL_CONTEXT" | "QUIZ"

type TrashItem = {
  id: string
  type: TrashItemType
  title: string
  status: ContentStatus
  deletedAt: string // ISO8601
}
```

### `BulkTrashActionRequest`

```typescript
type BulkTrashActionRequest = {
  ids: string[] // UUID list, duplicates are ignored by backend
}
```

### `BulkTrashActionResponse`

```typescript
type TrashActionStatus =
  | "RESTORED"
  | "HARD_DELETED"
  | "NOT_FOUND"
  | "NOT_TRASHED"

type BulkTrashActionResponse = {
  requested: number
  succeeded: number
  results: {
    id: string
    status: TrashActionStatus
    message: string
  }[]
}
```

FE should treat `succeeded < requested` as partial success and show row-level results.

---

## 2. List Trash

### `GET /system/trash/characters`

List soft-deleted characters.

**Response `200`:**

```json
{
  "success": true,
  "message": "Trashed characters retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "type": "CHARACTER",
      "title": "Ngô Quyền",
      "status": "INACTIVE",
      "deletedAt": "2026-05-25T14:00:00"
    }
  ],
  "timestamp": "2026-05-25T14:00:00"
}
```

### `GET /system/trash/historical-contexts`

List soft-deleted historical contexts.

**Response `200`:**

```json
{
  "success": true,
  "message": "Trashed historical contexts retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "type": "HISTORICAL_CONTEXT",
      "title": "Trận Bạch Đằng",
      "status": "INACTIVE",
      "deletedAt": "2026-05-25T14:00:00"
    }
  ],
  "timestamp": "2026-05-25T14:00:00"
}
```

### `GET /system/trash/quizzes`

List soft-deleted quizzes.

**Response `200`:**

```json
{
  "success": true,
  "message": "Trashed quizzes retrieved successfully",
  "data": [
    {
      "id": "uuid",
      "type": "QUIZ",
      "title": "Quiz lịch sử Việt Nam",
      "status": "INACTIVE",
      "deletedAt": "2026-05-25T14:00:00"
    }
  ],
  "timestamp": "2026-05-25T14:00:00"
}
```

---

## 3. Restore

Restore clears `deletedAt`. It does not change `isPublished`.

### `PATCH /system/trash/characters/restore`

**Request:**

```json
{
  "ids": ["uuid-1", "uuid-2"]
}
```

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

### `PATCH /system/trash/historical-contexts/restore`

**Request:** `BulkTrashActionRequest`

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

### `PATCH /system/trash/quizzes/restore`

**Request:** `BulkTrashActionRequest`

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

Example response:

```json
{
  "success": true,
  "message": "Character restore completed",
  "data": {
    "requested": 2,
    "succeeded": 1,
    "results": [
      {
        "id": "uuid-1",
        "status": "RESTORED",
        "message": "Character restored"
      },
      {
        "id": "uuid-2",
        "status": "NOT_TRASHED",
        "message": "Character is not in trash"
      }
    ]
  },
  "timestamp": "2026-05-25T14:00:00"
}
```

---

## 4. Hard Delete

Hard delete permanently removes records and is only allowed for items already in trash.

### `DELETE /system/trash/characters`

**Request:** `BulkTrashActionRequest`

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

### `DELETE /system/trash/historical-contexts`

**Request:** `BulkTrashActionRequest`

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

### `DELETE /system/trash/quizzes`

**Request:** `BulkTrashActionRequest`

**Response `200`:** `{ success: true, data: BulkTrashActionResponse }`

Example response:

```json
{
  "success": true,
  "message": "Historical context hard delete completed",
  "data": {
    "requested": 2,
    "succeeded": 1,
    "results": [
      {
        "id": "uuid-1",
        "status": "HARD_DELETED",
        "message": "Historical context permanently deleted"
      },
      {
        "id": "uuid-2",
        "status": "NOT_FOUND",
        "message": "Historical context not found"
      }
    ]
  },
  "timestamp": "2026-05-25T14:00:00"
}
```

---

## 5. Error Handling

### `401 Unauthorized`

Missing or invalid token.

### `403 Forbidden`

Authenticated user is not `SYSTEM_ADMIN`.

### `400 Bad Request`

Invalid body, empty `ids`, or invalid UUID format.

Example:

```json
{
  "success": false,
  "message": "ids is required",
  "data": null,
  "timestamp": "2026-05-25T14:00:00"
}
```

## 6. FE Integration Notes

- Trash module should be visible to `CONTENT_ADMIN` and `SYSTEM_ADMIN`.
- Use separate tabs or filters for `characters`, `historical-contexts`, and `quizzes`.
- Restore and hard delete are bulk-safe; FE can call the same endpoint for one or many selected rows.
- `NOT_TRASHED` and `NOT_FOUND` are row-level results, not full request failures.
- After successful restore/hard delete, refresh the corresponding trash list.
