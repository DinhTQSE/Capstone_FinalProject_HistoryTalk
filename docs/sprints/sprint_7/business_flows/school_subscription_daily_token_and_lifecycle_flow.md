# 🔄 BUSINESS FLOW: SCHOOL SUBSCRIPTION, DAILY TOKEN POOL & ACADEMIC YEAR LIFECYCLE
> **Sprint:** Sprint 7 (18/10/2026 – 30/10/2026)  
> **Phân hệ:** Quản trị Gói B2B Trường học (01/09 – 31/05), Quỹ Token Chung theo Ngày (Daily Allowance) & Vòng đời Học sinh  
> **Stories Covered:** `US-SP7-14`, `US-SP7-15`, `US-SP7-16`, `US-SP7-17`, `US-SP7-18`  
> **Tác giả:** 🔵 Trần Quốc Dinh (`DinhTQ`)  

---

## 1. TỔNG QUAN KIẾN TRÚC NGHIỆP VỤ

Hệ thống chuyển đổi từ mô hình *"Cấp phát tĩnh 1 lần vô thời hạn"* sang mô hình **"Hợp đồng Năm học 9 tháng (01/09 – 31/05) + Quỹ Token Chung (Shared Pool) + Hạn mức Ngày (Daily Allowance) + Quản lý Vòng đời Học sinh (Academic Rollover & Account Deactivation)"**.

### 5 Nguyên tắc Vàng (Core Business Invariants)
1. **Chu kỳ gói trường học (`01/09` – `31/05`):**
   * Gói B2B trường học chỉ hoạt động từ **đầu tháng 9 (`01/09`)** đến **cuối tháng 5 (`31/05`)**.
   * Trường phải **gia hạn trong tháng 5 (`01/05` – `31/05`)**, và gói mới chỉ bắt đầu có hiệu lực sử dụng từ **`01/09`** đầu năm học tiếp theo.
2. **Phân tách cơ chế Khóa (Two-Level Locking):**
   * **Hết hạn gói (`EXPIRED` / Chưa gia hạn):** **Khóa toàn bộ nền tảng của trường** (chặn mọi tính năng của Giáo viên, Học sinh, School Admin, chỉ mở trang thông tin gia hạn).
   * **Còn hạn gói nhưng Hết Token (`Out of Tokens`):** **Chỉ khóa tính năng AI** (Chat nhân vật lịch sử), toàn bộ các tính năng khác (làm Quiz, đọc tài liệu, giao/nộp bài tập, xem Dashboard) vẫn hoạt động bình thường.
3. **100% Quỹ chung + Hạn mức Chi tiêu theo Ngày (Pure Shared Pool + Daily Spending Limit):**
   * Toàn bộ token của trường nằm cố định tại Quỹ chung (`school.unallocatedTokenQuota`). **Không chia trước token vào tài khoản học sinh đầu ngày và không cần thu hồi cuối ngày.**
   * Mỗi học sinh có bộ đếm lượng đã dùng trong ngày (`dailyTokensUsed`) và hạn mức được tiêu tối đa trong ngày (`dailyTokenLimit`, mặc định `1.000 token/ngày`). Học sinh chat câu nào thì hệ thống trừ trực tiếp vào `school.unallocatedTokenQuota` và tăng `dailyTokensUsed` câu đó $\rightarrow$ **Triệt tiêu 100% "token chết" (stranded tokens)** và **chống xài lố cả năm**.
4. **Luồng Xin & Duyệt thêm Hạn mức trong ngày (Daily Boost):**
   * Học sinh chạm trần hạn mức ngày có thể bấm **"Xin thêm token"** $\rightarrow$ Giáo viên dạy lớp đó (hoặc School Admin) duyệt tăng thêm hạn mức trong ngày (`dailyBonusTokens`), học sinh dùng tới đâu mới trừ từ Quỹ trường tới đó.
5. **Vòng đời qua các năm học (Academic Year Lifecycle):**
   * **Lên lớp / Đổi lớp:** Kết thúc năm học, lớp cũ chuyển sang `ARCHIVED`. Đầu năm học mới, học sinh được xếp vào lớp mới (`academicYear` mới) trên cùng tài khoản cũ.
   * **Ra trường / Chuyển trường:** Tài khoản học sinh bị chuyển sang **`DEACTIVATED` (Vô hiệu hóa — khóa đăng nhập hoàn toàn)**.

---

## 2. SƠ ĐỒ VÒNG ĐỜI GÓI TRƯỜNG HỌC & CƠ CHẾ KHÓA (US-SP7-14)

### 2.1. State Diagram: Trạng thái Gói Trường học theo Năm học

```mermaid
stateDiagram-v2
    [*] --> PENDING_ACTIVATION: Đăng ký mới / Gia hạn trong Tháng 5
    PENDING_ACTIVATION --> ACTIVE: Đến ngày 01/09 (Đầu năm học)
    ACTIVE --> RENEWED_IN_MAY: Trường gia hạn trong Tháng 5 (01/05 - 31/05)
    RENEWED_IN_MAY --> SUMMER_BREAK_LOCKED: Hết ngày 31/05 (Đóng năm học cũ)
    SUMMER_BREAK_LOCKED --> ACTIVE: Đến ngày 01/09 (Kích hoạt gói mới + Bảo lưu tối đa 20% token dư)
    ACTIVE --> EXPIRED: Qua ngày 31/05 mà CHƯA gia hạn
    EXPIRED --> PENDING_ACTIVATION: Gia hạn bổ sung (Chờ 01/09 hoặc Admin kích hoạt)
```

### 2.2. Flowchart: Kiểm tra Quyền Truy cập Nền tảng vs Quyền Gọi AI

```mermaid
flowchart TD
    Req["Request từ User thuộc Trường học (School Admin / Teacher / Student)"] --> CheckAccount{"User.accountStatus == DEACTIVATED?"}
    CheckAccount -- "Có (Đã ra trường/chuyển trường)" --> BlockLogin["403 ACCOUNT_DEACTIVATED: Khóa đăng nhập hoàn toàn"]
    CheckAccount -- "Không (ACTIVE)" --> CheckSchoolSub{"Gói Trường đang ACTIVE và trong khoảng 01/09 - 31/05?"}

    CheckSchoolSub -- "Không (Hết hạn / Chưa gia hạn)" --> BlockAll["403 SCHOOL_SUBSCRIPTION_EXPIRED: Khóa TOÀN BỘ nền tảng của trường"]
    CheckSchoolSub -- "Có (Còn hạn)" --> CheckFeatureType{"Loại tính năng đang gọi?"}

    CheckFeatureType -- "Non-AI (Làm Quiz, Đọc bài, Giao/Nộp Assignment, Dashboard)" --> AllowNonAI["200 OK: Cho phép sử dụng bình thường 100%"]
    CheckFeatureType -- "Tính năng AI (Chat Nhân vật Lịch sử)" --> CheckDailyToken{"Kiểm tra: dailyTokensUsed < effectiveDailyLimit VÀ schoolPool > 0?"}

    CheckDailyToken -- "Thỏa mãn" --> AllowAI["200 OK: Cho phép gọi AI & Trừ trực tiếp từ Quỹ chung của trường"]
    CheckDailyToken -- "Đã chạm hạn mức ngày hoặc Quỹ trường = 0" --> BlockAIOnly["403 AI_QUOTA_EXCEEDED: Chỉ khóa tính năng AI (Gợi ý Xin thêm token hoặc Top-up)"]
```

---

## 3. LUỒNG 100% QUỸ CHUNG & HẠN MỨC CHI TIÊU THEO NGÀY (US-SP7-15 & US-SP7-16)

### 3.1. Cơ chế Direct Pool Deduction & Bộ đếm Hạn mức Ngày (Không chia trước, Không cần thu hồi)
Thay vì chia token vào tài khoản học sinh đầu ngày rồi cuối ngày phải thu hồi, hệ thống áp dụng mô hình **100% Quỹ chung (Pure Shared Pool) kết hợp Bộ đếm Hạn mức Ngày (`dailyTokensUsed`)**:

1. **Khi học sinh gửi tin nhắn AI đầu tiên trong ngày mới $D$ (`lastTokenResetAt < D.atStartOfDay()`):**
   * Hệ thống chỉ đặt lại bộ đếm trong ngày của học sinh: `user.dailyTokensUsed = 0`, `user.dailyBonusTokens = 0`, `user.lastTokenResetAt = LocalDateTime.now()`.
   * **Không rút trước bất kỳ token nào từ `school.unallocatedTokenQuota`**.
2. **Kiểm tra điều kiện trước khi gọi AI:**
   * Xác định hạn mức tối đa học sinh được phép tiêu hôm nay:
     $$\text{effectiveDailyLimit} = \text{COALESCE}(\text{classroom.dailyTokenQuota}, \text{school.defaultDailyStudentQuota}, 1000) + \text{user.dailyBonusTokens}$$
   * Số lượng còn được phép tiêu hôm nay:
     $$\text{availableToday} = \min(\max(0, \text{effectiveDailyLimit} - \text{user.dailyTokensUsed}), \text{school.unallocatedTokenQuota})$$
   * Nếu $\text{availableToday} \le 0$: Chặn cuộc gọi AI với mã `403 AI_QUOTA_EXCEEDED` (các tính năng Quiz/Tài liệu/Bài tập vẫn mở bình thường).
3. **Sau khi AI phản hồi (`tokensUsed`):**
   * Trừ trực tiếp `tokensUsed` vào Quỹ chung của trường: `school.unallocatedTokenQuota -= tokensUsed`.
   * Tăng bộ đếm đã dùng trong ngày của học sinh: `user.dailyTokensUsed += tokensUsed`.

### 3.2. Sequence Diagram: Học sinh Chat AI Trừ Trực tiếp vào Quỹ Chung

```mermaid
sequenceDiagram
    actor Student as Học sinh (ROLE_STUDENT)
    participant ChatCtrl as MessageServiceImpl
    participant Guard as DailyTokenAllowanceService
    participant DB as PostgreSQL (School & User)
    participant AI as HistoryTalk AI Service

    Student->>ChatCtrl: POST /api/v1/chat/messages
    ChatCtrl->>Guard: verifyAndPrepareDailyAllowance(student)
    alt Gói trường hết hạn (ngoài 01/09 - 31/05 hoặc EXPIRED)
        Guard-->>ChatCtrl: Throw 403 SCHOOL_SUBSCRIPTION_EXPIRED
        ChatCtrl-->>Student: 403 Khóa toàn bộ nền tảng trường
    else Sang ngày mới (lastTokenResetAt < Today 00:00)
        Guard->>DB: Reset dailyTokensUsed = 0, dailyBonusTokens = 0, lastTokenResetAt = NOW()
    end

    alt dailyTokensUsed >= effectiveDailyLimit HOẶC school.unallocatedTokenQuota <= 0
        Guard-->>ChatCtrl: Throw 403 AI_QUOTA_EXCEEDED
        ChatCtrl-->>Student: 403 Chỉ khóa tính năng AI (Các tính năng Quiz/Bài tập vẫn mở)
    else Còn hạn mức ngày & Quỹ trường > 0
        Guard-->>ChatCtrl: OK (effectiveAvailableToday)
        ChatCtrl->>AI: Gửi prompt tới AI Backend
        AI-->>ChatCtrl: Trả lời + totalToken
        ChatCtrl->>Guard: recordStudentTokenUsage(student, totalToken)
        Guard->>DB: Trừ trực tiếp school.unallocatedTokenQuota -= totalToken & Tăng student.dailyTokensUsed += totalToken
        ChatCtrl-->>Student: 200 OK (Nội dung trả lời AI + remainingTokens hôm nay)
    end
```

---

## 4. LUỒNG XIN & DUYỆT THÊM HẠN MỨC TOKEN TRONG NGÀY (US-SP7-17)

```mermaid
sequenceDiagram
    actor Student as Học sinh
    actor Teacher as Giáo viên phụ trách lớp / School Admin
    participant API as SchoolLifecycleAndTokenController
    participant Svc as TokenBoostRequestService
    participant DB as PostgreSQL

    Student->>API: POST /api/v1/student/token-requests { requestedTokens: 1000, reason: "Ôn thi HK1" }
    API->>Svc: createBoostRequest(studentPrincipal, request)
    Svc->>DB: Kiểm tra học sinh thuộc lớp ACTIVE & chưa có đơn PENDING hôm nay
    Svc->>DB: Lưu bản ghi token_boost_request (status = PENDING)
    Svc-->>Student: 201 Created (Đang chờ Giáo viên duyệt)

    Teacher->>API: GET /api/v1/teacher/token-requests?status=PENDING
    API-->>Teacher: Danh sách yêu cầu của học sinh trong lớp mình dạy

    Teacher->>API: PUT /api/v1/teacher/token-requests/{id}/review { action: "APPROVE", approvedTokens: 1000 }
    API->>Svc: reviewBoostRequest(teacherPrincipal, id, reviewDto)
    Svc->>DB: Kiểm tra quyền sở hữu lớp (classroom.teacher_id == teacher.uid)
    Svc->>DB: Cộng student.dailyBonusTokens += 1000 (Token thực tế vẫn nằm ở Quỹ trường cho tới khi HS chat)
    Svc->>DB: Cập nhật status = APPROVED, reviewedBy = teacher.uid
    Svc-->>Teacher: 200 OK (Đã mở thêm 1.000 hạn mức token trong ngày cho học sinh)
```

---

## 5. LUỒNG VÒNG ĐỜI QUA CÁC NĂM HỌC: LÊN LỚP, ĐỔI LỚP, RA TRƯỜNG / CHUYỂN TRƯỜNG (US-SP7-18)

### 5.1. Flowchart: Xử lý Cuối Năm học (31/05) & Đầu Năm học mới (01/09)

```mermaid
flowchart TD
    EndYear["Kết thúc năm học (31/05) hoặc School Admin bấm Chuyển giao năm học"] --> ArchiveClasses["Chuyển tất cả Classroom của năm học cũ: status = ACTIVE -> ARCHIVED"]
    ArchiveClasses --> CheckStudentType{"Phân loại Học sinh"}

    CheckStudentType -- "Học sinh Khối 10, 11 (Lên lớp / Đổi lớp)" --> KeepActive["Giữ nguyên User (ACTIVE), bảo toàn lịch sử Quiz/Assignment cũ"]
    KeepActive --> NewYear["Đầu năm học mới (01/09): Import / Gán vào Classroom mới (academicYear mới, status = ACTIVE)"]

    CheckStudentType -- "Học sinh Khối 12 Tốt nghiệp / HS Chuyển trường" --> DeactivateUser["Chuyển User: accountStatus = DEACTIVATED, deletedAt = NOW()"]
    DeactivateUser --> BlockAuth["Khóa đăng nhập vĩnh viễn (Login & JWT Filter trả về 403 ACCOUNT_DEACTIVATED)"]
```

### 5.2. Quy tắc Ràng buộc Lớp học theo Năm học (`ClassStudent` Constraints)
1. **Một học sinh có nhiều lớp qua các năm, nhưng chỉ 1 lớp `ACTIVE` tại một thời điểm:**
   * Năm học `2025`: Học sinh `HS001` thuộc lớp `10A1` (`academicYear = 2025`, `status = ARCHIVED`).
   * Năm học `2026`: Học sinh `HS001` thuộc lớp `11A2` (`academicYear = 2026`, `status = ACTIVE`).
   * Khi kiểm tra điều kiện *"Học sinh đã có lớp chưa"* lúc gán lớp hoặc import: Hệ thống chỉ kiểm tra các lớp có `classroom.status == 'ACTIVE'`, cho phép học sinh đã có lớp `ARCHIVED` của năm trước được vào lớp `ACTIVE` của năm mới bình thường.
2. **Khi Học sinh Ra trường hoặc Chuyển trường (`DEACTIVATED`):**
   * Toàn bộ token chưa dùng vốn đã nằm sẵn trong `school.unallocatedTokenQuota` nên không bao giờ bị thất thoát.
   * Đánh dấu `user.accountStatus = DEACTIVATED` (và gán `deletedAt = LocalDateTime.now()`).
   * Tại `AuthServiceImpl.login()` và `JwtAuthenticationFilter`: Nếu phát hiện `user.accountStatus == DEACTIVATED` hoặc `user.deletedAt != null`, từ chối ngay lập tức với lỗi `403 Forbidden ("Tài khoản học sinh đã ngừng hoạt động do đã tốt nghiệp hoặc chuyển trường")`.
