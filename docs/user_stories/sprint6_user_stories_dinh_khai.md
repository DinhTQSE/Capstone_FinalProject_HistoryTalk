# 🚀 SPRINT 6 — USER STORIES (11/10/2026 – 17/10/2026)
> **Mục tiêu Sprint 6:** Class Student Association, Local History Content Workflow & Policy Disclaimer, Assignment & Deadline Engine, 5 System Dashboards APIs.  
> **Phụ trách:** Trần Quốc Dinh (`DinhTQ`) & Võ Đồng Đức Khải (`KhaiVDD`)  
> **Thời gian:** 11/10/2026 – 17/10/2026  

---

## 📌 TỔNG HỢP USER STORIES IN SPRINT 6

| Story ID | Tên User Story | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- |
| **`US-SP6-01`** | Gán & Chuyển Học sinh vào Lớp học theo danh sách | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/classrooms/{classId}/students/batch` |
| **`US-SP6-02`** | Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương | 🟢 **Khải (KhaiVDD)** | `POST /api/v1/teacher/local-contents` |
| **`US-SP6-03`** | School Admin Duyệt Local Content & Policy Disclaimer | 🟢 **Khải (KhaiVDD)** | `PUT /api/v1/school-admin/local-contents/{id}/status` |
| **`US-SP6-04`** | Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline | 🔵 **Định (DinhTQ)** | `POST /api/v1/teacher/assignments` |
| **`US-SP6-05`** | Học sinh Nộp bài & Tự động Đánh giá Tiến độ | 🔵 **Định (DinhTQ)** | `POST /api/v1/student/assignments/{id}/submit` |
| **`US-SP6-06`** | API Dashboard Báo cáo Trường học (School Admin) | 🔵 **Định (DinhTQ)** | `GET /api/v1/school-admin/dashboard` |
| **`US-SP6-07`** | API Dashboard Giáo viên & Analytics Lớp học | 🔵 **Định (DinhTQ)** | `GET /api/v1/teacher/dashboard/classrooms/{classId}` |
| **`US-SP6-08`** | API Dashboard Tiến độ Học sinh Cá nhân | 🔵 **Định (DinhTQ)** | `GET /api/v1/student/dashboard` |
| **`US-SP6-09`** | API Dashboard Khách hàng B2C Cá nhân | 🔵 **Định (DinhTQ)** | `GET /api/v1/customer/dashboard` |
| **`US-SP6-10`** | API Dashboard Admin Hệ thống & Sức khỏe JVM | 🔵 **Định (DinhTQ)** | `GET /api/v1/admin/dashboard` |

---

## 📑 CHI TIẾT USER STORIES SPRINT 6

### 🆔 US-SP6-01: Gán & Chuyển Học sinh vào Lớp học (Class Roster Association)
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `School Admin` / `Teacher`
* **Thời gian:** 11/10/2026 – 12/10/2026
* **User Story:**  
  Là School Admin hoặc Giáo viên, tôi muốn gán hàng loạt học sinh vào lớp học hoặc chuyển học sinh ra khỏi lớp, để quản lý danh sách học sinh theo từng lớp học chính xác.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/classrooms/{classId}/students/batch` tiếp nhận danh sách `student_ids` để thêm vào Lớp.
  2. API `DELETE /api/v1/classrooms/{classId}/students/{studentId}` xóa/gỡ học sinh khỏi Lớp.
  3. Validate: Tất cả học sinh được thêm phải thuộc cùng `school_id` với Lớp học.

---

### 🆔 US-SP6-02: Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương Nội bộ Trường (Local Content Sharing)
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `Teacher`
* **Thời gian:** 12/10/2026 – 13/10/2026
* **User Story:**  
  Là Giáo viên, tôi muốn tạo mới Bối cảnh, Nhân vật hoặc Quiz mang tính chất Lịch sử Địa phương và tự động chia sẻ nội bộ với các Giáo viên khác trong trường, để phục vụ việc giao bài tập đặc thù của nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/local-contents` tạo nội dung địa phương gắn liền với `school_id` và `created_by_teacher_id`. Mặc định trạng thái là `DRAFT`.
  2. Tất cả Giáo viên thuộc **cùng `school_id` đó** đều xem và dùng chung được nội dung địa phương này để tham khảo và giao bài tập.
  3. Người dùng thuộc trường khác hoặc B2C Customer **tuyệt đối không thể truy cập** được nội dung địa phương này.

---

### 🆔 US-SP6-03: School Admin Duyệt Local Content & Policy Disclaimer Enforcement
* **Developer:** 🟢 **Khải (KhaiVDD)**
* **Actor:** `School Admin`
* **Thời gian:** 13/10/2026 – 14/10/2026
* **User Story:**  
  Là School Admin, tôi muốn kiểm duyệt nội dung Lịch sử Địa phương do Giáo viên trường mình soạn thảo và bấm Duyệt (`PUBLISHED`), để học sinh trong trường làm bài tập và chịu trách nhiệm nội dung theo đúng Policy nhà trường.
* **Acceptance Criteria (AC):**
  1. API `PUT /api/v1/school-admin/local-contents/{id}/status` cho phép chuyển trạng thái giữa `PUBLISHED`, `DRAFT`, và `INACTIVE`.
  2. Lưu trường `approved_by_school_admin_id` để phục vụ đối soát nội bộ nhà trường.
  3. Theo Điều khoản dịch vụ (Policy), bên cung cấp ứng dụng HistoryTalk hoàn toàn **miễn trừ trách nhiệm pháp lý** đối với tính xác thực của các nội dung Lịch sử Địa phương do nhà trường tự đăng tải.

---

### 🆔 US-SP6-04: Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline (Assignment Creation)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `Teacher`
* **Thời gian:** 14/10/2026 – 15/10/2026
* **User Story:**  
  Là Giáo viên, tôi muốn giao Bài tập cho Lớp phụ trách (gồm: Đọc Bối cảnh, Chat AI với Nhân vật, hoặc Làm Quiz kiểm tra) kèm Hạn nộp (`deadline`), để học sinh làm bài và thu thập kết quả.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/assignments` nhận: `class_id`, `title`, `assignment_type` (`CONTEXT_READING`, `AI_CHAT_ROLEPLAY`, `QUIZ_TEST`), `target_id`, `deadline`.
  2. Hỗ trợ chọn bài tập từ cả Kho Nội dung Chung (Global) lẫn Kho Lịch sử Địa phương (`PUBLISHED`) của trường.
  3. Tự động khởi tạo trạng thái `assignment_submission` là `NOT_STARTED` cho toàn bộ học sinh có trong Lớp.

---

### 🆔 US-SP6-05: Học sinh Nộp bài & Tự động Đánh giá Tiến độ (Assignment Submission & Auto-Grading)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Student` / System
* **Thời gian:** 15/10/2026
* **User Story:**  
  Là Học sinh hoặc Hệ thống, tôi muốn tự động chấm điểm bài Quiz bấm giờ và đếm số tin nhắn AI trò chuyện, để tự động hoàn thành bài tập và ghi nhận điểm số.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/student/assignments/{id}/submit`:
     * Với Bài Quiz: Tính điểm % câu trả lời đúng và lưu `score`.
     * Với Bài Chat AI: Đếm tổng số tin nhắn học sinh đã trò chuyện với nhân vật AI (`ai_chat_message_count`).
  2. Cập nhật trạng thái `COMPLETED` (nếu nộp trước hạn) hoặc `LATE_SUBMITTED` (nếu nộp sau hạn).

---

### 🆔 US-SP6-06: API Dashboard Báo cáo Trường học (School Admin Dashboard API)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Admin`
* **Thời gian:** 16/10/2026
* **User Story:**  
  Là School Admin, tôi muốn gọi API Dashboard tổng quan trường học, để theo dõi quy mô trường, tỷ lệ học sinh hoàn thành bài tập và mức độ tiêu thụ Token của gói Enterprise.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/dashboard` trả về JSON:
     * Tổng số Lớp học, Tổng số Giáo viên, Tổng số Học sinh.
     * Tỷ lệ hoàn thành bài tập toàn trường (% `COMPLETED`, % `LATE_SUBMITTED`, % `NOT_STARTED`).
     * Thống kê Tổng Token đã phân bổ vs Tổng Token học sinh tiêu thụ thực tế.

---

### 🆔 US-SP6-07: API Dashboard Giáo viên & Analytics Lớp học (Teacher Dashboard API)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `Teacher`
* **Thời gian:** 16/10/2026
* **User Story:**  
  Là Giáo viên, tôi muốn gọi API Dashboard Lớp học, để xem sổ điểm các bài Quiz Test và mức độ tương tác trò chuyện AI của từng học sinh trong lớp.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/teacher/dashboard/classrooms/{classId}` trả về:
     * Bảng điểm chi tiết bài Quiz Test của từng học sinh trong lớp.
     * Thống kê Tần suất/Số lượng tin nhắn tương tác AI (*Engagement Analytics*) của học sinh.
     * Tỷ lệ học sinh nộp bài Đúng hạn vs Trễ hạn.

---

### 🆔 US-SP6-08: API Dashboard Tiến độ Học sinh Cá nhân (Student Dashboard API)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `School Student`
* **Thời gian:** 17/10/2026
* **User Story:**  
  Là Học sinh, tôi muốn gọi API Dashboard cá nhân, để xem danh sách bài tập sắp đến hạn nộp, bảng điểm cá nhân và lượng Token AI còn lại của tôi.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/student/dashboard` trả về:
     * Danh sách Bài tập cần hoàn thành xếp theo Hạn nộp gần nhất (`Upcoming Deadlines`).
     * Bảng điểm cá nhân và Hạn mức Token AI còn lại trong ngày.

---

### 🆔 US-SP6-09: API Dashboard Khách hàng B2C Cá nhân (Customer Dashboard API)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `Customer`
* **Thời gian:** 17/10/2026
* **User Story:**  
  Là Người dùng B2C tự do, tôi muốn xem Dashboard cá nhân về điểm số Quiz tự luyện, gói cước hiện tại và lịch sử thanh toán qua PayOS.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/customer/dashboard` trả về: Lịch sử Quiz tự luyện tập, Gói cước cá nhân hiện tại (Free/Plus/Pro), Lịch sử thanh toán thành công.

---

### 🆔 US-SP6-10: API Dashboard Admin Hệ thống & Sức khỏe JVM (System Admin Dashboard API)
* **Developer:** 🔵 **Định (DinhTQ)**
* **Actor:** `System Admin`
* **Thời gian:** 17/10/2026
* **User Story:**  
  Là System Admin, tôi muốn gọi API Dashboard giám sát hệ thống, để xem chỉ số JVM Health, tổng lưu lượng Token tiêu thụ toàn nền tảng, báo cáo doanh thu PayOS và Top câu hỏi Quiz sai nhiều nhất.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/admin/dashboard` trả về:
     * JVM Health Metrics (CPU usage %, RAM MB, Thread Dump Java Spring Boot).
     * Báo cáo Tổng Token AI tiêu thụ toàn bộ các Trường học và Khách hàng B2C.
     * Top 10 câu hỏi Quiz có tỷ lệ làm sai cao nhất hệ thống.
