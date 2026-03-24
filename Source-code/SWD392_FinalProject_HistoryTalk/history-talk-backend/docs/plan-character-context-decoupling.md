# Plan: Decouple Character Creation from HistoricalContext Mapping

Muc tieu: Bo rang buoc context khoi luong tao Character. Character va HistoricalContext duoc tao doc lap, sau do map/unmap bang API rieng. Chat session van bat buoc cap hop le `(characterId, contextId)`.

## 1) Problem Statement

Hien tai API tao Character cho phep/yeu cau `contextId` hoac `contextIds`, dan den:
- Contract khong ro rang (2 field cho cung mot nghiep vu).
- Character khong tao duoc neu chua co context.
- Tang do phuc tap cho FE khi tao moi du lieu co ban.
- De tao loi nghiep vu khi map context khong dung thoi diem.

## 2) Target Design

### 2.1 Domain rule moi
- Character la base entity, tao doc lap, khong can context.
- HistoricalContext la base entity, tao doc lap, khong can character.
- Quan he Character <-> HistoricalContext duoc quan ly boi API mapping rieng.
- Chat session create van yeu cau `characterId` + `contextId` va validate cap nay da duoc map.

### 2.2 Backward compatibility strategy
- Giai doan transition: van nhan `contextId/contextIds` trong request tao Character nhung danh dau deprecated.
- Release tiep theo: bo hoan toan 2 field khoi DTO/API contract.

## 3) Scope

### In scope
- Refactor DTO/service/controller cho create character khong can context.
- Them API map/unmap/list mapping.
- Refactor chat-session validation theo mapping table/relationship.
- Update swagger + docs.
- Them test case cho flow moi.

### Out of scope
- Thay doi giao dien FE chi tiet (chi cung cap contract/API).
- Thay doi model AI service.

## 3.1 Impact Assessment on Existing APIs

### Character read APIs
- `GET /v1/characters/{characterId}`:
  - Trang thai: van truy van duoc character ngay ca khi chua map context.
  - Impact: can bo sung check soft-delete cho CUSTOMER de dong bo voi context/document rules.
- `GET /v1/characters` (get all):
  - Trang thai: bi impact.
  - Nguyen nhan: query hien tai dung `JOIN c.historicalContexts hc` (inner join), nen character chua map context se bi loai khoi ket qua.
  - Yeu cau update: doi sang truy van ho tro character khong co context (left join hoac query tach theo co/khong co era).
- `GET /v1/characters/context/{contextId}`:
  - Trang thai: dung nghiep vu hien tai (chi tra character da map context).
  - Impact: khong can doi contract, chi can giu dung filter role/draft/deleted.

### APIs phu thuoc character-context mapping
- `POST /v1/chat/sessions`:
  - Trang thai: da validate cap `(characterId, contextId)` phai map.
  - Impact: tiep tuc giu rule nay sau khi decouple create character.
- `GET /v1/chat/sessions?contextId=&characterId=`:
  - Trang thai: phu thuoc contextId + characterId hop le.
  - Impact: khong doi contract.

## 4) API Contract Proposal

Tat ca endpoint mutate yeu cau STAFF/ADMIN.

### 4.1 Character creation/update
- `POST /v1/characters`
  - Remove business requirement cho `contextId/contextIds`.
  - Request chi gom thong tin character.
- `PUT /v1/characters/{characterId}`
  - Khong quan ly mapping context trong endpoint nay.

### 4.2 Mapping APIs (moi)
- `POST /v1/characters/{characterId}/contexts/{contextId}`
  - Tao lien ket character-context (idempotent: neu da co thi return success).
- `DELETE /v1/characters/{characterId}/contexts/{contextId}`
  - Go lien ket.
- `GET /v1/characters/{characterId}/contexts`
  - Lay danh sach context da map cho character.
- `GET /v1/historical-contexts/{contextId}/characters`
  - Da co, tiep tuc su dung; can dam bao filter role/draft/deleted dung rule.

### 4.3 Chat session create
- `POST /v1/chat/sessions`
  - Van yeu cau `characterId`, `contextId`.
  - Validate ton tai mapping character-context truoc khi tao session.
  - Neu khong map: throw `InvalidRequestException` (400).

## 5) Data Model and Migration

## 5.1 Existing relationship
- Neu da co many-to-many qua join table: khong can migration schema lon.
- Neu relation hien tai khong co unique constraint tren cap `(character_id, context_id)`: can bo sung.

### 5.2 Migration de xuat
Tao migration moi, vi du `V10__character_context_mapping_constraints.sql`:
1. Add unique index/constraint cho pair `(character_id, context_id)` trong join table.
2. Add index cho truy van nguoc `(context_id, character_id)`.

Luu y:
- Truoc khi add unique constraint, deduplicate du lieu trung lap neu co.

## 6) Implementation Steps

### Phase 0 - Stabilize read APIs (bat buoc truoc rollout)
1. Sửa `GET /v1/characters`:
- Thay query inner join bang left join hoac 2-query strategy:
  - Khong filter era: tra ca character chua map context.
  - Co filter era: chi tra character co it nhat mot context dung era.
2. Sửa `GET /v1/characters/{id}`:
- Non staff/admin khong duoc thay character soft-deleted (`deletedAt != null`).
3. Cap nhat swagger/docs response semantics:
- `context` co the `null`.
- `contexts` va `events` co the rong.

### Phase 1 - Contract soft transition (khong breaking)
1. `CreateCharacterRequest`
- Giu `contextId/contextIds` de tuong thich nguoc, nhung danh dau deprecated trong Swagger description.
- Service khong throw loi neu 2 field nay rong.
2. `CharacterServiceImpl.createCharacter`
- Bo bat buoc "At least one contextId is required".
- Neu co context trong request thi cho phep map ngay (optional) de khong vo client cu.
3. Them log warning khi request con gui `contextId/contextIds`.

### Phase 2 - Add explicit mapping APIs
1. Tao service methods:
- `addContextToCharacter(characterId, contextId, userId, userRole)`
- `removeContextFromCharacter(characterId, contextId, userId, userRole)`
- `getContextsOfCharacter(characterId, role)`
2. Them controller endpoints tuong ung.
3. Add repository helper query neu can cho check mapping existence nhanh.
4. Dam bao phan quyen mutate la STAFF/ADMIN.

### Phase 3 - Enforce clean contract (breaking in vNext)
1. Xoa `contextId/contextIds` khoi `CreateCharacterRequest`.
2. Update FE contract va API docs.
3. Remove code fallback map-context khi create character.

### Phase 4 - Chat/session validation hardening
1. Trong `ChatSessionService.createSession(...)`:
- Validate character ton tai va visible theo role.
- Validate context ton tai va visible theo role.
- Validate pair mapping ton tai.
2. Tra loi 404 neu entity khong ton tai/khong visible; 400 neu pair khong hop le.

### Phase 5 - Dependent API consistency pass
1. Rà soat cac API doc/list co nhung field lien quan context de dam bao khong null-pointer khi character chua map context.
2. Rà soat endpoint thong ke/tim kiem neu co su dung join bat buoc character-context.
3. Chot response code strategy nhat quan:
- 404: entity khong ton tai hoac khong visible theo role.
- 400: pair character-context khong hop le theo nghiep vu.

## 7) Security and Authorization Rules

- `POST/DELETE` mapping APIs: `@PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")`.
- `GET` mapping APIs:
  - CUSTOMER: chi thay context/character active, published, non-deleted.
  - STAFF/ADMIN: thay day du theo rule hien hanh (co the gom draft/deleted tuy endpoint).
- Tuyet doi khong dung header fallback de gia mao role/user.

## 8) Validation Rules

- Add mapping:
  - Character/context phai ton tai.
  - Khong cho map context soft-deleted cho customer-facing flows.
  - Duplicate map khong tao record moi (idempotent).
- Remove mapping:
  - Neu mapping khong ton tai, tra 404 hoac success idempotent (chon 1 va thong nhat).
- Chat create:
  - Pair khong map => `InvalidRequestException`.

## 9) Testing Checklist

### Unit tests
- get all character khong era filter -> bao gom character chua map context.
- get all character co era filter -> chi tra character co context trung era.
- get character by id voi role CUSTOMER + deletedAt != null -> 404.
- create character khong context -> success.
- create character co context deprecated fields -> success + mapping duoc tao (transition phase).
- add mapping duplicate -> idempotent.
- remove mapping -> dung behavior da chon.
- chat create voi pair khong map -> 400.

### Integration/API tests
- role CUSTOMER khong goi duoc mutate mapping APIs.
- STAFF/ADMIN goi duoc add/remove mapping.
- list contexts by character tra dung theo role va soft-delete/draft filter.
- regression cho get character by context va get contexts in character response.
- regression cho `GET /v1/chat/sessions` va `POST /v1/chat/sessions` khi character co/khong co mapping context.

### Build checks
- `mvn -DskipTests compile`
- Chay smoke API tren Swagger cho cac endpoint moi.

## 10) Rollout Plan

1. Deploy Phase 1 + Phase 2 truoc (khong breaking).
2. Thong bao FE migration sang flow moi:
- Buoc 1: tao Character
- Buoc 2: map context bang API rieng
- Buoc 3: tao chat session voi pair hop le
3. Sau khi FE migration xong, deploy Phase 3 de remove deprecated fields.

## 11) Risks and Mitigation

- Risk: Client cu phu thuoc context bat buoc khi tao character.
  - Mitigation: giu compatibility trong Phase 1.
- Risk: Duplicate mapping data gay loi unique constraint khi migrate.
  - Mitigation: script deduplicate truoc khi add constraint.
- Risk: Behavior khac nhau giua 404 va 400 lam FE confusion.
  - Mitigation: chot quy tac response code trong API contract.

## 12) Definition of Done

- `GET /v1/characters` khong bo sot character chua map context (trong case khong filter era).
- `GET /v1/characters/{id}` ap dung dung visibility rule cho draft + soft-delete.
- Character tao moi khong can context va khong loi validation.
- Mapping APIs add/remove/list hoat dong dung role policy.
- Chat session create chi thanh cong khi pair character-context hop le.
- Swagger/README cap nhat day du.
- Build compile pass va test checklist dat yeu cau.
