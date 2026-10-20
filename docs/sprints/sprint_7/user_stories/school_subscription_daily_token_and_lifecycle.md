# 📋 USER STORIES: SCHOOL SUBSCRIPTION, PAY-AS-YOU-GO SHARED TOKEN POOL, TEACHER TOKEN TRANSFER & TICKET WORKFLOW
> **Sprint:** Sprint 7 (16/10/2026 – 30/10/2026)  
> **Phân hệ:** Quản trị Gói B2B Trường học theo Năm học, Quỹ Token Chung Pay-As-You-Go, Luồng Giáo viên Cấp Token & Ticket Duyệt Token, Upload Tài liệu PDF Giáo viên & Vòng đời Học sinh  
> **Lập trình viên phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP7-14`** | Chu kỳ Gói Trường học (01/09 – 31/05), Gia hạn Tháng 5, Top-up & Khóa phân tầng | `System Admin`, `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/admin/schools/{id}/renew`<br>`POST /api/v1/admin/schools/{id}/top-up` |
| **`US-SP7-15`** | Quỹ Token Chung Pay-As-You-Go (Không cấp cứng khi Import) & Hạn mức Ngày cho Học sinh (`1.000`) và Giáo viên (`10.000`) | `System`, `School Student`, `Teacher` | 🔵 Dinh (`DinhTQ`) | Student/Teacher Import & AI Chat Pay-As-You-Go Guard |
| **`US-SP7-16`** | Cấu hình Định mức Token Ngày (Học sinh & Giáo viên) & Bảng Ước tính Gói Enterprise | `School Admin` | 🔵 Dinh (`DinhTQ`) | `PUT /api/v1/school-admin/token-policy` |
| **`US-SP7-17`** | Giáo viên Cấp Token của mình cho Học sinh ngay lập tức & Tạo Ticket lên School Admin khi Giáo viên hết Token (Cả 3 Role đều View được Ticket) | `School Student`, `Teacher`, `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/student/token-requests`<br>`POST /api/v1/teacher/students/{studentId}/grant-tokens`<br>`POST /api/v1/teacher/token-requests`<br>`PUT /api/v1/teacher/token-requests/{id}/review`<br>`PUT /api/v1/school-admin/token-requests/{id}/review`<br>`GET /api/v1/token-requests` |
| **`US-SP7-18`** | Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Khóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`) | `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/school-admin/academic-year/rollover`<br>`PUT /api/v1/school-admin/students/{uid}/deactivate` |
| **`US-SP7-19`** | Giáo viên Upload Tài liệu PDF cho Bối cảnh & Nhân vật Lịch sử Địa phương | `Teacher`, `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/teacher/local-contents/contexts/{contextId}/documents/upload-pdf`<br>`POST /api/v1/teacher/local-contents/characters/{characterId}/documents/upload-pdf` |
| **`US-SP7-20`** | Khắc phục lỗi `NullPointerException` trên Dashboard School Admin & lỗi 500 trên API Classrooms | `School Admin`, `Teacher` | 🔵 Dinh (`DinhTQ`) | `GET /api/v1/school-admin/dashboard`<br>`GET /api/v1/school-admin/classrooms` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP7-14: Chu kỳ Gói Trường học (01/09 – 31/05), Gia hạn Tháng 5, Top-up & Cơ chế Khóa phân tầng
* **Actor:** `System Admin`, `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **User Story:**  
  Là **School Admin / System Admin**, tôi muốn gói B2B của trường được định nghĩa theo chu kỳ năm học chính thức (**từ 01/09 đến hết 31/05**), có cửa sổ gia hạn trong **tháng 5** (kích hoạt từ **01/09** năm học mới), hỗ trợ nạp thêm (**Top-up**) khi cạn token giữa năm, và phân tách rõ ràng giữa hành vi **Hết hạn gói** với **Hết Token AI**.
* **Acceptance Criteria (AC):**
  1. **Chu kỳ gói chuẩn:** Từ **`01/09/{startYear} 00:00:00`** đến **`31/05/{endYear} 23:59:59`**.
  2. **Gia hạn trong Tháng 5:** Trường gia hạn trong tháng 5 (`01/05` – `31/05`), kích hoạt vào `01/09` và bảo lưu tối đa **20% lượng token còn dư** của năm cũ sang Quỹ chung năm mới.
  3. **Hết hạn gói (`EXPIRED` $\rightarrow$ Khóa toàn bộ nền tảng):** Trả về `403 SCHOOL_SUBSCRIPTION_EXPIRED`.
  4. **Còn hạn gói nhưng Hết Token (`Out of Tokens` $\rightarrow$ Chỉ khóa tính năng AI):** Chỉ chặn Chat AI (`403 AI_QUOTA_EXCEEDED`), các tính năng Quiz, Tài liệu, Assignment, Dashboard vẫn hoạt động bình thường.

---

### 🆔 US-SP7-15: Quỹ Token Chung Pay-As-You-Go (Không cấp cứng khi Import) & Hạn mức Ngày cho Học sinh (`1.000`) và Giáo viên (`10.000`)
* **Actor:** `System`, `School Student`, `Teacher`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Trước đây khi Import học sinh (`ClassroomImportProcessor` / `SchoolWideImportProcessor`), hệ thống cấp cứng một lượng token cố định (ví dụ `10.000 token`) cho mỗi tài khoản học sinh ngay lúc tạo tài khoản và trừ thẳng vào `school.unallocatedTokenQuota`. Cách làm này khiến trường bị trừ sạch quỹ token ngay khi import danh sách học sinh dù học sinh chưa hề sử dụng, gây "giam token" (*stranded tokens*). Ngoài ra, Giáo viên cũng cần hạn mức token theo ngày lớn hơn Học sinh để vừa phục vụ giảng dạy/chat AI vừa trích token cấp thêm cho học sinh khi cần.
* **User Story:**  
  Là **Hệ thống**, tôi muốn khi tạo/import tài khoản Học sinh hoặc Giáo viên thì **không trừ cứng token từ Quỹ trường trước**, mà giữ **100% token trong Quỹ chung (`school.unallocatedTokenQuota`)**; mỗi người dùng được thiết lập **Hạn mức chi tiêu theo ngày** (**Học sinh mặc định `1.000 token/ngày`**, **Giáo viên mặc định `10.000 token/ngày`**) và **dùng bao nhiêu mới trừ bấy nhiêu vào tổng token của trường**.
* **Acceptance Criteria (AC):**
  1. **Import Học sinh & Tạo Giáo viên không trừ trước Quỹ trường (`totalTokensDeducted = 0`):**
     * Khi import danh sách học sinh (`POST /api/v1/school-admin/students/import` hoặc `/api/v1/school-admin/classrooms/{id}/students/import`), hệ thống khởi tạo hạn mức khả dụng trong ngày cho học sinh (`user.token = COALESCE(classroom.dailyTokenQuota, school.defaultDailyStudentQuota, 1000)`), `dailyTokensUsed = 0`, `dailyBonusTokens = 0`.
     * **Tuyệt đối không trừ `school.unallocatedTokenQuota` lúc tạo tài khoản hay lúc cấp hạn mức ngày**.
     * Khi tạo tài khoản Giáo viên, hệ thống khởi tạo hạn mức khả dụng trong ngày của Giáo viên theo `school.defaultDailyTeacherQuota` (mặc định **`10.000 token/ngày`**).
  2. **Hạn mức Ngày của Giáo viên lớn hơn Học sinh (`10.000` vs `1.000`):**
     * Học sinh (`ROLE_SCHOOL_STUDENT`): Hạn mức mặc định **`1.000 token/ngày`** (hoặc theo cấu hình lớp/trường).
     * Giáo viên (`ROLE_TEACHER`): Hạn mức mặc định **`10.000 token/ngày`** (`school.defaultDailyTeacherQuota`) để Giáo viên vừa thoải mái chat với AI vừa có quỹ hạn mức trong ngày để trích cấp trực tiếp cho học sinh trong lớp khi học sinh hết token.
  3. **Khấu trừ Pay-As-You-Go khi Chat AI (`MessageServiceImpl` $\rightarrow$ `DailyTokenAllowanceService`):**
     * Sang ngày mới (`lastTokenResetAt < today.atStartOfDay()`), hệ thống tự động reset `dailyTokensUsed = 0`, `dailyBonusTokens = 0` cho cả Học sinh và Giáo viên.
     * Khi Học sinh hoặc Giáo viên chat với AI hết `totalToken`: Hệ thống trừ trực tiếp `totalToken` vào `school.unallocatedTokenQuota` và cộng `totalToken` vào `user.dailyTokensUsed`, đồng thời đồng bộ lại `user.token` hiển thị số dư còn được tiêu trong ngày.

---

### 🆔 US-SP7-16: Cấu hình Định mức Token Ngày (Học sinh & Giáo viên) & Bảng Ước tính Gói Enterprise
* **Actor:** `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **User Story:**  
  Là **School Admin**, tôi muốn cấu hình được cả hạn mức ngày mặc định của Học sinh (`defaultDailyStudentQuota`), hạn mức ngày mặc định của Giáo viên (`defaultDailyTeacherQuota`), và hạn mức riêng cho từng khối/lớp (`classroomDailyQuota`).
* **Acceptance Criteria (AC):**
  1. `PUT /api/v1/school-admin/token-policy` hỗ trợ cập nhật:
     * `defaultDailyStudentQuota` (mặc định `1.000 token/ngày`).
     * `defaultDailyTeacherQuota` (mặc định `10.000 token/ngày`, tự động đồng bộ lại hạn mức ngày khả dụng cho các Giáo viên trong trường).
     * `classroomId` hoặc `gradeLevel` + `classroomDailyQuota` cho từng lớp/khối cụ thể.

---

### 🆔 US-SP7-17: Luồng Giáo viên Tự Cấp Token của mình cho Học sinh & Tạo Ticket lên School Admin khi Giáo viên hết Token (Cả 3 Role đều View được Ticket)
* **Actor:** `School Student`, `Teacher`, `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Nếu học sinh hết token trong ngày mà phải đợi Giáo viên duyệt rồi lại phải chờ tiếp School Admin duyệt thì quá lâu, làm gián đoạn tiết học. Do đó, Giáo viên có hạn mức ngày lớn hơn (`10.000 token/ngày`) sẽ **tự trích trực tiếp từ hạn mức ngày của Giáo viên để cấp ngay cho học sinh**. Chỉ khi nào **Giáo viên cũng hết hạn mức token trong ngày** thì Giáo viên mới **tạo Ticket / chuyển tiếp Ticket lên cho School Admin duyệt cấp thêm**.
* **User Story:**  
  Là **Học sinh / Giáo viên / School Admin**, tôi muốn:
  1. Khi **Học sinh hết token trong ngày**: Học sinh tạo yêu cầu xin thêm token + lý do $\rightarrow$ **Giáo viên duyệt là trích ngay từ hạn mức token ngày của Giáo viên đưa cho Học sinh dùng luôn** (hoặc Giáo viên chủ động bấm cấp token của mình cho học sinh mà không cần chờ đơn), không phải chờ School Admin.
  2. Khi **Giáo viên hết hạn mức token trong ngày**: Giáo viên có thể **tạo Ticket xin cấp thêm token cho học sinh (hoặc cho chính mình)** hoặc **chuyển tiếp (Escalate) đơn của học sinh lên cho School Admin duyệt**.
  3. Khi Ticket được tạo, **cả 3 role (`SCHOOL_STUDENT`, `TEACHER`, `SCHOOL_ADMIN`) đều xem được danh sách và chi tiết Ticket**.
* **Acceptance Criteria (AC):**
  1. **Trường hợp 1 — Học sinh hết token, Giáo viên còn hạn mức ngày (Cấp ngay lập tức - 1 bước):**
     * **Cách 1 (Học sinh tạo đơn $\rightarrow$ Giáo viên bấm `APPROVE`):**
       * Học sinh gọi `POST /api/v1/student/token-requests` với `requestedTokens` và `reason` $\rightarrow$ Ticket được tạo với trạng thái `PENDING`.
       * Giáo viên phụ trách lớp gọi `PUT /api/v1/teacher/token-requests/{requestId}/review` với `action = "APPROVE"`.
       * Hệ thống kiểm tra hạn mức ngày còn lại của Giáo viên (`teacherRemainingToday >= approvedTokens`):
         * Trừ vào hạn mức ngày của Giáo viên: `teacher.dailyTokensUsed += approvedTokens` (giảm `teacher.token`).
         * Cộng ngay vào hạn mức thưởng trong ngày của Học sinh: `student.dailyBonusTokens += approvedTokens` (tăng `student.token`).
         * Chuyển trạng thái Ticket sang **`APPROVED`** ngay lập tức (không cần qua School Admin).
     * **Cách 2 (Giáo viên chủ động chuyển token của mình cho học sinh):**
       * Giáo viên gọi `POST /api/v1/teacher/students/{studentId}/grant-tokens` với `tokens` và `reason`.
       * Hệ thống trích ngay từ hạn mức ngày của Giáo viên sang Học sinh và lưu lịch sử Ticket ở trạng thái **`APPROVED`**.
  2. **Trường hợp 2 — Giáo viên hết hạn mức ngày $\rightarrow$ Tạo Ticket / Chuyển tiếp Ticket lên School Admin (`PENDING_ADMIN`):**
     * **Cách 1 (Chuyển tiếp đơn `PENDING` của học sinh lên Admin):**
       * Khi Giáo viên không đủ hạn mức ngày để tự cấp, Giáo viên gọi `PUT /api/v1/teacher/token-requests/{requestId}/review` với `action = "ESCALATE"` và `reviewNote`.
       * Hoặc nếu Giáo viên bấm `APPROVE` lúc đã cạn sạch hạn mức ngày (`0 token`), thông báo lỗi hướng dẫn Giáo viên dùng `ESCALATE` hoặc tạo Ticket lên School Admin.
       * Trạng thái Ticket chuyển từ `PENDING` $\rightarrow$ **`PENDING_ADMIN`**.
     * **Cách 2 (Giáo viên trực tiếp tạo Ticket gửi School Admin):**
       * Giáo viên gọi `POST /api/v1/teacher/token-requests` với `{ studentUid (optional), requestedTokens, reason }`.
       * Ticket được tạo trực tiếp với trạng thái **`PENDING_ADMIN`** (`requesterRole = "TEACHER"`). Nếu có `studentUid`, khi Admin duyệt sẽ cộng token cho học sinh đó; nếu không truyền `studentUid`, khi Admin duyệt sẽ cộng thêm hạn mức ngày cho chính Giáo viên.
     * **School Admin duyệt Ticket (`PUT /api/v1/school-admin/token-requests/{requestId}/review`):**
       * School Admin duyệt (`action = "APPROVE"`): Cấp thêm `dailyBonusTokens` từ Quỹ chung của trường (`school.unallocatedTokenQuota`), chuyển trạng thái Ticket sang **`APPROVED`**.
  3. **Cả 3 Role đều xem được Ticket (`GET /api/v1/token-requests` & `GET /api/v1/token-requests/{requestId}`):**
     * `ROLE_SCHOOL_STUDENT`: Xem toàn bộ các Ticket của mình (bao gồm cả Ticket do mình tạo và Ticket do Giáo viên tạo/cấp cho mình), thấy rõ tiến độ (`PENDING`, `PENDING_ADMIN`, `APPROVED`, `REJECTED`), ghi chú của Giáo viên và ghi chú của School Admin.
     * `ROLE_TEACHER`: Xem toàn bộ các Ticket của các lớp mình phụ trách và các Ticket do chính mình tạo.
     * `ROLE_SCHOOL_ADMIN`: Xem toàn bộ các Ticket trong phạm vi toàn trường.

---

### 🆔 US-SP7-18: Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Vô hiệu hóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`)
* **Actor:** `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Acceptance Criteria (AC):**
  1. Chuyển giao năm học (`POST /api/v1/school-admin/academic-year/rollover`): Lưu trữ lớp cũ (`ARCHIVED`), giữ nguyên tài khoản cho học sinh Khối 10, 11 lên lớp mới.
  2. Vô hiệu hóa học sinh tốt nghiệp Khối 12 hoặc chuyển trường (`PUT /api/v1/school-admin/students/{uid}/deactivate`): Chuyển `accountStatus = DEACTIVATED`, khóa đăng nhập hoàn toàn (`403 ACCOUNT_DEACTIVATED`).

---

### 🆔 US-SP7-19: Giáo viên Upload Tài liệu PDF cho Bối cảnh & Nhân vật Lịch sử Địa phương
* **Actor:** `Teacher`, `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Trước đây chỉ có `STAFF` và `ADMIN` mới được gọi các API trích xuất/upload PDF (`PdfExtractionController`, `DocumentFileController`), còn `TEACHER` chỉ có API truyền JSON text thủ công mà chưa có API upload file PDF trực tiếp cho Local Context / Local Character.
* **User Story:**  
  Là **Giáo viên (`ROLE_TEACHER`)**, tôi muốn upload trực tiếp file PDF (`multipart/form-data`) vào Bối cảnh Lịch sử Địa phương (Local Context) hoặc Nhân vật Lịch sử Địa phương (Local Character) để hệ thống tự động trích xuất nội dung văn bản, lưu trữ file PDF và đồng bộ sang AI Service phục vụ RAG.
* **Acceptance Criteria (AC):**
  1. Cung cấp API upload PDF 1 bước (`multipart/form-data`) cho Giáo viên:
     * `POST /api/v1/teacher/local-contents/contexts/{contextId}/documents/upload-pdf` (`file`, `title` optional).
     * `POST /api/v1/teacher/local-contents/characters/{characterId}/documents/upload-pdf` (`file`, `title` optional).
  2. Cho phép `TEACHER` và `SCHOOL_ADMIN` sử dụng các API hỗ trợ PDF:
     * `POST /api/v1/documents/extract-pdf`
     * `PUT /api/v1/documents/{docId}/pdf`
     * `GET /api/v1/documents/{docId}/view-url` (cho phép cả `TEACHER`, `SCHOOL_ADMIN`, `SCHOOL_STUDENT`).

---

### 🆔 US-SP7-20: Khắc phục lỗi NullPointerException trên Dashboard School Admin & lỗi 500 trên API Classrooms
* **Actor:** `School Admin`, `Teacher`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Acceptance Criteria (AC):**
  1. **`GET /api/v1/school-admin/dashboard`:** Khắc phục triệt để lỗi `"Cannot invoke com.historytalk.security.UserPrincipal.getUsername() because principal is null"` bằng cách fallback qua `SecurityUtils.getUserId()` khi `JwtAuthenticationFilter` lưu `AuthenticatedPrincipal` trong `SecurityContext`.
  2. **`GET /api/v1/school-admin/classrooms?page=0&size=200&academicYear=2026&sort=className,asc`:** Đồng bộ lịch sử Flyway (`V38`, `V39`, `V40`) và schema cơ sở dữ liệu để API phân trang và sắp xếp danh sách lớp trả về `200 OK`.
