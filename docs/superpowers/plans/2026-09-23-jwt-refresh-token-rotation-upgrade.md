# Kế Hoạch Chi Tiết: Nâng Cấp Hệ Thống JWT Sang Dual-Token Architecture Với Refresh Token Rotation (RTR) & Reuse Detection

> **Mục tiêu:** Nâng cấp toàn diện cơ chế xác thực JWT hiện tại lên tiêu chuẩn công nghiệp (Production-ready theo RFC 6749 / RFC 6819 OAuth 2.0 Security Best Current Practice), khắc phục triệt để các lỗ hổng: Logout giả vờ, Refresh Token vô hạn, mất session khi restart, và tài khoản bị khóa/đổi pass vẫn dùng được token cũ.

---

## 1. Hiện trạng & Lỗ hổng cần khắc phục

| STT | Lỗ hổng hiện tại | Rủi ro | Giải pháp nâng cấp |
| :--- | :--- | :--- | :--- |
| **1** | `logout()` chỉ thêm Access Token vào RAM (`BLACKLISTED_TOKENS`), nhưng `JwtAuthenticationFilter` không kiểm tra blacklist. | Logout giả vờ; token vẫn dùng được bình thường. | Thu hồi trực tiếp Refresh Token trong Database và Access Token có thời hạn cực ngắn (15 phút). |
| **2** | `/refresh-token` trả lại chính Refresh Token cũ. | Replay Attack; kẻ gian bắt được refresh token có thể dùng vĩnh viễn. | Triển khai **Refresh Token Rotation (RTR)**: mỗi lần refresh hủy token cũ, cấp token mới. |
| **3** | Không phát hiện kẻ xấu tái sử dụng Refresh Token. | Bị đánh cắp token mà hệ thống không hay biết. | **Automatic Reuse Detection**: Nếu phát hiện token đã hủy bị dùng lại, lập tức thu hồi toàn bộ token cùng `family_id`. |
| **4** | Session lưu trong RAM (`ConcurrentHashMap`). | Mất sạch trạng thái khi server khởi động lại hoặc deploy cluster. | Lưu trữ trạng thái Refresh Token có hash SHA-256 trong PostgreSQL (bảng `refresh_tokens`). |
| **5** | Đổi mật khẩu hoặc Deactivate user nhưng JWT cũ vẫn gọi được API. | Tài khoản bị xâm phạm không thể chấm dứt quyền truy cập ngay. | Bổ sung `password_changed_at` trong bảng `users` và kiểm tra `iat` của token; kiểm tra trạng thái active của user. |

---

## 2. Kiến trúc Luồng Xác Thực Mới (Dual-Token + RTR)

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        LUỒNG HOẠT ĐỘNG TOÀN DIỆN CỦA HỆ THỐNG MỚI                     │
└────────────────────────────────────────────────────────────────────────────────────────┘

 1. ĐĂNG NHẬP (/api/v1/auth/login)
    ├── Xác thực credentials (email + password)
    ├── Sinh Access Token (Hạn: 15 phút, chứa uid, email, role, iat)
    ├── Sinh Refresh Token (Hạn: 7 ngày, chuỗi ngẫu nhiên 64-byte an toàn)
    ├── Lưu SHA-256 hash của Refresh Token vào DB kèm family_id mới, IP, User-Agent
    └── Trả về cặp { accessToken, refreshToken, expiresIn }

 2. GỌI API NGHIỆP VỤ BÌNH THƯỜNG (GET / POST / ...)
    ├── JwtAuthenticationFilter đọc Bearer Access Token
    ├── Verify chữ ký HS512 trong CPU (Stateless - KHÔNG query DB)
    └── Cho phép truy cập tài nguyên (Hiệu năng cực cao)

 3. XOAY VÒNG TOKEN (/api/v1/auth/refresh-token)
    ├── Client gửi raw refreshToken
    ├── Server tính hash SHA-256 và tra cứu trong bảng refresh_tokens
    │
    ├── TRƯỜNG HỢP A: Token hợp lệ & chưa bị thu hồi (is_revoked = false)
    │   ├── Đánh dấu token cũ: is_revoked = true
    │   ├── Sinh Refresh Token MỚI trong cùng family_id
    │   ├── Sinh Access Token MỚI (15 phút)
    │   └── Trả về cặp token mới cho Client
    │
    └── TRƯỜNG HỢP B: BÁO ĐỘNG ĐỎ! Token đã bị thu hồi (is_revoked = true) mà lại bị gửi lên!
        ├── Phát hiện hành vi tấn công (Token Replay / Theft)
        ├── Hủy TOÀN BỘ chuỗi token thuộc family_id đó ngay lập tức (Family Revocation)
        └── Ném lỗi HTTP 401 Unauthorized (Yêu cầu đăng nhập lại toàn bộ)

 4. ĐĂNG XUẤT (/api/v1/auth/logout)
    ├── Nhận refreshToken từ request body
    ├── Đánh dấu token: is_revoked = true trong Database
    └── Trả về HTTP 200 OK

 5. ĐĂNG XUẤT TẤT CẢ THIẾT BỊ (/api/v1/auth/logout-all)
    ├── Đánh dấu toàn bộ refresh token của userId: is_revoked = true
    ├── Cập nhật users.password_changed_at = NOW() (để vô hiệu hóa mọi Access Token còn sót lại)
    └── Buộc mọi thiết bị phải login lại
```

---

## 3. Chi Tiết Các Bước Triển Khai (Implementation Steps)

### Bước 1: Database Migration (Flyway)
Tạo file migration: `Source-code/.../src/main/resources/db/migration/V28__add_refresh_tokens_and_security_enhancement.sql`

```sql
-- 1. Bảng lưu trữ refresh tokens
CREATE TABLE historical_schema.refresh_tokens (
    token_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES historical_schema.users(uid) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 hex string (64 chars)
    family_id      UUID NOT NULL,                    -- Nhóm chuỗi xoay vòng token
    is_revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    device_info    VARCHAR(255),
    ip_address     VARCHAR(45),
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_token_hash ON historical_schema.refresh_tokens(token_hash);
CREATE INDEX idx_refresh_token_user_id ON historical_schema.refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_family_id ON historical_schema.refresh_tokens(family_id);

-- 2. Thêm cột password_changed_at vào bảng users để quản lý thu hồi access token
ALTER TABLE historical_schema.users
ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
```

---

### Bước 2: Tạo Entity & Repository
- **Entity**: `com.historytalk.entity.authentication.RefreshToken`
  - Các trường: `tokenId`, `user`, `tokenHash`, `familyId`, `isRevoked`, `deviceInfo`, `ipAddress`, `expiresAt`, `createdAt`.
- **Repository**: `com.historytalk.repository.authentication.RefreshTokenRepository`
  - `Optional<RefreshToken> findByTokenHash(String tokenHash);`
  - `List<RefreshToken> findByFamilyId(UUID familyId);`
  - `@Modifying @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.familyId = :familyId") void revokeFamily(UUID familyId);`
  - `@Modifying @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.user.uid = :userId") void revokeAllUserTokens(UUID userId);`
  - `@Modifying @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now") void deleteExpiredTokens(Instant now);`

---

### Bước 3: Xây dựng `RefreshTokenService` & Logic Xoay Vòng (Rotation)
- Tạo Interface `RefreshTokenService` và `RefreshTokenServiceImpl` trong `com.historytalk.service.authentication`:
  - **Tạo token an toàn**: Dùng `SecureRandom` sinh 64 bytes ngẫu nhiên, encode Base64Url (không dùng JWT cho refresh token để giảm dung lượng và tránh lộ payload).
  - **Băm SHA-256**: Băm chuỗi raw token trước khi so khớp hoặc lưu DB. Dù DB có bị leak dữ liệu, kẻ tấn công cũng không thể giả mạo raw token.
  - **Cơ chế Rotation**: Khi nhận request refresh:
    1. Kiểm tra tồn tại và hạn dùng (`expiresAt > now`).
    2. Nếu `isRevoked == true` -> Kích hoạt `revokeFamily(token.getFamilyId())`, ghi log cảnh báo xâm nhập bảo mật (`SECURITY_ALERT: Refresh token reuse detected`), throw `UnauthorizedException`.
    3. Nếu hợp lệ -> Đặt `isRevoked = true` cho token hiện tại, tạo bản ghi mới với cùng `familyId` và thời hạn 7 ngày mới.

---

### Bước 4: Tinh Chỉnh Cấu Hình Thời Hạn Token (`application.properties`)
- Giảm thời hạn Access Token xuống chuẩn an toàn:
  - `JWT_EXPIRATION_MS=900000` (15 phút - thay vì 24 tiếng như hiện tại).
  - `JWT_REFRESH_EXPIRATION_MS=604800000` (7 ngày).

---

### Bước 5: Cập Nhật `AuthService` & `AuthController`
1. **`login()`**:
   - Gọi `refreshTokenService.createToken(user, deviceInfo, ipAddress)`.
   - Trả về `accessToken` và `rawRefreshToken`.
2. **`refreshToken()`**:
   - Nhận `rawRefreshToken` từ body request.
   - Gọi `refreshTokenService.rotateToken(...)`.
   - Sinh `accessToken` mới.
   - Trả về cả `accessToken` mới và `refreshToken` mới.
3. **`logout()`**:
   - Nhận `refreshToken` từ request.
   - Gọi `refreshTokenService.revokeToken(...)`.
4. **`logoutAll()`** (Thêm endpoint mới: `POST /api/v1/auth/logout-all`):
   - Thu hồi mọi refresh token của user hiện tại.
   - Cập nhật `passwordChangedAt = LocalDateTime.now()` của user.
5. **`resetPassword()` & `changePassword()`**:
   - Cập nhật mật khẩu mới + set `passwordChangedAt = LocalDateTime.now()`.
   - Thu hồi toàn bộ refresh token cũ của user.

---

### Bước 6: Kiểm Thử & Xác Nhận (Validation Plan)
1. **Kiểm tra biên dịch**: `mvn -q -DskipTests compile`.
2. **Unit / Integration Tests**:
   - Test login sinh cặp token hợp lệ.
   - Test refresh token thành công: token cũ bị revoke, token mới hoạt động.
   - Test **Reuse Detection**: Dùng lại token cũ -> toàn bộ phiên bị hủy ngay lập tức, trả HTTP 401.
   - Test logout: refresh token bị revoke, không thể dùng để refresh nữa.
   - Test đổi mật khẩu: các token cũ bị từ chối hoàn toàn.
