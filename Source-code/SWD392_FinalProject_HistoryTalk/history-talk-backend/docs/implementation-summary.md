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
