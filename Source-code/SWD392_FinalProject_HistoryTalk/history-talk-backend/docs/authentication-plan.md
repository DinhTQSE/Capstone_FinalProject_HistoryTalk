# Authentication Redesign Plan – History Talk Backend

**Date:** March 6, 2026  
**Status:** ✅ Completed – `mvn clean compile` BUILD SUCCESS  
**Author:** Team Backend

---

## 1. Vấn Đề Hiện Tại (Current Problems)

### 1.1 Package Declarations Sai (Critical – Ngăn Compile)
Toàn bộ auth module và một số file khác bị prefix sai `java.com.historyTalk.*` thay vì `com.historyTalk.*`.
Java sẽ compile nhưng Spring Boot component scan (`@SpringBootApplication` trên `com.historyTalk`) sẽ KHÔNG nhìn thấy các class này.
Kết quả: auth endpoints, historical context controllers đều bị inactive.

**File bị ảnh hưởng (25+ files):**
- `HistoryTalkApplication.java` → `package java.com.historyTalk;`
- `controller/authentication/AuthController.java`
- `controller/historicalContext/HistoricalContextController.java`
- `controller/historicalContext/HistoricalContextDocumentController.java`
- `config/CustomAccessDeniedHandler.java`, `RestAuthenticationEntryPoint.java`, `SpringSecurityConfig.java`, `SwaggerConfig.java`
- `service/authentication/*` (all 5 files)
- `dto/authentication/*` (all 7 files)
- `dto/user/*`, `mapper/user/*`, `utils/authentication/*`
- `jwtFilter/JwtAuthenticationFilter.java`

### 1.2 Import Sai Từ Project Khác (schoolhealth)
Auth code được copy từ `com.schoolhealth.schoolhealthmanagementsystem` với các references không tồn tại:
- `UserRepository`, `RoleRepository` → from schoolhealth project
- `JwtProperties` → from schoolhealth utils
- `EmailEventActionAuthenticationFilter` → doesn't exist here
- `UserProfile`, `RoleName`, `AccountStatus`, `UserRole` → schoolhealth entities

### 1.3 Entity Không Khớp Với Diagram
- `UserType` enum có thêm `GUEST` không có trong class diagram
- Auth flow không phù hợp với quan hệ `User → Staff → Role`

### 1.4 Database Config Sai
- `secretKey.properties` trỏ tới `health_school_system` thay vì `history_talk_db`
- JWT property names (`JWT_SECRET`) không khớp với `JwtTokenProvider` (`jwt.secret`)

### 1.5 Duplicate Security Config
Có hai security configs xung đột:
- `SecurityConfig` (đúng, active)  
- `SpringSecurityConfig` (copy từ schoolhealth, không compile được do sai import)

---

## 2. Entity Diagram Requirements

```
User:
  - uid: String «PK»
  - userName: String «UNIQUE»
  - email: String «UNIQUE»
  - password: String
  - userType: UserType
  - staffId: String «FK Staff» (optional – khi user IS a staff account)

UserType enum: REGISTERED | STAFF

Staff:
  - staffId: String «PK»
  - name: String
  - email: String «UNIQUE»
  - roleId: String «FK Role»

Role:
  - roleId: String «PK»
  - roleName: String (e.g., "ADMIN", "STAFF")
  - description: String
```

---

## 3. Target Architecture

### 3.1 Authentication Flow

```
POST /api/v1/auth/register   → Tạo User với userType=REGISTERED
POST /api/v1/auth/login      → Validate, trả JWT (accessToken + refreshToken)
POST /api/v1/auth/refresh-token → Đổi refreshToken → accessToken mới
POST /api/v1/auth/logout     → Blacklist token
```

### 3.2 JWT Token Design

**Access Token Payload:**
```json
{
  "sub": "user@email.com",
  "uid": "user-uuid-123",
  "userType": "STAFF",
  "roleName": "ADMIN",
  "iat": 1709000000,
  "exp": 1709900000
}
```

**Authorities được build từ JWT claims:**
- `userType = REGISTERED` → `[ROLE_REGISTERED]`
- `userType = STAFF` + `roleName = ADMIN` → `[ROLE_STAFF, ROLE_ADMIN]`

### 3.3 Security Matrix

| Endpoint | Access |
|---|---|
| `GET /v1/historical-contexts/**` | Public |
| `POST/PUT/DELETE /v1/historical-contexts/**` | `ROLE_STAFF` hoặc `ROLE_ADMIN` |
| `GET /v1/historical-documents/**` | Public |
| `POST/PUT/DELETE /v1/historical-documents/**` | `ROLE_STAFF` hoặc `ROLE_ADMIN` |
| `POST /api/v1/auth/**` | Public |

---

## 4. Solution – Danh Sách Thay Đổi

### Phase 1: Foundation (Critical)
| File | Action | Mô tả |
|------|--------|-------|
| `HistoryTalkApplication.java` | FIX | `java.com.historyTalk` → `com.historyTalk` |
| `entity/UserType.java` | UPDATE | Xóa `GUEST`, giữ `REGISTERED` và `STAFF` |
| `repository/UserRepository.java` | CREATE | JpaRepository cho User entity |
| `security/UserPrincipal.java` | CREATE | UserDetails adapter cho User entity |

### Phase 2: JWT Infrastructure
| File | Action | Mô tả |
|------|--------|-------|
| `security/JwtTokenProvider.java` | UPDATE | Fix @Value annotations + add `generateAccessToken(email, claims)`, `generateRefreshToken(email)`, `getAllClaims(token)` |
| `security/JwtAuthenticationFilter.java` | UPDATE | Đọc `userType`/`roleName` claims từ JWT → build authorities |
| `config/SecurityConfig.java` | UPDATE | Thêm `PasswordEncoder`, `AuthenticationManager`, `AuthenticationProvider` beans |

### Phase 3: Auth DTOs (fix packages + nội dung)
| File | Action | Mô tả |
|------|--------|-------|
| `dto/authentication/LoginRequest.java` | REWRITE | email + password |
| `dto/authentication/LoginResponse.java` | REWRITE | accessToken, refreshToken, uid, userName, userType |
| `dto/authentication/RegisterRequest.java` | REWRITE | userName, email, password, confirmPassword |
| `dto/authentication/RegisterResponse.java` | REWRITE | message, uid |
| `dto/authentication/RefreshTokenRequest.java` | REWRITE | refreshToken |
| `dto/authentication/RefreshTokenResponse.java` | REWRITE | accessToken, refreshToken |
| `dto/authentication/LogoutResponse.java` | REWRITE | message |

### Phase 4: Auth Service Layer
| File | Action | Mô tả |
|------|--------|-------|
| `service/authentication/JwtService.java` | REWRITE | Simplified interface (generateAccessToken, generateRefreshToken, extractEmail, validate) |
| `service/authentication/JwtServiceImpl.java` | REWRITE | Delegates to JwtTokenProvider |
| `service/authentication/CustomUserDetailsService.java` | REWRITE | Use UserRepository (historyTalk's own) |
| `service/authentication/AuthService.java` | REWRITE | Fix interface (đúng DTOs) |
| `service/authentication/AuthServiceImpl.java` | REWRITE | Complete rewrite với UserRepository, BCrypt, HistoryTalk entities |

### Phase 5: Controller
| File | Action | Mô tả |
|------|--------|-------|
| `controller/authentication/AuthController.java` | REWRITE | Fix packages + DTOs |
| `controller/historicalContext/HistoricalContextController.java` | FIX | Fix package declaration + import |
| `controller/historicalContext/HistoricalContextDocumentController.java` | FIX | Fix package declaration + import |

### Phase 6: Service Package Fix (Historical Context)
| File | Action | Mô tả |
|------|--------|-------|
| `service/historicalContext/HistoricalContextService.java` | FIX | `java.com.historyTalk...` → `com.historyTalk...` |
| `service/historicalContext/HistoricalContextDocumentService.java` | FIX | Same |

### Phase 7: Config & Utilities Cleanup
| File | Action | Mô tả |
|------|--------|-------|
| `config/CustomAccessDeniedHandler.java` | FIX | Fix package only |
| `config/RestAuthenticationEntryPoint.java` | FIX | Fix package only |
| `config/SpringSecurityConfig.java` | NEUTRALIZE | Xóa @Configuration annotation để không conflict |
| `config/SwaggerConfig.java` | FIX | Fix package only |
| `utils/authentication/JwtProperties.java` | FIX | Fix package, update property names |
| `utils/authentication/JwtUtils.java` | FIX | Fix package only |

### Phase 8: Stub Files Cleanup (prevent compile errors)
| File | Action | Mô tả |
|------|--------|-------|
| `dto/user/UserInformationRequest.java` | FIX | Fix package, remove schoolhealth imports |
| `mapper/user/UserInformationMapper.java` | STUB | Fix package, empty interface |
| `mapper/user/UserInformationMapperImpl.java` | STUB | Fix package, empty impl |
| `jwtFilter/JwtAuthenticationFilter.java` | STUB | Fix package, remove Spring annotations (not used) |

### Phase 9: Config Fix
| File | Action | Mô tả |
|------|--------|-------|
| `resources/secretKey.properties` | UPDATE | Fix DB config: `health_school_system` → `history_talk_db` |

---

## 5. New Files Detail

### `UserRepository.java`
```java
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByUserNameIgnoreCase(String userName);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUserNameIgnoreCase(String userName);
}
```

### `UserPrincipal.java`
- Implements `UserDetails`
- Wraps `User` entity
- `getUsername()` → returns `email`
- `getAuthorities()` → `ROLE_REGISTERED` or `ROLE_STAFF` + `ROLE_<roleName>`

---

## 6. Security Notes

> ⚠️ **secretKey.properties chứa real credentials – KHÔNG commit lên Git.**  
> File này đã được gitignore. Nếu đã push rồi, cần revoke và thay mới ngay.

> ⚠️ **JWT Blacklist dùng in-memory Set** – sẽ mất khi restart server.  
> Production: dùng Redis hoặc DB table để store blacklisted tokens.

> ⚠️ **CORS hiện tại là wildcard `*`** với `allowCredentials=false`.  
> Production: giới hạn origins theo frontend URL.

---

## 7. API Contract Sau Khi Triển Khai

### Register
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "userName": "nguyen_van_a",
  "email": "user@example.com",
  "password": "P@ssw0rd123!",
  "confirmPassword": "P@ssw0rd123!"
}
```
**Response 201:**
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "uid": "uuid-xxx",
    "userName": "nguyen_van_a",
    "email": "user@example.com",
    "userType": "REGISTERED"
  }
}
```

### Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "P@ssw0rd123!"
}
```
**Response 200:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900000,
    "uid": "uuid-xxx",
    "userName": "nguyen_van_a",
    "userType": "REGISTERED"
  }
}
```

### Refresh Token
```http
POST /api/v1/auth/refresh-token
Content-Type: application/json

{ "refreshToken": "eyJ..." }
```

### Logout
```http
POST /api/v1/auth/logout
Authorization: Bearer eyJ...
```
