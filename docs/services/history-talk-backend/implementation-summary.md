# Implementation Summary – Authentication Redesign

**Date Completed:** March 6, 2026  
**Build Status:** ✅ `mvn clean compile` – BUILD SUCCESS  

---

## Kết Quả

Toàn bộ auth module đã được viết lại từ đầu. Dự án biên dịch thành công, không còn lỗi `java.com.historyTalk.*` hay import từ `com.schoolhealth.*`.

---

## Danh Sách File Đã Thay Đổi

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `repository/UserRepository.java` | JpaRepository cho User – `findByEmailIgnoreCase`, `findByUserNameIgnoreCase`, `existsByEmail/UserName` |
| `security/UserPrincipal.java` | UserDetails adapter cho User entity; authorities build từ `userType` + `roleName` |
| `docs/authentication-plan.md` | Tài liệu plan + solution architecture |
| `docs/implementation-summary.md` | File này |

### ✏️ File Viết Lại Hoàn Toàn

| File | Thay đổi chính |
|------|---------------|
| `security/JwtTokenProvider.java` | `@Value` đổi sang `JWT_SECRET`/`JWT_EXPIRATION_MS`/`JWT_REFRESH_EXPIRATION_MS`; thêm `generateAccessToken(email, claims)`, `generateRefreshToken(email)`, `getAllClaims(token)` |
| `security/JwtAuthenticationFilter.java` | Đọc claims `userType`/`roleName` từ JWT để build Spring Security authorities; fallback header `X-Staff-Id`/`X-Staff-Role` cho Swagger testing |
| `service/authentication/JwtService.java` | Interface mới: `generateAccessToken`, `generateRefreshToken`, `extractEmail`, `isTokenValid`, `getAccessTokenExpirationMs` |
| `service/authentication/JwtServiceImpl.java` | Delegates sang `JwtTokenProvider`; xóa bỏ schoolhealth dependencies |
| `service/authentication/CustomUserDetailsService.java` | Dùng `UserRepository` của HistoryTalk; load by email; trả `UserPrincipal` |
| `service/authentication/AuthService.java` | Interface: `register`, `login`, `refreshToken`, `logout` |
| `service/authentication/AuthServiceImpl.java` | In-memory token blacklist (`ConcurrentHashMap`); `register` tạo REGISTERED user; `login` dùng `AuthenticationManager` + build JWT claims `{uid, userType, roleName?}` |
| `controller/authentication/AuthController.java` | Package fix; 4 endpoints: `POST /api/v1/auth/{register,login,refresh-token,logout}`; response wrap bằng `ApiResponse<T>` |
| `dto/authentication/LoginRequest.java` | `email` + `password` |
| `dto/authentication/LoginResponse.java` | `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `uid`, `userName`, `email`, `userType` |
| `dto/authentication/RegisterRequest.java` | `userName`, `email`, `password`, `confirmPassword` |
| `dto/authentication/RegisterResponse.java` | `uid`, `userName`, `email`, `userType`, `message` |
| `dto/authentication/RefreshTokenRequest.java` | `refreshToken` |
| `dto/authentication/RefreshTokenResponse.java` | `accessToken`, `refreshToken`, `tokenType`, `expiresIn` |
| `dto/authentication/LogoutResponse.java` | `message` |

### 🔧 File Sửa Package / Cấu Trúc

| File | Thay đổi |
|------|---------|
| `HistoryTalkApplication.java` | `java.com.historyTalk` → `com.historyTalk` |
| `entity/UserType.java` | Xóa `GUEST`; giữ `REGISTERED` và `STAFF` |
| `config/SecurityConfig.java` | Thêm `PasswordEncoder`, `AuthenticationProvider`, `AuthenticationManager` beans; fix `DaoAuthenticationProvider(PasswordEncoder)` constructor; xóa duplicate class body |
| `config/SpringSecurityConfig.java` | Thay bằng stub rỗng – không có `@Configuration` để tránh conflict với `SecurityConfig` |
| `config/CustomAccessDeniedHandler.java` | Fix package `java.com.historyTalk` → `com.historyTalk` |
| `config/RestAuthenticationEntryPoint.java` | Fix package |
| `config/SwaggerConfig.java` | Fix package |
| `utils/authentication/JwtProperties.java` | Fix package |
| `utils/authentication/JwtUtils.java` | Fix package |
| `controller/historicalContext/HistoricalContextController.java` | Fix package + service import |
| `controller/historicalContext/HistoricalContextDocumentController.java` | Fix package + service import |
| `service/historicalContext/HistoricalContextService.java` | Fix package |
| `service/historicalContext/HistoricalContextDocumentService.java` | Fix package |

### 🗃️ File Stub (Preserved, Không Dùng)

| File | Trạng thái |
|------|-----------|
| `dto/user/UserInformationRequest.java` | Fix package; giữ nội dung (dùng sau) |
| `mapper/user/UserInformationMapper.java` | Stub rỗng – schoolhealth types đã xóa |
| `mapper/user/UserInformationMapperImpl.java` | Stub rỗng – schoolhealth dependencies đã xóa |
| `jwtFilter/JwtAuthenticationFilter.java` | Stub rỗng – `@Component` đã xóa; active filter là `security/JwtAuthenticationFilter.java` |

### ⚙️ Config

| File | Thay đổi |
|------|---------|
| `resources/secretKey.properties` | `DB_URL` → `jdbc:postgresql://localhost:5432/history_talk_db`; `DB_SCHEMA` → `public` |

---

## Hotfix – HistoricalContextRepository ILIKE (March 6, 2026)

**Lỗi runtime:**
```
FunctionArgumentException: Parameter 1 of function 'lower()' has type 'STRING',
but argument is of type 'java.lang.String'
```

**Nguyên nhân:** Hibernate 6.3.1 có regression bug — `lower()` trong HQL bị reject ngay cả khi áp dụng lên entity path (`hc.name`), không chỉ riêng parameters.

**Fix trong [HistoricalContextRepository.java](../src/main/java/com/historyTalk/repository/HistoricalContextRepository.java):**
- Thay `LOWER(hc.name) LIKE CONCAT('%', :search, '%')` bằng `hc.name ILIKE CONCAT('%', :search, '%')`
- Hibernate 6 hỗ trợ `ILIKE` keyword trong HQL natively, dịch sang `ILIKE` trên PostgreSQL
- Không cần `LOWER()` nữa — `ILIKE` đã xử lý case-insensitive matching
- `normalize()` trong service vẫn giữ `.toLowerCase()` để uniform search input

---

## Luồng Xác Thực Sau Khi Triển Khai

```
[Client]
   │
   ├─ POST /api/v1/auth/register ──► AuthController.register()
   │                                      │
   │                                 AuthServiceImpl.register()
   │                                      │
   │                                 UserRepository.save(user)  ← userType=REGISTERED
   │                                      │
   │                                 201 RegisterResponse{uid, userName, email, userType}
   │
   ├─ POST /api/v1/auth/login ─────► AuthController.login()
   │                                      │
   │                                 AuthenticationManager.authenticate()
   │                                      │
   │                                 UserRepository.findByEmailIgnoreCase()
   │                                      │
   │                                 JwtService.generateAccessToken(email, claims{uid,userType,roleName?})
   │                                 JwtService.generateRefreshToken(email)
   │                                      │
   │                                 200 LoginResponse{tokens, user info}
   │
   ├─ [Subsequent requests]
   │    Authorization: Bearer <accessToken>
   │         │
   │    JwtAuthenticationFilter.doFilterInternal()
   │         ├─ check BLACKLISTED_TOKENS
   │         ├─ JwtTokenProvider.extractEmail(token)
   │         ├─ build authorities from claims: userType + roleName
   │         └─ SecurityContext.setAuthentication()
   │
   ├─ POST /api/v1/auth/refresh-token ─► AuthServiceImpl.refreshToken()
   │                                          │
   │                                     validate + !blacklisted
   │                                     generateAccessToken() mới
   │                                     200 RefreshTokenResponse
   │
   └─ POST /api/v1/auth/logout ────────► AuthServiceImpl.logout()
                                              │
                                         BLACKLISTED_TOKENS.add(token)
                                         200 LogoutResponse{message}
```

---

## Các Điểm Lưu Ý (Known Limitations)

| # | Vấn Đề | Ghi Chú |
|---|--------|--------|
| 1 | **JWT Blacklist là in-memory** | Mất khi restart. Production cần Redis hoặc DB table |
| 2 | **CORS wildcard `*`** | `allowCredentials=false`; production cần giới hạn origins |
| 3 | **secretKey.properties chứa real password** | Đã gitignore; KHÔNG push lên remote |
| 4 | **Mapper/UserInformation stubs** | `UserInformationMapper` và `UserInformationMapperImpl` đang là stub rỗng; cần implement khi làm user profile |

---

## Character CRUD Module (March 6, 2026)

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `repository/CharacterRepository.java` | `findAllWithSearch(search)` dùng ILIKE; `findByHistoricalContextContextId`; `findByStaffStaffId` |
| `dto/character/CreateCharacterRequest.java` | `name`, `background`, `image`, `personality`, `contextId`; staffId từ header |
| `dto/character/UpdateCharacterRequest.java` | Tất cả fields optional |
| `dto/character/CharacterResponse.java` | Nested `ContextInfo` + `StaffInfo` |
| `service/character/CharacterService.java` | CRUD + search + getByContext; ownership check dùng `InvalidRequestException` |
| `controller/character/CharacterController.java` | 6 endpoints; `@PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")` trên write ops |

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `config/SecurityConfig.java` | Thêm routes `/v1/characters/**` — GET public, POST/PUT/DELETE authenticated |

### API Endpoints

| Method | Path | Auth |
|--------|------|------|
| GET | `/v1/characters?search=` | Public |
| GET | `/v1/characters/{id}` | Public |
| GET | `/v1/characters/context/{ctxId}` | Public |
| POST | `/v1/characters` | STAFF / ADMIN |
| PUT | `/v1/characters/{id}` | STAFF / ADMIN |
| DELETE | `/v1/characters/{id}` | STAFF / ADMIN |

---

## Staff Account Registration (March 6, 2026)

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `repository/RoleRepository.java` | `findByRoleNameIgnoreCase(roleName)` |
| `dto/authentication/RegisterStaffRequest.java` | `userName`, `name`, `email`, `password`, `confirmPassword`, `roleName` |
| `dto/authentication/RegisterStaffResponse.java` | `uid`, `staffId`, `userName`, `name`, `email`, `roleName` |

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `service/authentication/AuthService.java` | Thêm method `registerStaff(RegisterStaffRequest)` |
| `service/authentication/AuthServiceImpl.java` | Inject `StaffRepository` + `RoleRepository`; implement `registerStaff` — tạo `Staff` record rồi `User` (type=STAFF) linked với Staff |
| `controller/authentication/AuthController.java` | Thêm `POST /api/v1/auth/register-staff` — yêu cầu `ROLE_ADMIN` |
| `config/SecurityConfig.java` | `/api/v1/auth/register-staff` → `authenticated()`; phần còn lại `/api/v1/auth/**` → `permitAll()` |

### Lưu Ý

- `roleName` trong request phải tồn tại trong bảng `role` của DB (phải seed trước).
- `UserPrincipal.buildAuthorities()` tự gán `ROLE_STAFF` + `ROLE_<roleName>` khi login.

---

## Enum Fields – EventEra, EventCategory, MessageRole (March 6, 2026)

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `entity/enums/EventEra.java` | `ANCIENT`, `MEDIEVAL`, `MODERN`, `CONTEMPORARY` |
| `entity/enums/EventCategory.java` | `WAR`, `POLITICS`, `CULTURE`, `SCIENCE`, `RELIGION`, `OTHER` |
| `entity/enums/MessageRole.java` | `USER`, `ASSISTANT` |

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `entity/historicalContext/HistoricalContextDocument.java` | ~~Thêm `era`, `category`, `year`/`startYear`/`endYear`~~ → **đã xóa, xem correction bên dưới** |
| `entity/chat/Message.java` | Thêm `role` (`MessageRole`); `@PrePersist` tự derive từ `is_from_ai` nếu `role == null` |

> ⚠️ **Correction (March 7, 2026):** `era`, `category`, `year`, `startYear`, `endYear` ban đầu được thêm nhầm vào `HistoricalContextDocument`. Các field này đã được **chuyển sang `HistoricalContext`** — xem section bên dưới.

---

## Enum Fields Migration: Document → Context (March 7, 2026)

**Lý do:** `era`, `category`, `year`, `startYear`, `endYear` là thông tin của *thời đại lịch sử* (context), không phải của từng *tài liệu riêng lẻ*. Tất cả document thuộc một context đều chia sẻ cùng era/category/timespan.

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `entity/historicalContext/HistoricalContext.java` | Thêm `era` (`EventEra`), `category` (`EventCategory`), `year`/`startYear`/`endYear` (`Integer`) — tất cả nullable |
| `entity/historicalContext/HistoricalContextDocument.java` | Xóa `era`, `category`, `year`, `startYear`, `endYear` và các import liên quan |
| `dto/historicalContext/CreateHistoricalContextRequest.java` | Thêm `era`, `category`, `year`, `startYear`, `endYear` — optional |
| `dto/historicalContext/UpdateHistoricalContextRequest.java` | Tương tự — optional |
| `dto/historicalContext/HistoricalContextResponse.java` | Thêm `era`, `category`, `year`, `startYear`, `endYear`; `period` computed = `"startYear–endYear"` |
| `dto/historicalContext/CreateHistoricalContextDocumentRequest.java` | Xóa 5 fields trên |
| `dto/historicalContext/UpdateHistoricalContextDocumentRequest.java` | Xóa 5 fields trên |
| `dto/historicalContext/HistoricalContextDocumentResponse.java` | Xóa 5 fields + `period` |
| `service/historicalContext/HistoricalContextService.java` | `createContext`/`updateContext` set 5 fields mới; `mapToResponse` tính `period` |
| `service/historicalContext/HistoricalContextDocumentService.java` | Xóa mapping era/category/year trong create, update, mapToResponse |

### Thiết Kế `period`

`period` **không lưu DB** — được tính toán tại tầng service:
```java
period = (startYear != null && endYear != null) ? startYear + "\u2013" + endYear : null
```
- `year` — năm sự kiện cụ thể xảy ra (VD: `938`)
- `startYear` / `endYear` — khoảng thời gian (VD: `938` / `1857`)
- `period` — computed string trả về trong response (VD: `"938–1857"`)

### DB Migration

`ddl-auto=update` — Hibernate tự `ALTER TABLE historical_context` thêm column mới (`era`, `category`, `year`, `start_year`, `end_year`). Các column cũ trên `historical_context_document` vẫn còn trong DB nhưng không được map — cần drop thủ công nếu muốn dọn schema.

---

## Character API Enhancement (March 7, 2026)

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `entity/character/Character.java` | Thêm `title` (VARCHAR 150), `lifespan` (VARCHAR 50), `side` (VARCHAR 100) — tất cả nullable |
| `repository/CharacterRepository.java` | Thay `findAllWithSearch(search): List` bằng `findAllWithFilter(search, era, pageable): Page` với split count query; `era` filter từ `historicalContext.era` |
| `dto/character/CharacterResponse.java` | Thêm `title`, `lifespan`, `side`, `era` (`EventEra`), `events[]` (nested `EventInfo{id, name, era, year}`) |
| `dto/character/CreateCharacterRequest.java` | Thêm optional `title`, `lifespan`, `side` |
| `dto/character/UpdateCharacterRequest.java` | Thêm optional `title`, `lifespan`, `side` |
| `service/character/CharacterService.java` | `getAllCharacters` → `PaginatedResponse<CharacterResponse>`; `era` string → `EventEra` enum (throws `InvalidRequestException` nếu invalid); limit capped at 20; `mapToResponse` populate `era` + `events[]` từ `historicalContext` |
| `controller/character/CharacterController.java` | `GET /v1/characters` nhận `era`, `page` (default 1), `limit` (default 8); trả `PaginatedResponse` |

### API Endpoints (Cập Nhật)

| Method | Path | Query Params | Auth |
|--------|------|-------------|------|
| GET | `/v1/characters` | `search`, `era`, `page` (default 1), `limit` (default 8, max 20) | Public |
| GET | `/v1/characters/{id}` | — | Public |
| GET | `/v1/characters/context/{ctxId}` | — | Public |
| POST | `/v1/characters` | — | STAFF / ADMIN |
| PUT | `/v1/characters/{id}` | — | STAFF / ADMIN |
| DELETE | `/v1/characters/{id}` | — | STAFF / ADMIN |

### `events[]` Shape

`events[]` lấy từ `character.historicalContext.documents` (lazy-fetched), mỗi item:
```json
{
  "id": "<docId>",
  "name": "<document title>",
  "era": "<EventEra từ historicalContext>",
  "year": "<Integer year từ historicalContext>"
}
```

---

## Cách Test Nhanh (Swagger)

1. Chạy service: `mvn spring-boot:run`
2. Mở Swagger: `http://localhost:8080/swagger-ui/index.html`
3. Thử `POST /api/v1/auth/register` với body JSON
4. Thử `POST /api/v1/auth/login` → Copy `accessToken`
5. Nhấn **Authorize** → nhập `Bearer <accessToken>`
6. Thử các endpoint historical-context có authentication

---

## Historical Context – Events API Enhancement (March 7, 2026)

### Mục Tiêu
Nâng cấp `GET /v1/historical-contexts` để đáp ứng requirement `/events` frontend: pagination, filter `era`/`category`, thêm field `yearLabel`, `location`, `imageUrl`, `beforeTCN`.

### ✏️ File Sửa

| File | Thay đổi |
|------|---------|
| `entity/historicalContext/HistoricalContext.java` | Thêm `beforeTCN` (`Boolean`, default `false`, NOT NULL), `location` (`VARCHAR 255`, nullable), `imageUrl` (`VARCHAR 500`, nullable) |
| `repository/HistoricalContextRepository.java` | Thêm import `EventEra`, `EventCategory`; sửa `findAllWithSearch` nhận thêm `era` + `category` params với filter `AND (:era IS NULL OR hc.era = :era) AND (:category IS NULL OR hc.category = :category)` |
| `dto/historicalContext/HistoricalContextResponse.java` | Thêm `yearLabel` (String), `beforeTCN` (Boolean), `location` (String), `imageUrl` (String) |
| `dto/historicalContext/CreateHistoricalContextRequest.java` | Thêm `beforeTCN` (Boolean, default `false`), `location` (`@Size` max 255), `imageUrl` (`@Size` max 500) |
| `dto/historicalContext/UpdateHistoricalContextRequest.java` | Thêm `beforeTCN` (Boolean), `location` (`@Size` max 255), `imageUrl` (`@Size` max 500) |
| `service/historicalContext/HistoricalContextService.java` | `getAllContexts` thêm `EventEra era`, `EventCategory category`; `createContext`/`updateContext` set 3 fields mới; `mapToResponse` tính `yearLabel` + populate `beforeTCN`, `location`, `imageUrl` |
| `controller/historicalContext/HistoricalContextController.java` | `GET /v1/historical-contexts` đổi từ `getAllContextsSimple` sang paginated `getAllContexts`; thêm query params `era`, `category`, `page`, `limit`; thêm import `PageRequest`, `Sort`, `EventEra`, `EventCategory`, `PaginatedResponse` |

### Thiết Kế `yearLabel`

`yearLabel` **không lưu DB** — tính tại tầng service:
```java
yearLabel = (year != null)
    ? year + (Boolean.TRUE.equals(beforeTCN) ? " TCN" : " SCN")
    : null;
```
- `beforeTCN = true` + `year = 111` → `yearLabel = "111 TCN"`
- `beforeTCN = false` + `year = 938` → `yearLabel = "938 SCN"`

### API Endpoints (Cập Nhật)

| Method | Path | Query Params | Auth | Response |
|--------|------|-------------|------|---------|
| GET | `/v1/historical-contexts` | `search`, `era`, `category`, `page` (default 1), `limit` (default 6) | Public | `PaginatedResponse<HistoricalContextResponse>` |
| GET | `/v1/historical-contexts/{contextId}` | — | Public | `HistoricalContextResponse` |
| POST | `/v1/historical-contexts` | — | STAFF / ADMIN | `HistoricalContextResponse` |
| PUT | `/v1/historical-contexts/{contextId}` | — | STAFF / ADMIN | `HistoricalContextResponse` |
| DELETE | `/v1/historical-contexts/{contextId}` | — | STAFF / ADMIN | `204 No Content` |

### Response Shape (`HistoricalContextResponse`)

```json
{
  "contextId": "uuid-string",
  "name": "Trận Bạch Đằng",
  "description": "...",
  "era": "ANCIENT",
  "category": "WAR",
  "year": 938,
  "startYear": null,
  "endYear": null,
  "period": null,
  "yearLabel": "938 SCN",
  "beforeTCN": false,
  "location": "Sông Bạch Đằng, Quảng Ninh",
  "imageUrl": "/images/events/bach-dang.jpg",
  "createdBy": { "staffId": "...", "name": "..." },
  "createdDate": "2026-03-07T10:00:00",
  "updatedDate": "2026-03-07T10:00:00"
}
```

### DB Migration

`ddl-auto=update` — Hibernate tự `ALTER TABLE historical_context` thêm 3 column mới: `before_tcn` (boolean not null default false), `location` (varchar 255), `image_url` (varchar 500).

### Lưu Ý

- `getAllContextsSimple(search)` vẫn còn trong service — dùng nội bộ nếu cần list không paginate
- `page` param là **1-based** (frontend friendly); service convert sang 0-based trước khi truyền `PageRequest`
- `era` / `category` filter dùng `IS NULL` check trong JPQL nên không truyền param = trả toàn bộ (không filter)

---

## Role System Redesign – Staff → UserRole Enum (March 7, 2026)

**Build Status:** ✅ `mvn compile` – BUILD SUCCESS

### Mục Tiêu

Đơn giản hóa model phân quyền: xóa các entity/table phụ (`Staff`, `RoleTable`), thay thế bằng enum `UserRole` gắn trực tiếp lên `User`. Tất cả nội dung entity chuyển từ FK `Staff` sang FK `User createdBy`.

### 🗑️ File Đã Xóa

| File | Lý do |
|------|-------|
| `entity/user/UserType.java` | Thay bằng `UserRole` enum |
| `entity/staff/Staff.java` | Không còn Staff entity riêng |
| `entity/RoleTable.java` | Role giờ là enum field trên User |
| `repository/StaffRepository.java` | Không còn Staff entity |
| `repository/RoleRepository.java` | Không còn RoleTable entity |

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `entity/enums/UserRole.java` | Enum: `CUSTOMER \| STAFF \| ADMIN` |

### ✏️ File Đã Sửa

| File | Thay đổi chính |
|------|---------------|
| `entity/user/User.java` | Xóa `UserType userType` + FK `Staff staff`; thêm `@Enumerated(EnumType.STRING) UserRole role` |
| `entity/historicalContext/HistoricalContext.java` | `@JoinColumn(name="staff_id") Staff staff` → `@JoinColumn(name="created_by") User createdBy` |
| `entity/historicalContext/HistoricalContextDocument.java` | Như trên; `@Index(name="idx_staff_id")` → `idx_created_by` |
| `entity/character/Character.java` | `Staff staff` → `User createdBy` |
| `entity/character/CharacterDocument.java` | `Staff staff` → `User createdBy` |
| `entity/quiz/Quiz.java` | `Staff staff` → `User createdBy` |
| `security/UserPrincipal.java` | Xóa `staffId`, `roleName`, `userType`; authority = single `ROLE_<UserRole>` |
| `security/AuthenticatedPrincipal.java` | Fields rút gọn: `email`, `uid`, `role` (String) |
| `security/JwtAuthenticationFilter.java` | Đọc `uid` + `role` từ JWT; build authority `ROLE_<role>`; fallback header `X-Staff-Role` vẫn giữ cho Swagger testing |
| `utils/SecurityUtils.java` | `getStaffId()` → `getUserId()` — trả `ap.getUid()` |
| `dto/authentication/LoginResponse.java` | `UserType userType` → `String role` |
| `dto/authentication/RegisterResponse.java` | `UserType userType` → `String role` |
| `dto/authentication/RegisterStaffRequest.java` | Xóa `name`, `roleName`; thêm `String role` (nhận `"STAFF"` hoặc `"ADMIN"`) |
| `dto/authentication/RegisterStaffResponse.java` | Xóa `staffId`, `name`; `roleName` → `role` |
| `dto/historicalContext/HistoricalContextResponse.java` | `CreatedByInfo.staffId` → `uid`; `CreatedByInfo.name` → `userName` |
| `dto/historicalContext/HistoricalContextDocumentResponse.java` | `staffId` → `uid`; `staffName` → `userName` |
| `dto/character/CharacterResponse.java` | `StaffInfo.staffId` → `uid`; `StaffInfo.name` → `userName` |
| `service/authentication/AuthServiceImpl.java` | Xóa inject `StaffRepository`, `RoleRepository`; `register` dùng `UserRole.CUSTOMER`; `registerStaff` validate role enum, tạo `User` với role trực tiếp; JWT claims: `uid` + `role` |
| `service/historicalContext/HistoricalContextService.java` | Inject `UserRepository` thay `StaffRepository`; dùng `User createdBy`; ownership: `getCreatedBy().getUid()` |
| `service/historicalContext/HistoricalContextDocumentService.java` | Như trên |
| `service/character/CharacterService.java` | Inject `UserRepository`; `getCreatedBy().getUid()` |
| `repository/HistoricalContextRepository.java` | `findByStaffStaffId(UUID, Pageable)` → `findByCreatedByUid(UUID, Pageable)` |
| `repository/HistoricalContextDocumentRepository.java` | `findByStaffStaffIdOrderByUploadDateDesc(UUID)` → `findByCreatedByUidOrderByUploadDateDesc(UUID)` |
| `controller/character/CharacterController.java` | `SecurityUtils.getStaffId()` → `SecurityUtils.getUserId()` |
| `controller/historicalContext/HistoricalContextController.java` | Như trên |
| `controller/historicalContext/HistoricalContextDocumentController.java` | Như trên |

### JWT Claims Mới

| Claim | Giá trị |
|-------|--------|
| `sub` | email |
| `uid` | User UUID (String) |
| `role` | `"CUSTOMER"` / `"STAFF"` / `"ADMIN"` |

**Xóa bỏ:** `userType`, `staffId`, `roleName`

### Response Shape Thay Đổi

`createdBy` trong các response DTO:

**Trước:**
```json
"createdBy": { "staffId": "...", "name": "Nguyễn Văn A" }
```

**Sau:**
```json
"createdBy": { "uid": "...", "userName": "nguyenvana" }
```

### DB Migration

`ddl-auto=update` — Hibernate tự:
- `ALTER TABLE historical_context` đổi column `staff_id` → `created_by` (FK sang `user`)
- Tương tự trên `historical_context_document`, `character`, `character_document`, `quiz`
- `ALTER TABLE user` thêm column `role` (VARCHAR, NOT NULL)

### Lưu Ý

- `getUserId()` trong `SecurityUtils` trả `uid` của `User` — **không phải** staffId cũ
- `X-Staff-Id` / `X-Staff-Role` headers vẫn còn trong `JwtAuthenticationFilter` **chỉ cho Swagger testing** — không dùng trong business logic
- `registerStaff` endpoint vẫn là `POST /v1/auth/register-staff`, yêu cầu `ROLE_ADMIN`; `role` field trong request nhận `"STAFF"` hoặc `"ADMIN"`

---

## Chat Sessions Module (March 8, 2026)

**Build Status:** ✅ `mvn compile` – BUILD SUCCESS

### Mục Tiêu

Implement 3 API endpoints quản lý chat sessions: lấy danh sách, tạo mới, xóa. Sessions thuộc về user đang đăng nhập — không có admin override.

### Flyway Migration

`V3__add_chat_session_fields.sql` — thêm 2 column vào `chat_session`:
```sql
ALTER TABLE historical_schema.chat_session
    ADD COLUMN IF NOT EXISTS title VARCHAR(255) DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP;
```

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `entity/chat/ChatSession.java` | Thêm `title` (VARCHAR 255, default `""`), `lastMessageAt` (TIMESTAMP nullable) |
| `repository/ChatSessionRepository.java` | `findByUserAndCharacterAndContext(userId, charId, ctxId)` — sorted by `lastMessageAt DESC NULLS LAST`; `findBySessionIdAndUserUid(sessionId, userId)` — ownership check |
| `dto/chat/CreateChatSessionRequest.java` | `contextId`, `characterId` — cả hai `@NotBlank` |
| `dto/chat/ChatSessionResponse.java` | `id`, `characterId`, `contextId`, `title`, `lastMessage`, `lastMessageAt`, `messageCount` |
| `service/chat/ChatSessionService.java` | `getSessions`, `createSession`, `deleteSession` |
| `controller/chat/ChatController.java` | 3 endpoints (xem bên dưới) |

### API Endpoints

| Method | Path | Query/Path | Auth | Response |
|--------|------|-----------|------|---------|
| GET | `/v1/chat/sessions` | `?contextId=&characterId=` | JWT bắt buộc | `ApiResponse<List<ChatSessionResponse>>` |
| POST | `/v1/chat/sessions` | body: `{contextId, characterId}` | JWT bắt buộc | `ApiResponse<ChatSessionResponse>` 201 |
| DELETE | `/v1/chat/sessions/{id}` | path: `sessionId` | JWT bắt buộc | 204 No Content |

### Business Rules

- `contextId` = `eventId` (tên gọi khác nhau, trỏ đến `HistoricalContext.contextId`)
- `createSession`: validate `character.historicalContext.contextId == request.contextId` → `InvalidRequestException` nếu không khớp
- `deleteSession`: dùng `findBySessionIdAndUserUid` — trả 404 nếu session không tồn tại **hoặc** không thuộc caller (tránh leak existence)
- `lastMessage`: derive từ message có `timestamp` lớn nhất trong `session.getMessages()` — không query riêng
- `messageCount`: `session.getMessages().size()`
- `CascadeType.ALL + orphanRemoval=true` trên `messages` — delete session tự xóa toàn bộ messages

### Response Shape

```json
{
  "id": "uuid-string",
  "characterId": "uuid-string",
  "contextId": "uuid-string",
  "title": "",
  "lastMessage": null,
  "lastMessageAt": null,
  "messageCount": 0
}
```

### SecurityConfig

`/v1/chat/**` — tất cả require JWT authenticated (không có route public). Đã thêm comment vào khối `authorizeHttpRequests` trong `SecurityConfig.java`.

---

## Chat Messages & History Module (March 8, 2026)

**Build Status:** ✅ `mvn compile` – BUILD SUCCESS

### Mục Tiêu

Implement 3 API endpoints: lấy danh sách tin nhắn của session, gửi tin nhắn (gọi AI), lấy toàn bộ lịch sử chat gộp theo event. Tích hợp với BE-Python qua `AiServiceClient`.

### Luồng Tích Hợp BE-Java ↔ BE-Python

```
FE → BE-Java → BE-Python (chỉ khi cần LLM)

BE-Java gọi BE-Python ở 3 thời điểm:
  1. createSession  → POST /v1/ai/chat (greeting message)
  2. sendMessage    → POST /v1/ai/chat (user message)
  3. sendMessage (tin đầu tiên) → POST /v1/ai/generate-title (async @EnableAsync)

BE-Java pre-fill characterData + contextData trong request → BE-Python không callback lại BE-Java.
```

### Flyway Migration

`V3__add_message_suggested_questions.sql`:
```sql
ALTER TABLE historical_schema.message
    ADD COLUMN IF NOT EXISTS suggested_questions TEXT;
```

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `repository/MessageRepository.java` | `findByChatSessionSessionIdOrderByTimestampAsc(UUID)` |
| `service/chat/AiServiceClient.java` | Spring `RestClient` gọi BE-Python; `chat()` + `generateTitleAsync()` (@Async) + builder helpers |
| `dto/chat/MessageResponse.java` | `id`, `sessionId`, `role`, `content`, `createdAt` |
| `dto/chat/GetMessagesResponse.java` | `messages[]`, `suggestedQuestions[]` |
| `dto/chat/SendMessageRequest.java` | `sessionId`, `content` (@NotBlank, @Size max 4000) |
| `dto/chat/SendMessageResponse.java` | `userMessage`, `assistantMessage`, `suggestedQuestions[]` |
| `dto/chat/ChatHistorySessionItem.java` | Một session trong history (character + context info + lastMessage) |
| `dto/chat/ChatHistoryGroupResponse.java` | Group theo context: `contextId`, `contextName`, `sessions[]` |
| `service/chat/MessageService.java` | `getMessages()` + `sendMessage()` với đầy đủ AI flow |
| `service/chat/ChatHistoryService.java` | `getHistory()` — group by contextId, sort by lastMessageAt |

### ✏️ File Đã Sửa

| File | Thay đổi |
|------|----------|
| `entity/chat/Message.java` | Thêm `suggestedQuestions TEXT` — lưu JSON array string, chỉ có ở ASSISTANT message |
| `repository/ChatSessionRepository.java` | Thêm `findAllByUserUid(UUID)` — dùng cho GET /chat/history |
| `service/chat/ChatSessionService.java` | `createSession` gọi AI greeting sau khi save; thêm inject `AiServiceClient`, `MessageRepository`, `ObjectMapper` |
| `controller/chat/ChatController.java` | Thêm 3 endpoint mới (xem bên dưới) |
| `HistoryTalkApplication.java` | Thêm `@EnableAsync` |
| `secretKey.properties` | Thêm `AI_SERVICE_URL=http://localhost:8001` |

### API Endpoints (Đầy đủ sau khi update)

| Method | Path | Auth | Response |
|--------|------|------|----------|
| GET | `/v1/chat/sessions?contextId=&characterId=` | JWT | `ApiResponse<List<ChatSessionResponse>>` |
| POST | `/v1/chat/sessions` | JWT | `ApiResponse<ChatSessionResponse>` 201 |
| DELETE | `/v1/chat/sessions/{id}` | JWT | 204 |
| GET | `/v1/chat/sessions/{id}/messages` | JWT | `ApiResponse<GetMessagesResponse>` |
| POST | `/v1/chat/messages` | JWT | `ApiResponse<SendMessageResponse>` 201 |
| GET | `/v1/chat/history` | JWT | `ApiResponse<List<ChatHistoryGroupResponse>>` |

### Business Rules

- `sendMessage`: lưu userMessage trước → load history → gọi AI → lưu assistantMessage → update `lastMessageAt`
- `suggestedQuestions`: lấy từ ASSISTANT message cuối cùng (parse JSON string), `[]` nếu không có
- `isFirstUserMessage`: nếu là tin nhắn USER đầu tiên trong session → async generate-title
- `createSession greeting`: gọi AI với `"Hãy chào và giới thiệu ngắn gọn về bản thân."`, lỗi AI không làm fail session creation (try-catch + log.warn)
- `ChatHistoryService`: group sessions by contextId, sort sessions trong group by `lastMessageAt DESC NULLS LAST`, sort groups by max `lastMessageAt` của group
- `SendMessageRequest` chỉ gửi `sessionId` + `content` — `characterId`/`contextId` lấy từ session trong DB

### `AiServiceClient` Pre-fill Pattern

`AiServiceClient.buildCharacterPayload(Character)` và `buildContextPayload(HistoricalContext)` convert entity → payload record gồm các field BE-Python cần để build prompt, truyền vào request để BE-Python không phải callback BE-Java.

---

## Hotfix – AiServiceClient 422 Unprocessable Entity (March 8, 2026)

**Build Status:** ✅ `mvn clean compile` – BUILD SUCCESS

**Lỗi runtime:**
```
com.historyTalk.exception.SystemException: AI service unavailable:
  422 Unprocessable Entity: {"detail":[{"type":"missing","loc":["body"],"msg":"Field required","input":null}]}
```

**Nguyên nhân (2 vấn đề cộng hưởng):**

| # | Vấn đề | Hậu quả |
|---|--------|---------|
| 1 | `private record ChatRequest/GenerateTitleRequest/...` — Jackson không thể introspect nested `private` class | Serialize thành `null` body thay vì JSON |
| 2 | `RestClient.builder()` (static factory) — tạo builder với bare-bones `ObjectMapper` mặc định, không có full record support của Spring Boot | Request body không serialize đúng |

**Fix trong `service/chat/AiServiceClient.java`:**

1. **Inject `RestClient.Builder` bean** thay vì gọi static `RestClient.builder()`:

```java
// Trước (sai)
public AiServiceClient(@Value("${AI_SERVICE_URL:...}") String url, ...) {
    this.restClient = RestClient.builder().baseUrl(url).build();
}

// Sau (đúng)
public AiServiceClient(
        @Value("${AI_SERVICE_URL:http://localhost:8001}") String url,
        ChatSessionRepository repo,
        RestClient.Builder restClientBuilder) {   // ← inject Spring Boot auto-configured builder
    this.restClient = restClientBuilder.baseUrl(url).build();
}
```

2. **Đổi tất cả `private record` → `record`** (package-private) cho các inner record serialized/deserialized:
   - `private record ChatRequest` → `record ChatRequest`
   - `private record ChatResponseData` → `record ChatResponseData`
   - `private record ChatApiResponse` → `record ChatApiResponse`
   - `private record GenerateTitleRequest` → `record GenerateTitleRequest`
   - `private record GenerateTitleData` → `record GenerateTitleData`
   - `private record GenerateTitleApiResponse` → `record GenerateTitleApiResponse`

**Lưu ý cho tương lai:** Khi khai báo inner record/class dùng để serialize HTTP body với Jackson trong Spring Boot, luôn dùng **package-private** (không có access modifier) hoặc `public`. Tránh dùng `private` cho outer Jackson-serialized types. Luôn inject `RestClient.Builder` bean thay vì gọi `RestClient.builder()` static.

### Response Shape `GetMessagesResponse`

```json
{
  "messages": [
    { "id": "uuid", "sessionId": "uuid", "role": "USER", "content": "...", "createdAt": "..." },
    { "id": "uuid", "sessionId": "uuid", "role": "ASSISTANT", "content": "...", "createdAt": "..." }
  ],
  "suggestedQuestions": ["Câu hỏi 1", "Câu hỏi 2", "Câu hỏi 3"]
}
```

### Response Shape `ChatHistoryGroupResponse`

```json
[
  {
    "contextId": "uuid",
    "contextName": "Trận Bạch Đằng",
    "sessions": [
      {
        "id": "uuid",
        "characterId": "uuid",
        "characterName": "Ngô Quyền",
        "characterTitle": "Vương",
        "characterImage": "/images/ngo-quyen.jpg",
        "contextId": "uuid",
        "contextName": "Trận Bạch Đằng",
        "sessionTitle": "Cuộc trò chuyện về chiến thuật",
        "lastMessage": "Ta đã bày trận cọc...",
        "lastMessageAt": "2026-03-08T10:00:00",
        "messageCount": 12
      }
    ]
  }
]
```
