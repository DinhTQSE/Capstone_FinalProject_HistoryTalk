# 📋 IMPLEMENTATION PLAN: SCHOOL SUBSCRIPTION, DAILY TOKEN POOL & ACADEMIC YEAR LIFECYCLE (SPRINT 7)
> **Sprint:** Sprint 7 (18/10/2026 – 30/10/2026)  
> **Target User Story File:** `docs/sprints/sprint_7/user_stories/school_subscription_daily_token_and_lifecycle.md`  
> **Business Flow File:** `docs/sprints/sprint_7/business_flows/school_subscription_daily_token_and_lifecycle_flow.md`  
> **Stories Covered:** `US-SP7-14`, `US-SP7-15`, `US-SP7-16`, `US-SP7-17`, `US-SP7-18`  
> **Lập trình viên phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)  
> **Trạng thái:** Đã lên kế hoạch (Planned)  

---

## 🎯 1. TỔNG QUAN MỤC TIÊU & PHẠM VI

Kế hoạch này giải quyết triệt để 5 bài toán cốt lõi của mô hình B2B Trường học:
1. **`US-SP7-14` (Chu kỳ Gói Trường học `01/09` – `31/05`, Gia hạn Tháng 5, Top-up & Khóa phân tầng):**
   * Quản lý thời hạn gói theo năm học (`subscription_start_date`, `subscription_end_date`, `renewed_for_next_year`).
   * Gia hạn trong tháng 5, kích hoạt từ `01/09` năm học kế tiếp (bảo lưu tối đa 20% token dư).
   * **Hết hạn gói (`EXPIRED`):** Khóa toàn bộ nền tảng của trường (`SchoolSubscriptionGuardInterceptor`).
   * **Còn hạn nhưng Hết Token:** Chỉ khóa tính năng AI, các tính năng khác (Quiz, Tài liệu, Bài tập, Dashboard) dùng bình thường.
2. **`US-SP7-15` (Quỹ Token Chung & Hạn mức Ngày - Daily Allowance):**
   * Bỏ cơ chế cấp đứt cả năm. Toàn bộ token nằm trong `school.unallocated_token_quota`.
   * Tự động hoàn trả token dư của ngày cũ về Quỹ trường và cấp hạn mức ngày mới (`daily_token_limit`, mặc định `1.000 token/ngày`) khi học sinh dùng AI trong ngày mới.
3. **`US-SP7-16` (Cấu hình Định mức Ngày & Gợi ý Gói Enterprise):**
   * Cung cấp thông tin định mức gợi ý (`EnterprisePackage`: Small ~350 HS, Medium ~1.500 HS, Large ~3.500 HS).
   * API cho School Admin cấu hình `default_daily_student_quota` toàn trường hoặc `daily_token_quota` riêng cho từng lớp/khối.
4. **`US-SP7-17` (Luồng Xin & Duyệt thêm Token trong ngày):**
   * Học sinh tạo yêu cầu xin thêm token trong ngày (`token_boost_request`).
   * Giáo viên phụ trách lớp hoặc School Admin duyệt cấp nhanh từ Quỹ trường vào `user.token`.
5. **`US-SP7-18` (Vòng đời Năm học: Lên lớp `ARCHIVED` & Ra trường/Chuyển trường `DEACTIVATED`):**
   * Chuyển các lớp năm cũ sang `ARCHIVED` để học sinh vào lớp `ACTIVE` của năm học mới (`academic_year` mới) mà giữ nguyên tài khoản.
   * Khi học sinh ra trường hoặc chuyển trường: Thu hồi token dư trong ngày về Quỹ trường và chuyển tài khoản sang **`DEACTIVATED` (Khóa đăng nhập hoàn toàn)**.

---

## 🏗️ 2. THIẾT KẾ CƠ SỞ DỮ LIỆU (FLYWAY MIGRATION V38)

Tạo file `src/main/resources/db/migration/V38__school_subscription_daily_token_and_academic_lifecycle.sql`:

```sql
-- 1. Mở rộng bảng school cho chu kỳ năm học (01/09 - 31/05) và hạn mức ngày mặc định
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS subscription_start_date DATE,
    ADD COLUMN IF NOT EXISTS subscription_end_date DATE,
    ADD COLUMN IF NOT EXISTS renewed_for_next_year BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS next_package_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS default_daily_student_quota INTEGER NOT NULL DEFAULT 1000;

-- 2. Mở rộng bảng classroom cho phép cấu hình hạn mức ngày riêng theo lớp/khối (nullable -> fallback về school.default_daily_student_quota)
ALTER TABLE classroom
    ADD COLUMN IF NOT EXISTS daily_token_quota INTEGER;

-- 3. Mở rộng bảng "user" để quản lý trạng thái DEACTIVATED khi học sinh ra trường / chuyển trường
ALTER TABLE "user"
    ADD COLUMN IF NOT EXISTS account_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS deactivated_reason VARCHAR(255);

-- 4. Tạo bảng token_boost_request cho luồng học sinh xin thêm token trong ngày (US-SP7-17)
CREATE TABLE IF NOT EXISTS token_boost_request (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school(id),
    classroom_id UUID NOT NULL REFERENCES classroom(id),
    student_uid UUID NOT NULL REFERENCES "user"(uid),
    requested_tokens INTEGER NOT NULL,
    approved_tokens INTEGER,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    reviewed_by_uid UUID REFERENCES "user"(uid),
    review_note VARCHAR(500),
    request_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_boost_req_classroom_status
    ON token_boost_request(classroom_id, status, request_date);
CREATE INDEX IF NOT EXISTS idx_token_boost_req_student_date
    ON token_boost_request(student_uid, request_date);
```

---

## 🗺️ 3. LỘ TRÌNH THỰC HIỆN THEO 6 PHASE

```text
Phase 1: Database Migration (Flyway V38) & Enums
   ├── V38__school_subscription_daily_token_and_academic_lifecycle.sql
   ├── SchoolStatus (ACTIVE, INACTIVE, SUSPENDED, EXPIRED)
   ├── AccountStatus (ACTIVE, DEACTIVATED)
   ├── TokenBoostRequestStatus (PENDING, APPROVED, REJECTED)
   └── EnterprisePackage (bổ sung recommendedStudents & defaultDailyQuota)
         │
         ▼
Phase 2: Entities & Repositories
   ├── Cập nhật School.java, Classroom.java, User.java
   ├── Tạo mới TokenBoostRequest.java & TokenBoostRequestRepository.java
   └── Cập nhật ClassStudentRepository (chỉ check trùng trên lớp có status = 'ACTIVE')
         │
         ▼
Phase 3: US-SP7-14 (Subscription Lifecycle 01/09 - 31/05, May Renewal, Top-up & Platform Guard)
   ├── SchoolSubscriptionService & Implementation (Renew in May, Top-up, 20% Rollover on Sep 1)
   ├── SchoolSubscriptionGuardFilter / Interceptor (Chặn toàn bộ nền tảng khi EXPIRED)
   └── Endpoints: POST /api/v1/admin/schools/{id}/renew & POST /api/v1/admin/schools/{id}/top-up
         │
         ▼
Phase 4: US-SP7-15 & US-SP7-16 (Shared Pool + Daily Token Allowance & Policy Config)
   ├── DailyTokenAllowanceService (Lazy Daily Reset + Refund unused yesterday tokens to Pool)
   ├── Tích hợp vào luồng Chat AI (Hết token -> Chỉ khóa AI, trả về 403 AI_QUOTA_EXCEEDED)
   └── Endpoint: PUT /api/v1/school-admin/token-policy
         │
         ▼
Phase 5: US-SP7-17 (Daily Token Boost Request & Teacher/Admin Approval)
   ├── TokenBoostRequestService & Implementation
   └── Endpoints: POST /api/v1/student/token-requests, GET & PUT /api/v1/teacher/token-requests
         │
         ▼
Phase 6: US-SP7-18 (Academic Year Rollover, Class Archive & Student Account Deactivation)
   ├── AcademicLifecycleService (Archive old classes, Promote/Transfer, Deactivate Graduated/Transferred)
   ├── Cập nhật AuthServiceImpl & JwtAuthenticationFilter (Chặn đăng nhập khi accountStatus == DEACTIVATED)
   └── Unit Tests & Integration Tests (JUnit 5 + MockMvc)
```

---

## 📂 4. DANH SÁCH FILE CẦN TẠO VÀ CHỈNH SỬA

| STT | File Path | Hành động | Mục đích |
| :--- | :--- | :--- | :--- |
| 1 | `src/main/resources/db/migration/V38__school_subscription_daily_token_and_academic_lifecycle.sql` | Tạo mới | Thêm cột hạn gói năm học, hạn mức ngày, trạng thái `account_status` và bảng `token_boost_request` |
| 2 | `src/main/java/com/historytalk/entity/enums/SchoolStatus.java` | Chỉnh sửa | Thêm trạng thái `EXPIRED` |
| 3 | `src/main/java/com/historytalk/entity/enums/AccountStatus.java` | Tạo mới | Enum `ACTIVE`, `DEACTIVATED` |
| 4 | `src/main/java/com/historytalk/entity/enums/TokenBoostRequestStatus.java` | Tạo mới | Enum `PENDING`, `APPROVED`, `REJECTED` |
| 5 | `src/main/java/com/historytalk/entity/enums/EnterprisePackage.java` | Chỉnh sửa | Bổ sung thông tin `recommendedStudents` (350, 1500, 3500) |
| 6 | `src/main/java/com/historytalk/entity/school/School.java` | Chỉnh sửa | Thêm `subscriptionStartDate`, `subscriptionEndDate`, `renewedForNextYear`, `defaultDailyStudentQuota` |
| 7 | `src/main/java/com/historytalk/entity/classroom/Classroom.java` | Chỉnh sửa | Thêm `dailyTokenQuota` |
| 8 | `src/main/java/com/historytalk/entity/user/User.java` | Chỉnh sửa | Thêm `accountStatus`, `deactivatedReason` |
| 9 | `src/main/java/com/historytalk/entity/school/TokenBoostRequest.java` | Tạo mới | Entity yêu cầu xin thêm token trong ngày |
| 10 | `src/main/java/com/historytalk/repository/school/TokenBoostRequestRepository.java` | Tạo mới | Repository truy vấn yêu cầu xin thêm token theo lớp/trường/ngày |
| 11 | `src/main/java/com/historytalk/repository/classroom/ClassStudentRepository.java` | Chỉnh sửa | Cho phép học sinh vào lớp `ACTIVE` mới khi lớp năm cũ đã `ARCHIVED` |
| 12 | `src/main/java/com/historytalk/security/SchoolSubscriptionGuardInterceptor.java` | Tạo mới | Chặn toàn bộ nền tảng trường khi gói hết hạn (`EXPIRED`), nhưng mở Non-AI khi chỉ hết token |
| 13 | `src/main/java/com/historytalk/security/JwtAuthenticationFilter.java` | Chỉnh sửa | Chặn ngay token của tài khoản có `accountStatus == DEACTIVATED` |
| 14 | `src/main/java/com/historytalk/service/auth/AuthServiceImpl.java` | Chỉnh sửa | Chặn đăng nhập đối với học sinh đã `DEACTIVATED` (ra trường/chuyển trường) |
| 15 | `src/main/java/com/historytalk/service/school/token/DailyTokenAllowanceService.java` | Tạo mới | Xử lý Lazy Daily Reset, hoàn trả token dư ngày cũ về Quỹ trường & cấp hạn mức ngày mới |
| 16 | `src/main/java/com/historytalk/service/school/token/TokenBoostRequestService.java` | Tạo mới | Xử lý học sinh xin thêm token & giáo viên/admin duyệt |
| 17 | `src/main/java/com/historytalk/service/school/AcademicLifecycleService.java` | Tạo mới | Xử lý gia hạn tháng 5, Top-up, Rollover năm học (`ARCHIVED`) & Deactivate học sinh ra trường |
| 18 | `src/main/java/com/historytalk/controller/school/SchoolLifecycleAndTokenController.java` | Tạo mới | REST APIs cho `US-SP7-14`, `US-SP7-16`, `US-SP7-17`, `US-SP7-18` |

---

## 🧪 5. KẾ HOẠCH KIỂM THỬ (UNIT & INTEGRATION TESTS)

1. **`DailyTokenAllowanceServiceTest`:**
   * Học sinh gọi AI lần đầu trong ngày: Hoàn trả token dư ngày hôm qua về `school.unallocatedTokenQuota` và cấp mới `1.000 token` cho hôm nay.
   * Học sinh gọi AI lần thứ 2 trong cùng ngày: Không reset lại mà trừ tiếp vào `user.token` hiện tại.
   * Trường hết sạch `unallocatedTokenQuota` và `user.token == 0`: Ném ngoại lệ `AI_QUOTA_EXCEEDED` (chỉ chặn AI, không khóa các API Non-AI).
2. **`SchoolSubscriptionAndLifecycleServiceTest`:**
   * Gia hạn gói trường hợp lệ trong tháng 5 (`01/05` – `31/05`) vs Báo lỗi nếu gia hạn sai quy định.
   * Kích hoạt năm học mới (`01/09`): Cộng dồn tối đa **20% token dư** của năm cũ sang năm mới.
   * Hết hạn ngày `31/05` chưa gia hạn: Khóa toàn bộ nền tảng trường (`403 SCHOOL_SUBSCRIPTION_EXPIRED`).
   * Chuyển giao năm học: Các lớp cũ chuyển sang `ARCHIVED`, học sinh vào được lớp `ACTIVE` của năm học mới.
   * Học sinh ra trường / chuyển trường: Tài khoản chuyển sang `DEACTIVATED`, thu hồi token dư về trường, gọi Login bị chặn `403 ACCOUNT_DEACTIVATED`.
3. **`TokenBoostRequestServiceTest`:**
   * Học sinh gửi yêu cầu xin thêm token trong ngày thành công.
   * Giáo viên dạy đúng lớp duyệt yêu cầu $\rightarrow$ Cộng token ngay vào `student.token` và trừ từ `school.unallocatedTokenQuota`.
   * Giáo viên lớp khác cố tình duyệt $\rightarrow$ Chặn `403 Forbidden`.
