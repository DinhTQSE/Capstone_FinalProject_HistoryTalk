# 📋 IMPLEMENTATION PLAN: SCHOOL SUBSCRIPTION, PAY-AS-YOU-GO SHARED POOL, TEACHER TOKEN TRANSFER & TICKET WORKFLOW (SPRINT 7)
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Target User Story File:** `docs/sprints/sprint_7/user_stories/school_subscription_daily_token_and_lifecycle.md`  
> **Business Flow File:** `docs/sprints/sprint_7/business_flows/school_subscription_daily_token_and_lifecycle_flow.md`  
> **Stories Covered:** `US-SP7-14`, `US-SP7-15`, `US-SP7-16`, `US-SP7-17`, `US-SP7-18`, `US-SP7-19`, `US-SP7-20`  
> **Lập trình viên phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)  
> **Trạng thái:** Đã hoàn thành triển khai & kiểm thử (Implemented & Verified)  

---

## 🎯 1. TỔNG QUAN MỤC TIÊU & CÁC ISSUE ĐÃ GIẢI QUYẾT

1. **Issue 1 (`GET /api/v1/school-admin/dashboard` trả lỗi NullPointerException `principal is null`):**
   * **Nguyên nhân:** `JwtAuthenticationFilter` lưu `AuthenticatedPrincipal` vào `SecurityContextHolder`, nên tham số `@AuthenticationPrincipal UserPrincipal principal` tại `AnalyticsDashboardController` bị `null`.
   * **Giải pháp:** Cập nhật `AnalyticsDashboardController`, `AnalyticsDashboardServiceImpl` và `UserAvatarController` để tự động fallback lấy `uid` qua `SecurityUtils.getUserId()` và nạp `UserPrincipal` từ `UserRepository`.

2. **Issue 2 (Sửa nghiệp vụ Import Student Token sang Pure Shared Pool Pay-As-You-Go):**
   * **Nguyên nhân:** `AbstractStudentImportProcessor`, `SchoolWideImportProcessor` và `ClassroomImportProcessor` trước đây cấp cứng `defaultInitialToken` (10.000 token) cho mỗi học sinh lúc tạo tài khoản và trừ thẳng vào `school.unallocatedTokenQuota`.
   * **Giải pháp:**
     * Không trừ `school.unallocatedTokenQuota` khi import học sinh (`totalTokensDeducted = 0`).
     * Thiết lập hạn mức ngày khả dụng cho học sinh (`dailyTokenLimit` = `classroom.dailyTokenQuota` hoặc `school.defaultDailyStudentQuota`, mặc định `1.000 token/ngày`) và cho giáo viên (`school.defaultDailyTeacherQuota`, mặc định `10.000 token/ngày`).
     * Khi học sinh hoặc giáo viên chat với AI (`MessageServiceImpl`), hệ thống mới trừ số token thực tế sử dụng vào `school.unallocatedTokenQuota` và cộng vào `user.dailyTokensUsed`.

3. **Issue 3 (`GET /api/v1/school-admin/classrooms?page=0&size=200&academicYear=2026&sort=className,asc` trả lỗi 500):**
   * **Nguyên nhân:** Trùng phiên bản Flyway migration (`V33__extend_local_content.sql` trùng số `V33` và lệch lịch sử `V38`/`V39` trên cơ sở dữ liệu).
   * **Giải pháp:** Chuẩn hóa thứ tự file migration thành `V20261016140000__extend_local_content.sql`, `V20261018150000__school_subscription_daily_token_and_academic_lifecycle.sql` và `V20261020100000__teacher_daily_token_quota_and_ticket_workflow.sql`.

4. **Issue 4 (Teacher chưa có API để Upload Document PDF):**
   * **Giải pháp:**
     * Bổ sung 2 API upload PDF trực tiếp (`multipart/form-data`) trong `TeacherLocalContentController`:
       * `POST /api/v1/teacher/local-contents/contexts/{contextId}/documents/upload-pdf`
       * `POST /api/v1/teacher/local-contents/characters/{characterId}/documents/upload-pdf`
     * Mở quyền `@PreAuthorize` cho `TEACHER` và `SCHOOL_ADMIN` trên `PdfExtractionController` (`/api/v1/documents/extract-pdf`) và `DocumentFileController` (`/api/v1/documents/{docId}/pdf`, `/view-url`).

5. **Issue 5 (Luồng Giáo viên Tự Cấp Token của mình cho Học sinh + Tạo Ticket lên School Admin khi Giáo viên hết Token):**
   * **Giải pháp:**
     * Bổ sung cột `default_daily_teacher_quota` (mặc định `10.000 token/ngày`) vào bảng `school`.
     * Khi Học sinh hết token trong ngày:
       * Học sinh tạo yêu cầu (`POST /api/v1/student/token-requests` $\rightarrow$ `PENDING`) và Giáo viên duyệt (`PUT /api/v1/teacher/token-requests/{id}/review` với `action = APPROVE`), **HOẶC** Giáo viên chủ động cấp trực tiếp (`POST /api/v1/teacher/students/{studentId}/grant-tokens`).
       * Hệ thống trích trực tiếp từ hạn mức ngày còn lại của Giáo viên (`teacher.dailyTokensUsed += tokens`) sang `student.dailyBonusTokens += tokens` và chuyển trạng thái sang **`APPROVED` ngay lập tức** (không cần chờ School Admin).
     * Khi Giáo viên hết hạn mức ngày:
       * Giáo viên chuyển tiếp đơn của học sinh (`action = ESCALATE` $\rightarrow$ `PENDING_ADMIN`) hoặc tạo Ticket mới (`POST /api/v1/teacher/token-requests` $\rightarrow$ `PENDING_ADMIN`).
       * School Admin duyệt (`PUT /api/v1/school-admin/token-requests/{id}/review` với `action = APPROVE`) để cấp từ Quỹ chung của trường (`APPROVED`).
     * Cả 3 role (`SCHOOL_STUDENT`, `TEACHER`, `SCHOOL_ADMIN`) đều xem được danh sách và chi tiết Ticket qua `GET /api/v1/token-requests` và `GET /api/v1/token-requests/{requestId}`.

---

## 🏗️ 2. THIẾT KẾ CƠ SỞ DỮ LIỆU (FLYWAY MIGRATIONS V39 & V40)

### 2.1. Migration `V20261020100000__teacher_daily_token_quota_and_ticket_workflow.sql`
```sql
-- 1. Hạn mức token ngày của Giáo viên (mặc định 10.000 token/ngày, lớn hơn học sinh 1.000 token/ngày)
ALTER TABLE school
    ADD COLUMN IF NOT EXISTS default_daily_teacher_quota INTEGER NOT NULL DEFAULT 10000;

-- 2. Mở rộng bảng token_boost_request hỗ trợ cả Giáo viên cấp trực tiếp và Escalate/Ticket lên School Admin
ALTER TABLE token_boost_request
    ALTER COLUMN student_uid DROP NOT NULL,
    ALTER COLUMN classroom_id DROP NOT NULL,
    ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE token_boost_request
    ADD COLUMN IF NOT EXISTS requester_uid UUID REFERENCES "user"(uid),
    ADD COLUMN IF NOT EXISTS requester_role VARCHAR(30) NOT NULL DEFAULT 'SCHOOL_STUDENT',
    ADD COLUMN IF NOT EXISTS teacher_reviewed_by_uid UUID REFERENCES "user"(uid),
    ADD COLUMN IF NOT EXISTS teacher_review_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS teacher_reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS admin_reviewed_by_uid UUID REFERENCES "user"(uid),
    ADD COLUMN IF NOT EXISTS admin_review_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS admin_reviewed_at TIMESTAMP;
```

---

## 📂 3. DANH SÁCH FILE ĐÃ TẠO VÀ CHỈNH SỬA

| STT | File Path | Hành động | Mục đích |
| :--- | :--- | :--- | :--- |
| 1 | `src/main/resources/db/migration/V20261016140000__extend_local_content.sql` | Đổi tên từ V33 | Sửa lỗi xung đột version Flyway V33 |
| 2 | `src/main/resources/db/migration/V20261018150000__school_subscription_daily_token_and_academic_lifecycle.sql` | Đổi tên từ V38 | Đồng bộ lịch sử Flyway V39 |
| 3 | `src/main/resources/db/migration/V20261020100000__teacher_daily_token_quota_and_ticket_workflow.sql` | Tạo mới | Thêm `default_daily_teacher_quota` và mở rộng `token_boost_request` |
| 4 | `src/main/java/com/historytalk/controller/analytics/AnalyticsDashboardController.java` | Chỉnh sửa | Fix lỗi `principal is null` trên `/api/v1/school-admin/dashboard` |
| 5 | `src/main/java/com/historytalk/service/analytics/AnalyticsDashboardServiceImpl.java` | Chỉnh sửa | Fallback resolve `UserPrincipal` qua `SecurityUtils.getUserId()` |
| 6 | `src/main/java/com/historytalk/service/school/AbstractStudentImportProcessor.java` | Chỉnh sửa | Pure Shared Pool: Không trừ trước `unallocatedTokenQuota` khi import học sinh |
| 7 | `src/main/java/com/historytalk/service/school/SchoolWideImportProcessor.java` | Chỉnh sửa | Khởi tạo hạn mức ngày cho học sinh import toàn trường mà không trừ trước Quỹ trường |
| 8 | `src/main/java/com/historytalk/service/classroom/ClassroomImportProcessor.java` | Chỉnh sửa | Khởi tạo hạn mức ngày theo lớp/trường mà không trừ trước Quỹ trường |
| 9 | `src/main/java/com/historytalk/service/school/token/DailyTokenAllowanceServiceImpl.java` | Chỉnh sửa | Hỗ trợ hạn mức ngày cho cả `SCHOOL_STUDENT` (`1.000`) và `TEACHER` (`10.000`) |
| 10 | `src/main/java/com/historytalk/service/chat/MessageServiceImpl.java` | Chỉnh sửa | Khấu trừ Pay-As-You-Go vào Quỹ trường cho cả Học sinh và Giáo viên khi chat AI |
| 11 | `src/main/java/com/historytalk/entity/enums/TokenBoostRequestStatus.java` | Chỉnh sửa | `PENDING`, `PENDING_ADMIN`, `APPROVED`, `REJECTED` |
| 12 | `src/main/java/com/historytalk/entity/school/TokenBoostRequest.java` | Chỉnh sửa | Bổ sung thông tin người tạo ticket, giáo viên duyệt và admin duyệt |
| 13 | `src/main/java/com/historytalk/dto/school/TeacherGrantStudentTokenRequest.java` | Tạo mới | DTO cho phép Giáo viên cấp trực tiếp token của mình cho học sinh |
| 14 | `src/main/java/com/historytalk/service/school/token/TokenBoostRequestServiceImpl.java` | Chỉnh sửa | Luồng GV cấp token từ hạn mức của GV (`APPROVE` / `grantTokensFromTeacherToStudent`) & Escalate/Tạo Ticket lên School Admin (`PENDING_ADMIN`) |
| 15 | `src/main/java/com/historytalk/controller/school/SchoolLifecycleAndTokenController.java` | Chỉnh sửa | REST APIs cho Student, Teacher, School Admin tạo/duyệt/xem Ticket & cấp token trực tiếp |
| 16 | `src/main/java/com/historytalk/controller/localcontent/TeacherLocalContentController.java` | Chỉnh sửa | Thêm 2 endpoints upload PDF trực tiếp cho Context & Character của Teacher |
| 17 | `src/main/java/com/historytalk/service/localcontent/LocalContentServiceImpl.java` | Chỉnh sửa | Triển khai `uploadLocalContextPdfDocument` & `uploadLocalCharacterPdfDocument` |
| 18 | `src/main/java/com/historytalk/controller/document/DocumentFileController.java` | Chỉnh sửa | Cho phép `TEACHER` và `SCHOOL_ADMIN` upload/xem file PDF |
| 19 | `src/main/java/com/historytalk/controller/document/PdfExtractionController.java` | Chỉnh sửa | Cho phép `TEACHER` và `SCHOOL_ADMIN` trích xuất text từ PDF |
