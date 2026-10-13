# 🔄 SPRINT 0 — BUSINESS FLOWS (AUTHENTICATION & RBAC)
> **Chủ đề:** Quy trình Xác thực Dual-Token, Xoay vòng Refresh Token Rotation (RTR) & Kiểm tra Phân quyền RBAC  
> **Áp dụng cho:** Sprint 0 (13/10/2026 – 19/10/2026)  

---

## 1. LUỒNG ĐĂNG NHẬP & CẤP PHÁT DUAL-TOKEN

```mermaid
sequenceDiagram
    autonumber
    actor User as Người dùng (Client)
    participant Gateway as JwtAuthenticationFilter
    participant AuthCtrl as AuthController
    participant AuthService as AuthServiceImpl
    participant RefreshSvc as RefreshTokenService
    participant DB as PostgreSQL Database

    User->>AuthCtrl: 1. POST /api/v1/auth/login {email, password}
    AuthCtrl->>AuthService: 2. Xác thực credentials
    AuthService->>DB: 3. Kiểm tra user tồn tại & chưa bị khóa (deletedAt == null)
    DB-->>AuthService: Trả về thông tin User
    
    AuthService->>RefreshSvc: 4. Tạo Refresh Token mới (64-byte random, family_id mới)
    RefreshSvc->>DB: 5. Lưu SHA-256 hash của Refresh Token (is_revoked = false)
    DB-->>RefreshSvc: Lưu thành công
    
    AuthService->>AuthService: 6. Sinh Access Token (15p, claims: sub, uid, role, iat, exp)
    AuthService-->>AuthCtrl: 7. Trả về { accessToken, rawRefreshToken, expiresIn: 900000 }
    AuthCtrl-->>User: 8. HTTP 200 OK kèm Cặp Token
```

---

## 2. LUỒNG GỌI API NGHIỆP VỤ & KIỂM TRA PHÂN QUYỀN RBAC

```mermaid
sequenceDiagram
    autonumber
    actor User as Client
    participant Filter as JwtAuthenticationFilter
    participant SecContext as SecurityContextHolder
    participant Endpoint as Protected Controller (@PreAuthorize)

    User->>Filter: 1. Request kèm Header "Authorization: Bearer <accessToken>"
    Note over Filter: Giải mã chữ ký HS512 trong CPU (Stateless)
    
    alt Token không hợp lệ hoặc đã hết hạn (exp < now)
        Filter-->>User: HTTP 401 Unauthorized
    else Token hợp lệ
        Filter->>SecContext: 2. Thiết lập Principal & Authorities ("ROLE_" + role)
        Filter->>Endpoint: 3. Cho phép đi tiếp vào Controller
        
        alt User có Role nằm trong @PreAuthorize("hasAnyRole(...)")
            Endpoint-->>User: 4. HTTP 200 OK (Thực thi nghiệp vụ thành công)
        else User KHÔNG có Role phù hợp
            Endpoint-->>User: 5. HTTP 403 Forbidden (CustomAccessDeniedHandler)
        end
    end
```

---

## 3. LUỒNG XOAY VÒNG TOKEN (RTR) & BÁO ĐỘNG ĐỎ PHÁT HIỆN XÂM PHẠM

```mermaid
flowchart TD
    Start["Client gửi POST /api/v1/auth/refresh-token {refreshToken}"] --> Hash["Server tính SHA-256 hash của raw token"]
    Hash --> Query["Truy vấn bảng refresh_tokens bằng token_hash"]
    
    Query --> CheckExist{"Tìm thấy token trong DB & chưa quá hạn (expires_at > now)?"}
    CheckExist -- "Không tồn tại hoặc đã hết hạn" --> Err401["HTTP 401: Refresh token không hợp lệ hoặc đã hết hạn"]
    
    CheckExist -- "Hợp lệ" --> CheckRevoked{"Trạng thái is_revoked == true?"}
    
    CheckRevoked -- "CẢNH BÁO ĐỎ: is_revoked == true (Token bị dùng lại)" --> BreachAction["PHÁT HIỆN TÁI SỬ DỤNG TOKEN (REPLAY ATTACK)"]
    BreachAction --> RevokeFamily["UPDATE refresh_tokens SET is_revoked = true WHERE family_id = :familyId<br/>(Hủy toàn bộ dòng họ token)"]
    RevokeFamily --> AlertLog["Ghi log cảnh báo an ninh & Trả về HTTP 401"]
    
    CheckRevoked -- "An toàn: is_revoked == false (Lần đầu sử dụng)" --> RotateOld["Đánh dấu token cũ: is_revoked = true"]
    RotateOld --> GenNew["Sinh Refresh Token MỚI (cùng family_id, 7 ngày)<br/>Sinh Access Token MỚI (15 phút)"]
    GenNew --> SaveDB["Lưu token mới vào bảng refresh_tokens"]
    SaveDB --> Success["HTTP 200 OK: Trả về cặp Token mới cho Client"]
```
