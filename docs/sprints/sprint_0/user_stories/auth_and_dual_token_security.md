# 📋 USER STORIES: XÁC THỰC & BẢO MẬT DUAL-TOKEN (AUTHENTICATION & DUAL-TOKEN SECURITY)
> **Sprint:** Sprint 0 (13/10/2026 – 19/10/2026)  
> **Phân hệ:** Xác thực & Nền tảng Bảo mật (Authentication & Security Foundation)  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP0-01`** | Chuẩn hóa Payload Access Token & Khắc phục mất Subject claim | Tất cả người dùng | 🟢 Khải (KhaiVDD) | `POST /api/v1/auth/login` |
| **`US-SP0-02`** | Cấp phát Dual-Token & Cơ chế Xoay vòng Refresh Token (RTR) | Tất cả người dùng | 🟢 Khải (KhaiVDD) | `POST /api/v1/auth/refresh-token` |
| **`US-SP0-03`** | Thu hồi Token Database khi Đăng xuất & Vô hiệu hóa phiên tức thì | Tất cả người dùng | 🟢 Khải (KhaiVDD) | `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all` |

---

## 📝 CHI TIẾT USER STORIES

### 🆔 US-SP0-01: Chuẩn hóa Payload Access Token & Khắc phục mất Subject claim (Access Token Payload Standardization)
* **Actor:** `Tất cả người dùng (All Actors)`
* **Thời gian thực hiện:** 13/10/2026 – 15/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là người dùng hệ thống, tôi muốn Access Token của tôi chứa đầy đủ các thông tin định danh hợp lệ (`sub`, `uid`, `role`, `iat`, `exp`), để các API trong hệ thống xác thực danh tính của tôi chính xác mà không bị mất thông tin email hoặc lỗi quyền hạn.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/auth/login` tiếp nhận thông tin đăng nhập (`email`, `password`).
  2. Sửa lỗi `claims(Map)` trong thư viện JJWT 0.12.5 của `JwtTokenProvider.java`:
     - Phải đảm bảo claim `sub` (subject = email) không bị ghi đè hay biến mất khỏi JWT.
     - Payload của Access Token phải chứa tối thiểu các claims: `sub` (email), `uid` (UUID user), `role` (Role name), `iat` (thời điểm cấp), `exp` (thời điểm hết hạn).
  3. Thời hạn của Access Token được rút ngắn xuống **15 phút** (`900000 ms`) theo tiêu chuẩn an toàn RFC 6749.
  4. Bộ lọc `JwtAuthenticationFilter` giải mã Access Token trích xuất chính xác `email` từ subject, `uid` và `role`, thiết lập `AuthenticatedPrincipal` hợp lệ trong `SecurityContextHolder`.

---

### 🆔 US-SP0-02: Cấp phát Dual-Token & Cơ chế Xoay vòng Refresh Token (Refresh Token Rotation & Reuse Detection)
* **Actor:** `Tất cả người dùng (All Actors)`
* **Thời gian thực hiện:** 14/10/2026 – 17/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là người dùng hệ thống, tôi muốn phiên đăng nhập của tôi được tự động duy trì an toàn bằng cặp Access Token và Refresh Token xoay vòng, để tôi không bị gián đoạn khi làm việc và tài khoản được bảo vệ nếu token bị kẻ gian đánh cắp.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/auth/login`:
     - Trả về cặp `{ accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900000 }`.
     - Refresh Token là chuỗi ngẫu nhiên 64-byte an toàn (`SecureRandom`), thời hạn 7 ngày.
     - Server băm SHA-256 chuỗi Refresh Token và lưu vào bảng `historical_schema.refresh_tokens` kèm theo `family_id` mới đại diện cho phiên đó.
  2. API `POST /api/v1/auth/refresh-token`:
     - Tiếp nhận `refreshToken` từ request body.
     - Băm SHA-256 tra cứu trong bảng `refresh_tokens`.
     - **Trường hợp hợp lệ (`is_revoked == false` và còn hạn):**
       - Đánh dấu token cũ `is_revoked = true`.
       - Sinh Refresh Token MỚI với cùng `family_id`, lưu vào DB (`is_revoked = false`).
       - Sinh Access Token MỚI (15 phút).
       - Trả về cặp token mới cho Client.
     - **Trường hợp Tái sử dụng (Reuse Detection - `is_revoked == true`):**
       - Kích hoạt **Báo động đỏ**: Hủy toàn bộ chuỗi token thuộc `family_id` đó (`UPDATE refresh_tokens SET is_revoked = true WHERE family_id = :familyId`).
       - Ném ngoại lệ HTTP `401 Unauthorized` buộc đăng nhập lại từ đầu.

---

### 🆔 US-SP0-03: Thu hồi Token Database khi Đăng xuất & Vô hiệu hóa phiên tức thì (Database Revocation & Instant Session Invalidation)
* **Actor:** `Tất cả người dùng (All Actors)`
* **Thời gian thực hiện:** 16/10/2026 – 18/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là người dùng hoặc Quản trị viên, tôi muốn khi người dùng bấm Đăng xuất hoặc Đổi mật khẩu/Khóa tài khoản thì các phiên truy cập cũ bị chấm dứt ngay lập tức trong Database, để kẻ gian không thể tiếp tục dùng token cũ để gọi API.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/auth/logout`:
     - Tiếp nhận `refreshToken` từ body hoặc header.
     - Cập nhật trực tiếp `is_revoked = true` trong bảng `historical_schema.refresh_tokens`.
     - Xóa bỏ hoàn toàn cơ chế in-memory `BLACKLISTED_TOKENS` cũ.
  2. API `POST /api/v1/auth/logout-all`:
     - Thu hồi toàn bộ refresh token của người dùng hiện tại (`WHERE user_id = :userId`).
     - Cập nhật cột `users.password_changed_at = CURRENT_TIMESTAMP`.
  3. Xử lý khi Đổi mật khẩu (`changePassword`) / Quên mật khẩu (`resetPassword`) / Khóa tài khoản (`softDeleteUser`):
     - Thu hồi toàn bộ Refresh Token của user trong database.
     - Cập nhật `password_changed_at = CURRENT_TIMESTAMP`.
     - `JwtAuthenticationFilter` kiểm tra: nếu token có `iat < password_changed_at` $\rightarrow$ Từ chối quyền truy cập ngay lập tức.
