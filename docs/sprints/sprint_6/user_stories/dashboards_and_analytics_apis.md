# 📋 USER STORIES: DASHBOARDS & ANALYTICS APIS
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Hệ thống Báo cáo Thống kê & 5 Chuyên hệ Dashboard  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-06`** | API Dashboard Báo cáo Trường học (School Admin) | `School Admin` | 🔵 Dinh (DinhTQ) | `GET /api/v1/school-admin/dashboard` |
| **`US-SP6-07`** | API Dashboard Giáo viên & Analytics Lớp học | `Teacher` | 🔵 Dinh (DinhTQ) | `GET /api/v1/teacher/dashboard/classrooms/{classId}` |
| **`US-SP6-08`** | API Dashboard Tiến độ Học sinh Cá nhân | `School Student` | 🔵 Dinh (DinhTQ) | `GET /api/v1/student/dashboard` |
| **`US-SP6-09`** | API Dashboard Khách hàng B2C Cá nhân | `Customer` | 🔵 Dinh (DinhTQ) | `GET /api/v1/customer/dashboard` |
| **`US-SP6-10`** | API Dashboard Admin Hệ thống & Sức khỏe JVM | `System Admin` | 🔵 Dinh (DinhTQ) | `GET /api/v1/admin/dashboard` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-06: API Dashboard Báo cáo Trường học (School Admin Dashboard API)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 16/10/2026 (Cập nhật đồng bộ Sprint 7)
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là School Admin, tôi muốn gọi API Dashboard tổng quan trường học, để theo dõi chu kỳ gói năm học (`01/09` – `31/05`), quy mô trường, hạn mức Token ngày mặc định, số đơn xin thêm Token đang chờ duyệt và mức độ tiêu thụ trực tiếp từ Quỹ Token chung của trường.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/dashboard` trả về JSON:
     * Thông tin Chu kỳ Gói Trường học: `packageType`, `recommendedStudents`, `status` (`ACTIVE`/`EXPIRED`), `subscriptionStartDate` (`01/09`), `subscriptionEndDate` (`31/05`), `renewedForNextYear`, `nextPackageType`.
     * Tổng số Lớp học, Tổng số Giáo viên, Tổng số Học sinh đang hoạt động (`ACTIVE`).
     * Thống kê Quỹ Token Chung (`Pure Shared Pool`): `totalSchoolTokenQuota` (Tổng quỹ gói năm học + Top-up), `unallocatedTokenQuota` (Quỹ chung còn lại của trường), `allocatedTokens` / `tokensUsedByStudents` (Tổng token học sinh đã tiêu thụ thực tế trực tiếp từ Quỹ chung), `defaultDailyStudentQuota` (Định mức ngày mặc định, ví dụ `1.000 token/ngày`), và `pendingTokenBoostRequests` (Số yêu cầu xin thêm token đang chờ duyệt).
     * Tỷ lệ hoàn thành bài tập toàn trường (% `COMPLETED`, % `LATE_SUBMITTED`, % `NOT_STARTED`).

---

### 🆔 US-SP6-07: API Dashboard Giáo viên & Analytics Lớp học (Teacher Dashboard API)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 16/10/2026 (Cập nhật đồng bộ Sprint 7)
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Giáo viên, tôi muốn gọi API Dashboard Lớp học, để xem sổ điểm các bài Quiz Test, mức độ tương tác trò chuyện AI, hạn mức Token ngày của từng học sinh trong lớp và số yêu cầu xin thêm Token trong ngày đang chờ tôi duyệt.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/teacher/dashboard/classrooms/{classId}` trả về:
     * Thông tin lớp học: `className`, `classCode`, `gradeLevel`, `academicYear`, `status` (`ACTIVE`/`ARCHIVED`), `dailyTokenLimit` (Hạn mức token ngày áp dụng cho lớp), và `pendingTokenBoostRequests` (Số đơn học sinh trong lớp xin thêm token hôm nay đang chờ duyệt).
     * Bảng phân tích từng học sinh trong lớp: Điểm Quiz trung bình, số lượt làm Quiz, số tin nhắn AI (`aiMessagesCount`), và chi tiết hạn mức Token ngày hôm nay (`dailyTokenLimit`, `dailyBonusTokens`, `dailyTokensUsed`, `currentTokens` còn được tiêu hôm nay).
     * Tỷ lệ học sinh nộp bài Đúng hạn vs Trễ hạn.

---

### 🆔 US-SP6-08: API Dashboard Tiến độ Học sinh Cá nhân (Student Dashboard API)
* **Actor:** `School Student`
* **Thời gian thực hiện:** 17/10/2026 (Cập nhật đồng bộ Sprint 7)
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Học sinh, tôi muốn gọi API Dashboard cá nhân, để xem danh sách bài tập sắp đến hạn nộp, bảng điểm cá nhân, danh sách lớp học qua các năm học (`ACTIVE` / `ARCHIVED`), cùng hạn mức Token AI trong ngày của tôi (`dailyTokenLimit`, `dailyBonusTokens`, `tokensUsedToday`, `currentTokens` còn lại hôm nay).
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/student/dashboard` trả về:
     * Hạn mức Token AI theo ngày (trừ trực tiếp từ Quỹ chung của trường khi chat): `dailyTokenLimit` (định mức ngày cơ bản), `dailyBonusTokens` (định mức được giáo viên duyệt cộng thêm hôm nay), `tokensUsedToday` (đã tiêu hôm nay), `currentTokens` (hạn mức còn được phép tiêu hôm nay).
     * Danh sách lớp học (`enrolledClassrooms`) kèm `academicYear` và `status` (`ACTIVE` cho lớp năm hiện tại, `ARCHIVED` cho lớp các năm học cũ).
     * Danh sách Bài tập cần hoàn thành xếp theo Hạn nộp gần nhất (`Upcoming Deadlines`) và Bảng điểm cá nhân.

---

### 🆔 US-SP6-09: API Dashboard Khách hàng B2C Cá nhân (Customer Dashboard API)
* **Actor:** `Customer`
* **Thời gian thực hiện:** 17/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Người dùng B2C tự do, tôi muốn xem Dashboard cá nhân về điểm số Quiz tự luyện, gói cước hiện tại và lịch sử thanh toán qua PayOS.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/customer/dashboard` trả về: Lịch sử Quiz tự luyện tập, Gói cước cá nhân hiện tại (Free/Plus/Pro), Lịch sử thanh toán thành công.

---

### 🆔 US-SP6-10: API Dashboard Admin Hệ thống & Sức khỏe JVM (System Admin Dashboard API)
* **Actor:** `System Admin`
* **Thời gian thực hiện:** 17/10/2026 (Cập nhật đồng bộ Sprint 7)
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là System Admin, tôi muốn gọi API Dashboard giám sát hệ thống, để xem chỉ số JVM Health, tổng lưu lượng Token tiêu thụ toàn nền tảng (bao gồm Quỹ chung các trường B2B còn lại vs đã tiêu thụ), thống kê số trường `ACTIVE` vs `EXPIRED`, báo cáo doanh thu PayOS và Top câu hỏi Quiz sai nhiều nhất.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/admin/dashboard` trả về:
     * JVM Health Metrics (CPU usage %, RAM MB, Thread Dump Java Spring Boot).
     * Báo cáo Tổng Token AI (`tokenSummary`): `totalTokensB2bAllocated` (Tổng quỹ đã cấp cho các trường), `totalTokensB2bRemainingInPools` (Tổng quỹ chung còn lại tại các trường), `totalTokensB2bConsumed` (Tổng token khối trường học đã tiêu thụ), `totalTokensB2cConsumed` (Khối B2C đã tiêu thụ), `totalTokensSystemConsumed` (Toàn hệ thống).
     * Tổng quan nền tảng (`platformOverview`): `totalSchools`, `activeSchools` (trong hạn `01/09` – `31/05`), `expiredSchools` (hết hạn chưa gia hạn), `totalUsers`, `totalQuizzes`, `totalRevenue`.
     * Top 10 câu hỏi Quiz có tỷ lệ làm sai cao nhất hệ thống.
