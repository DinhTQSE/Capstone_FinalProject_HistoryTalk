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
* **Thời gian thực hiện:** 16/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là School Admin, tôi muốn gọi API Dashboard tổng quan trường học, để theo dõi quy mô trường, tỷ lệ học sinh hoàn thành bài tập và mức độ tiêu thụ Token của gói Enterprise.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/dashboard` trả về JSON:
     * Tổng số Lớp học, Tổng số Giáo viên, Tổng số Học sinh.
     * Tỷ lệ hoàn thành bài tập toàn trường (% `COMPLETED`, % `LATE_SUBMITTED`, % `NOT_STARTED`).
     * Thống kê Tổng Token đã phân bổ vs Tổng Token học sinh tiêu thụ thực tế.

---

### 🆔 US-SP6-07: API Dashboard Giáo viên & Analytics Lớp học (Teacher Dashboard API)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 16/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Giáo viên, tôi muốn gọi API Dashboard Lớp học, để xem sổ điểm các bài Quiz Test và mức độ tương tác trò chuyện AI của từng học sinh trong lớp.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/teacher/dashboard/classrooms/{classId}` trả về:
     * Bảng điểm chi tiết bài Quiz Test của từng học sinh trong lớp.
     * Thống kê Tần suất/Số lượng tin nhắn tương tác AI (*Engagement Analytics*) của học sinh.
     * Tỷ lệ học sinh nộp bài Đúng hạn vs Trễ hạn.

---

### 🆔 US-SP6-08: API Dashboard Tiến độ Học sinh Cá nhân (Student Dashboard API)
* **Actor:** `School Student`
* **Thời gian thực hiện:** 17/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là Học sinh, tôi muốn gọi API Dashboard cá nhân, để xem danh sách bài tập sắp đến hạn nộp, bảng điểm cá nhân và lượng Token AI còn lại của tôi.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/student/dashboard` trả về:
     * Danh sách Bài tập cần hoàn thành xếp theo Hạn nộp gần nhất (`Upcoming Deadlines`).
     * Bảng điểm cá nhân và Hạn mức Token AI còn lại trong ngày.

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
* **Thời gian thực hiện:** 17/10/2026
* **Phụ trách:** 🔵 Dinh (DinhTQ)
* **User Story:**  
  Là System Admin, tôi muốn gọi API Dashboard giám sát hệ thống, để xem chỉ số JVM Health, tổng lưu lượng Token tiêu thụ toàn nền tảng, báo cáo doanh thu PayOS và Top câu hỏi Quiz sai nhiều nhất.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/admin/dashboard` trả về:
     * JVM Health Metrics (CPU usage %, RAM MB, Thread Dump Java Spring Boot).
     * Báo cáo Tổng Token AI tiêu thụ toàn bộ các Trường học và Khách hàng B2C.
     * Top 10 câu hỏi Quiz có tỷ lệ làm sai cao nhất hệ thống.
