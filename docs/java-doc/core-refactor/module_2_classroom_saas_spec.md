# Tài Liệu Nghiệp Vụ & Thiết Kế - Module 2: Quản Lý Lớp Học (SaaS B2B)

**Dự án:** HistoryTalk - Nền tảng Giáo dục Lịch sử ứng dụng AI & RAG  
**Phân hệ:** Java Backend (`history-talk-backend-Java`)  
**Phiên bản:** 2.0 (Flow-Oriented Specification)  
**Trạng thái:** Chờ Review  
**Tác giả:** Trần Quốc Dinh & Võ Đồng Đức Khải  

---

## 📑 MỤC LỤC TỔNG QUAN
1. [Tổng Quan & Cấu Trúc Phân Quyền SaaS B2B](#1-tổng-quan--cấu-trúc-phân-quyền-saas-b2b)
2. [LUỒNG 1: Khởi Tạo Trường & Cấp Tài Khoản Admin](#2-luồng-1-khởi-tạo-trường--cấp-tài-khoản-admin)
3. [LUỒNG 2: Nhập Danh Sách Giáo Viên, Học Sinh & Tạo Lớp Qua Excel](#3-luồng-2-nhập-danh-sách-giáo-viên-học-sinh--tạo-lớp-qua-excel)
4. [LUỒNG 3: Giáo Viên Giao Bài Tập & Quy Tắc Liêm Chính](#4-luồng-3-giáo-viên-giao-bài-tập--quy-tắc-liêm-chính)
5. [LUỒNG 4: Học Sinh Làm Bài Tập & Ghi Nhận Tiến Độ](#5-luồng-4-học-sinh-làm-bài-tập--ghi-nhận-tiến-độ)
6. [Tổng Hợp Database DDL (Flyway Migration V26)](#6-tổng-hợp-database-ddl-flyway-migration-v26)
7. [Các Điểm Cần Thống Nhất Thêm Với Team](#7-các-điểm-cần-thống-nhất-thêm-với-team)

---

## 1. TỔNG QUAN & CẤU TRÚC PHÂN QUYỀN SAAS B2B

Module 2 cung cấp giải pháp **Multi-tenant SaaS** dành cho các Trường học (THCS, THPT, Trung tâm Giáo dục). Hệ thống phân cấp rõ ràng 4 vai trò:

```
[SYSTEM_ADMIN] (Admin Tổng Hệ Thống)
      └── [SCHOOL] (Trường Học - Mã school_code)
            └── [SCHOOL_ADMIN] (Admin Nhà Trường)
                  ├── [TEACHER] (Giáo Viên) ──► [CLASSROOM] (Lớp Học)
                  └── [SCHOOL_STUDENT] (Học Sinh) ──► Thuộc Lớp Học
```

| Vai trò | Ký hiệu Role | Quyền hạn chính trong Module 2 |
| :--- | :--- | :--- |
| **System Admin** | `SYSTEM_ADMIN` | Khởi tạo Trường học (`School`), cấp mã `school_code`, thời hạn hợp đồng SaaS và tài khoản `SCHOOL_ADMIN`. |
| **School Admin** | `SCHOOL_ADMIN` | Upload Excel danh sách Giáo viên & Học sinh toàn trường; quản lý tài khoản & phân chia Lớp học. |
| **Teacher** | `TEACHER` | Quản lý Lớp học phụ trách; Giao bài tập (`Assignment`); Soạn nội dung/Quiz địa phương (`Local Content`); Xem báo cáo điểm. |
| **School Student** | `SCHOOL_STUDENT` | Đăng nhập tài khoản trường cấp; tự động thấy Lớp & Bài tập; hoàn thành bài tập (Bối cảnh, AI Chat, Quiz) và xem điểm. |

---

## 2. LUỒNG 1: KHỞI TẠO TRƯỜNG & CẤP TÀI KHOẢN ADMIN

### 2.1. Quy Trình Nghiệp Vụ (Workflow)
1. Khi Nhà trường ký hợp đồng SaaS, `SYSTEM_ADMIN` nhập thông tin Trường trên hệ thống qua Form:
   - Tên trường (VD: *THPT Chuyên Lê Hồng Phong*)
   - **Mã định danh trường (`school_code` - Bắt buộc duy nhất):** VD: `LHP`
   - Thông tin người đại diện (`SCHOOL_ADMIN`): Họ tên, Email, Số điện thoại.
   - Ngày hết hạn bản quyền SaaS (`subscription_expires_at`) và Giới hạn tài khoản (`max_teachers`, `max_students`).
2. Hệ thống tự động khởi tạo bản ghi `school` và tài khoản `users` có vai trò `SCHOOL_ADMIN`.
3. Hệ thống gửi Email chứa Mật khẩu ban đầu đến Email người đại diện.

### 2.2. Quy Tắc Định Danh `SCHOOL_ADMIN` (Naming Rules)
* **Username School Admin:** `{school_code}_admin` $\rightarrow$ Ví dụ: `lhp_admin`.

### 2.3. APIs & Tables Liên Quan
* **Table:** `school`, `users`
* **API:** `POST /api/v1/schools` — Tạo trường học mới (`SYSTEM_ADMIN`).

---

## 3. LUỒNG 2: NHẬP DANH SÁCH GIÁO VIÊN, HỌC SINH & TẠO LỚP QUA EXCEL

### 3.1. Quy Trình Nghiệp Vụ (Workflow)
1. `SCHOOL_ADMIN` tải file Excel 2 Sheet theo đúng khuôn mẫu chuẩn của hệ thống lên Portal.
2. Backend kiểm tra tính hợp lệ của file (Pre-validation).
3. Backend tự động xử lý Batch:
   - Tạo các tài khoản `TEACHER` và `SCHOOL_STUDENT`.
   - Tự động tạo các Lớp học (`CLASSROOM`) nếu lớp chưa tồn tại.
   - Tự động xếp Học sinh vào đúng Lớp học tương ứng.
   - Phân công Giáo viên phụ trách Lớp.
4. Backend trả về file Excel Báo cáo Kết quả (chứa danh sách Username & Mật khẩu khởi tạo) để `SCHOOL_ADMIN` gửi cho các lớp. Học sinh **chỉ cần đăng nhập là đã có sẵn Lớp & Bài tập**, không cần nhập Join Code.

### 3.2. Quy Tắc Chống Trùng Tài Khoản 100% (Anti-Collision Rules)
Mọi Username sinh ra từ file Excel đều bắt buộc gắn Prefix `school_code`:
* **Giáo Viên Username:** `{school_code}_gv_{ma_gv}` $\rightarrow$ Ví dụ: `lhp_gv_gv01`
* **Học Sinh Username:** `{school_code}_hs_{ma_hs}` $\rightarrow$ Ví dụ: `lhp_hs_hs192645`
* **Email Dự Phòng:** Nếu học sinh không có email cá nhân, tự sinh Email SaaS: `{username}@saas.historytalk.vn`.
* **Quy tắc Mật khẩu:** Mật khẩu mặc định: `{ma_hs}@2026` hoặc `HistoryTalk@123`. Đánh dấu `is_first_login = true` $\rightarrow$ **Bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên**.

### 3.3. Quy Định Cấu Trúc File Excel Import Mẫu

#### Sheet 1: `Danh_Sach_Giao_Vien`
| Cột | Tên cột Excel | Bắt buộc | Ví dụ | Mô tả |
| :---: | :--- | :---: | :--- | :--- |
| **A** | `Ma_Giao_Vien` | **Có** | `GV01` | Mã GV duy nhất trong trường |
| **B** | `Ho_Va_Ten` | **Có** | `Trần Thị B` | Họ tên giáo viên |
| **C** | `Email` | **Có** | `tranthib@school.edu.vn` | Email nhận tài khoản |

#### Sheet 2: `Danh_Sach_Hoc_Sinh_Va_Lop`
| Cột | Tên cột Excel | Bắt buộc | Ví dụ | Mô tả |
| :---: | :--- | :---: | :--- | :--- |
| **A** | `Ten_Lop` | **Có** | `12A1` | Tên lớp học |
| **B** | `Khoi` | **Có** | `12` | Khối (10, 11, 12) |
| **C** | `Nien_Khoa` | **Có** | `2025-2026` | Niên khóa |
| **D** | `Ma_Hoc_Sinh` | **Có** | `HS192645` | Mã HS duy nhất trong trường |
| **E** | `Ho_Va_Ten` | **Có** | `Nguyễn Minh Trí` | Họ tên học sinh |
| **F** | `Email_Hoc_Sinh` | Không | `tri.nm@gmail.com` | Để trống nếu dùng Email SaaS |
| **G** | `Ma_GV_Phu_Trach` | **Có** | `GV01` | Khớp với `Ma_Giao_Vien` Sheet 1 |

### 3.4. APIs & Tables Liên Quan
* **Tables:** `school`, `users`, `classroom`, `classroom_student`
* **API:** `POST /api/v1/schools/{schoolId}/import` — Upload file Excel import (`SCHOOL_ADMIN`).

---

## 4. LUỒNG 3: GIÁO VIÊN GIAO BÀI TẬP & QUY TẮC LIÊM CHÍNH

### 4.1. Quy Trình Nghiệp Vụ (Workflow)
1. `TEACHER` tạo Bài tập (`Assignment`) cho 1 hoặc nhiều Lớp do mình phụ trách.
2. Cấu hình bài tập bao gồm: Tiêu đề, Hạn nộp (`due_date`), Mô tả hướng dẫn và các **Task thành phần**:
   - **Task 1 - Bối Cảnh (`CONTEXT_READ`):** Chọn sự kiện lịch sử cần đọc (`context_id`).
   - **Task 2 - Chat AI (`CHARACTER_CHAT`):** Chọn Nhân vật lịch sử đàm đạo (`character_id`) và thiết lập số câu chat tối thiểu (`min_messages`).
   - **Task 3 - Trắc Nghiệm (`QUIZ_TEST`):** Chọn bài Quiz (`quiz_id`) và thiết lập điểm đạt (`pass_score`).

### 4.2. Quy Tắc Đảm Bảo Tính Liêm Chính Trong Học Tập (Academic Integrity Rules)
Để tránh tình trạng học sinh gian lận hoặc "chống chế" (bấm xem qua loa hoặc gõ spam câu chat vô nghĩa để tính điểm):

1. **Phân định rõ bản chất từng Task:**
   - **Đọc Bối Cảnh & Chat AI:** Được định vị là **Nhiệm vụ Bổ trợ Tự học & Trải nghiệm**. Giúp học sinh hấp thụ bối cảnh và nguyên nhân lịch sử một cách tự nhiên.
   - **Bài Trắc Nghiệm (Quiz):** Được định vị là **Căn Cứ Chấm Điểm & Đánh Giá Liêm Chính Duy Nhất (Grading Anchor)**. Đây là thước đo bắt buộc để lấy điểm số thực tế.
2. **Bộ lọc chống Spam tin nhắn Chat AI (Spam Filter):**
   - Chỉ đếm tin nhắn hợp lệ ($\ge 10$ ký tự, không lặp lại các chuỗi vô nghĩa như `1`, `2`, `abc`).

### 4.3. Nguyên Tắc Tích Hợp Hệ Thống Quiz Hiện Có (Zero Rewrite Principle)
Để **tận dụng 100% mã nguồn Quiz đã xây dựng** (`com.historytalk.entity.quiz`), không viết lại từ đầu:
* Trong Task Quiz (`assignment_item`), cột `target_id` trỏ trực tiếp đến UUID `quiz_id` của bảng `quiz` có sẵn.
* Tái sử dụng 100% bảng `quiz_session`, `quiz_answer_detail` và các service `QuizService.startQuiz()`, `QuizService.submitQuiz()`.

### 4.4. APIs & Tables Liên Quan
* **Tables:** `assignment`, `assignment_item`, `quiz` (UUID), `historical_context`, `character`
* **APIs:** 
  * `POST /api/v1/assignments` — Tạo và giao bài tập mới (`TEACHER`).
  * `GET /api/v1/assignments/{assignmentId}/report` — Xem báo cáo bảng điểm lớp (`TEACHER`).

---

## 5. LUỒNG 4: HỌC SINH LÀM BÀI TẬP & GHI NHẬN TIẾN ĐỘ

### 5.1. Quy Trình Nghiệp Vụ (Workflow)
1. `SCHOOL_STUDENT` đăng nhập, vào màn hình *"Bài tập của tôi"*:
2. Hệ thống hiển thị danh sách Bài tập kèm Trạng thái (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `LATE_SUBMITTED`).
3. Học sinh thực hiện từng Task:
   - **Làm Task 1 (Đọc bối cảnh):** Bấm xem nội dung $\rightarrow$ Tự động đánh dấu hoàn thành Task 1.
   - **Làm Task 2 (Chat AI):** Trò chuyện với Nhân vật $\rightarrow$ Khi số tin nhắn hợp lệ $\ge min\_messages$, tự động hoàn thành Task 2.
   - **Làm Task 3 (Quiz Test):** Bấm làm Quiz $\rightarrow$ Hệ thống mở phiên `QuizSession` hiện có $\rightarrow$ Học sinh nộp bài $\rightarrow$ Lưu `quiz_score` vào tiến độ Task 3.
4. Khi cả 3 Task hoàn thành $\rightarrow$ Chuyển trạng thái Bài tập sang `COMPLETED`.

### 5.2. Quy Tắc Ghi Nhận Tiến Độ (Progress Rules)
* Nộp bài sau ngày `due_date` $\rightarrow$ Trạng thái tự động ghi nhận là `LATE_SUBMITTED` (Nộp muộn).
* Kết quả bài Quiz lấy trực tiếp từ `QuizSession.score` của lần nộp bài chính thức.

### 5.3. APIs & Tables Liên Quan
* **Tables:** `assignment_submission`, `assignment_item_progress`, `quiz_session`
* **APIs:**
  * `GET /api/v1/students/my-assignments` — Danh sách bài tập của tôi (`SCHOOL_STUDENT`).
  * `POST /api/v1/students/assignments/{assignmentId}/submit` — Cập nhật tiến trình/Nộp bài (`SCHOOL_STUDENT`).

---

## 6. TỔNG HỢP DATABASE DDL (FLYWAY MIGRATION `V26`)

```sql
-- 1. Bảng Trường học
CREATE TABLE school (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code VARCHAR(50) NOT NULL UNIQUE,
    school_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    max_teachers INT DEFAULT 50,
    max_students INT DEFAULT 2000,
    subscription_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 2. Cập nhật bảng users hỗ trợ school_id & first login
ALTER TABLE users 
ADD COLUMN school_id BIGINT NULL,
ADD COLUMN is_first_login BOOLEAN DEFAULT TRUE,
ADD CONSTRAINT fk_user_school FOREIGN KEY (school_id) REFERENCES school(id);

-- 3. Bảng Lớp học
CREATE TABLE classroom (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    grade_level INT NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    join_code VARCHAR(20) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (school_id) REFERENCES school(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id)
);

-- 4. Bảng Học sinh thuộc Lớp (N-N)
CREATE TABLE classroom_student (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_class_student (classroom_id, student_id),
    FOREIGN KEY (classroom_id) REFERENCES classroom(id),
    FOREIGN KEY (student_id) REFERENCES users(id)
);

-- 5. Bảng Bài tập
CREATE TABLE assignment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    due_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (classroom_id) REFERENCES classroom(id),
    FOREIGN KEY (teacher_id) REFERENCES users(id)
);

-- 6. Bảng Chi tiết Nhiệm vụ trong Bài tập
CREATE TABLE assignment_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    item_type VARCHAR(30) NOT NULL,  -- CONTEXT_READ, CHARACTER_CHAT, QUIZ_TEST
    target_id VARCHAR(255) NOT NULL, -- UUID quiz_id hoặc BIGINT context_id/character_id
    min_messages INT DEFAULT 0,       -- Dành cho CHARACTER_CHAT
    pass_score DOUBLE DEFAULT 0.0,    -- Dành cho QUIZ_TEST
    FOREIGN KEY (assignment_id) REFERENCES assignment(id)
);

-- 7. Bảng Tiến trình nộp bài của Học sinh
CREATE TABLE assignment_submission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED', -- NOT_STARTED, IN_PROGRESS, COMPLETED, LATE_SUBMITTED
    overall_score DOUBLE NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_assignment_student (assignment_id, student_id),
    FOREIGN KEY (assignment_id) REFERENCES assignment(id),
    FOREIGN KEY (student_id) REFERENCES users(id)
);

-- 8. Bảng Tiến trình từng Task trong bài tập
CREATE TABLE assignment_item_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    assignment_item_id BIGINT NOT NULL,
    is_completed BOOLEAN DEFAULT FALSE,
    current_messages_count INT DEFAULT 0,
    quiz_session_id VARCHAR(255) NULL, -- Liên kết với quiz_session.session_id hiện có
    quiz_score DOUBLE NULL,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (submission_id) REFERENCES assignment_submission(id),
    FOREIGN KEY (assignment_item_id) REFERENCES assignment_item(id)
);
```

---

## 7. CÁC ĐIỂM CẦN THỐNG NHẤT THÊM VỚI TEAM
1. **Chính sách Quản lý Token Học sinh SaaS:** Option A (Token cố định mỗi ngày/học sinh) vs Option B (Dùng chung quỹ Token của Trường).
2. **Báo cáo Export:** Có cần API xuất file Excel/PDF bảng điểm lớp cho Giáo viên không?
