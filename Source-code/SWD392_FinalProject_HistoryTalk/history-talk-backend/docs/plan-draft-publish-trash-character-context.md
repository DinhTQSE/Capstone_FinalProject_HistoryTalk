# Plan: Draft/Publish + Trash/Restore for Character and HistoricalContext

Muc tieu: Bo sung luong tao ban nhap (draft), publish noi dung, va quan ly thung rac cho Character/HistoricalContext bang `deleted_at`.

## 1) Business Rules

### 1.1 Draft/Publish
- `isDraft = true`: noi dung dang o trang thai ban nhap.
- `isDraft = false`: noi dung da publish cho nguoi dung.
- `STAFF` va `ADMIN`: duoc xem va chat voi Character draft.
- `CUSTOMER`: khong duoc chat voi Character draft, khong duoc thay noi dung draft.

### 1.2 Trash/Archive bang `deleted_at`
- `deleted_at IS NULL`: ban ghi dang hoat dong.
- `deleted_at IS NOT NULL`: ban ghi trong thung rac (soft-deleted).
- Admin/Staff UI can co:
  - Danh sach thung rac.
  - Nut Khoi phuc (Restore).
  - Nut Xoa vinh vien (Permanent Delete).

### 1.3 Status mapping de tra ve FE
- De xuat mapping:
  - `deleted_at IS NOT NULL` => `INACTIVE`.
  - `deleted_at IS NULL AND is_draft = true` => `DRAFT`.
  - `deleted_at IS NULL AND is_draft = false` => `ACTIVE`.

Ghi chu: Neu bat buoc map `deleted_at IS NULL => INACTIVE` thi can xac nhan lai nghiep vu vi se dao nguoc logic soft-delete hien tai.

## 2) DB Migration Plan

Tao migration moi (vi du `V8__draft_publish_and_trash.sql`) voi noi dung:

1. Add draft flag:
- `historical_schema."character"`: add `is_draft boolean not null default true`.
- `historical_schema.historical_context`: add `is_draft boolean not null default true`.

2. Backfill du lieu cu:
- Chon 1 trong 2 chien luoc:
  - A. Tat ca du lieu cu mac dinh la published: set `is_draft = false` cho cac record hien co.
  - B. Tat ca du lieu cu giu trang thai draft: giu default true.

3. Index khuyen nghi:
- `idx_character_publish_filter` tren `(deleted_at, is_draft, name)`.
- `idx_context_publish_filter` tren `(deleted_at, is_draft, created_date)`.

## 3) Entity Changes

### 3.1 Character
- Them field:
  - `@Column(name = "is_draft", nullable = false)`
  - `private Boolean isDraft = true;`
- Giu nguyen `@SQLDelete` + `@Where(clause = "deleted_at IS NULL")`.

### 3.2 HistoricalContext
- Them field `isDraft` tuong tu Character.
- Giu nguyen soft-delete annotations.

## 4) DTO/API Changes

### 4.1 Character DTO
- `CreateCharacterRequest`: them `isDraft` (optional, default true).
- `UpdateCharacterRequest`: them `isDraft` (optional) de publish/unpublish.
- `CharacterResponse`: them:
  - `isDraft`
  - `status` (`ACTIVE` | `DRAFT` | `INACTIVE`)

### 4.2 HistoricalContext DTO
- Tuong tu Character: add `isDraft` + `status` vao response.

### 4.3 Endpoint bo sung cho staff/admin
- Character:
  - `GET /api/v1/characters/trash`
  - `PATCH /api/v1/characters/{id}/restore`
  - `DELETE /api/v1/characters/{id}/permanent`
- HistoricalContext:
  - `GET /api/v1/historical-contexts/trash`
  - `PATCH /api/v1/historical-contexts/{id}/restore`
  - `DELETE /api/v1/historical-contexts/{id}/permanent`

## 5) Service/Repository Rules

### 5.1 Visibility rules
- Customer-facing list/detail:
  - filter `deleted_at IS NULL` va `isDraft = false`.
- Staff/Admin list/detail:
  - co the xem ca draft (nhung khong bao gom trash neu khong goi endpoint trash).

### 5.2 Chat guard
Trong `ChatSessionService.createSession(...)`:
- Lay role tu `SecurityUtils.getRoleName()`.
- Neu `role == CUSTOMER` va `character.isDraft == true` => throw `InvalidRequestException` (HTTP 400).

### 5.3 Trash operations
- Soft delete: set `deleted_at = now()`.
- Restore: set `deleted_at = NULL`.
- Permanent delete: hard delete record.

Luu y quan trong: vi dang dung `@Where(clause = "deleted_at IS NULL")`, can dung query rieng (native query/JPQL bo qua `@Where`) de lay ban ghi trong trash va de restore.

## 6) Security/Authorization
- Mutating endpoints van giu `@PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")`.
- Customer khong co endpoint thao tac draft/trash.
- Chat endpoint:
  - Customer bi chan voi character draft.
  - Staff/Admin duoc phep.

## 7) Test Checklist

### 7.1 Draft/Publish
1. Staff tao character/context voi `isDraft=true` => khong hien thi cho customer.
2. Staff publish (`isDraft=false`) => customer thay va chat duoc.
3. Customer co gang tao chat voi character draft => bi reject dung thong diep.

### 7.2 Trash/Restore
1. Staff soft-delete => ban ghi bien mat khoi list active.
2. Staff xem trash list => co ban ghi vua xoa.
3. Restore => ban ghi quay lai list active, quan he du lieu giu nguyen.
4. Permanent delete => record bi xoa han trong DB.

### 7.3 Regression
1. Build pass: `mvn -DskipTests compile`.
2. Flyway migrate pass tren DB moi va DB da co du lieu.
3. Cac API cu van tuong thich (khong vo DTO contract hien co).

## 8) Implementation Order
1. Migration them `is_draft`.
2. Cap nhat Entity + DTO.
3. Cap nhat Repository queries cho role-based visibility.
4. Cap nhat Service (draft rules + chat guard + restore/permanent delete).
5. Cap nhat Controller endpoints staff trash/restore/permanent.
6. Chay compile + test smoke.
