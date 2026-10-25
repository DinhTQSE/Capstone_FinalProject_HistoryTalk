# 🏆 SPRINT 7 OVERVIEW & DOCUMENTATION (16/10/2026 – 30/10/2026)
> **Mục tiêu chính:**  
> 1. Nâng cấp toàn bộ module Quiz — thống nhất pipeline `question_data` cho mọi loại câu hỏi (Multiple Choice, Fill-in-Blank, Matching), bổ sung Question Bank độc lập, và hoàn thiện luồng làm bài cho learner.  
> 2. Chuẩn hóa Vòng đời Gói B2B Trường học theo Năm học (`01/09` – `31/05`), Cơ chế Quỹ Token Chung Pay-As-You-Go + Hạn mức Ngày (Học sinh `1.000/ngày`, Giáo viên `10.000/ngày`), Luồng Giáo viên Cấp trực tiếp Token từ hạn mức của mình cho Học sinh + Tạo Ticket lên School Admin khi Giáo viên hết Token (cả 3 role đều xem được Ticket), API Upload PDF Tài liệu Lịch sử Địa phương cho Giáo viên, và Quản lý Vòng đời Học sinh qua các Năm học (`ARCHIVED` / `DEACTIVATED`).  
> **Thành viên phụ trách:** 🟢 Bao (`Bao-2005`) & 🔵 Trần Quốc Dinh (`DinhTQ`)

---

## 📂 THƯ MỤC CẤU TRÚC TÀI LIỆU SPRINT 7

### 1. 📂 User Stories (Theo Nội dung Task):
* 📄 **[quiz_customer_flow.md](user_stories/quiz_customer_flow.md):**
  * `US-SP7-01` (Bao): Duyệt danh sách Quiz
  * `US-SP7-02` (Bao): Bắt đầu phiên làm bài
  * `US-SP7-03` (Bao): Nộp bài và nhận kết quả
  * `US-SP7-04` (Bao): Xem lịch sử làm bài
  * `US-SP7-05` (Bao): Xem chi tiết kết quả phiên làm bài
  * `US-SP7-06` (Bao): Đánh giá sao cho Quiz
  * `US-SP7-07` (Bao): Báo cáo câu hỏi lỗi

* 📄 **[quiz_staff_management.md](user_stories/quiz_staff_management.md):**
  * `US-SP7-08` (Bao): Quản lý Quiz (CRUD + Import CSV)
  * `US-SP7-09` (Bao): Quản lý câu hỏi trong Quiz
  * `US-SP7-10` (Bao): Xem lịch sử phiên làm bài (Admin)
  * `US-SP7-11` (Bao): Xử lý báo cáo câu hỏi

* 📄 **[question_bank.md](user_stories/question_bank.md):**
  * `US-SP7-12` (Bao): Quản lý Question Bank độc lập
  * `US-SP7-13` (Bao): Gắn / Gỡ câu hỏi từ Bank vào Quiz

* 📄 **[quiz_fe_contracts.md](user_stories/quiz_fe_contracts.md):** Đặc tả đầy đủ request/response cho Frontend

* 📄 **[school_subscription_daily_token_and_lifecycle.md](user_stories/school_subscription_daily_token_and_lifecycle.md):**
  * `US-SP7-14` (Dinh): Chu kỳ Gói Trường học (`01/09` – `31/05`), Gia hạn Tháng 5, Top-up & Cơ chế Khóa phân tầng (Hết hạn gói $\rightarrow$ Khóa toàn bộ nền tảng; Hết Token $\rightarrow$ Chỉ khóa AI)
  * `US-SP7-15` (Dinh): Quỹ Token Chung Pay-As-You-Go (Không trừ cứng lúc Import học sinh) & Hạn mức Ngày cho Học sinh (`1.000/ngày`) và Giáo viên (`10.000/ngày`)
  * `US-SP7-16` (Dinh): Cấu hình Định mức Token Ngày (Học sinh & Giáo viên) & Bảng Ước tính Gói Enterprise
  * `US-SP7-17` (Dinh): Giáo viên Cấp Token của mình trực tiếp cho Học sinh + Tạo Ticket lên School Admin khi Giáo viên hết Token (Cả 3 Role `STUDENT`, `TEACHER`, `SCHOOL_ADMIN` đều xem được Ticket)
  * `US-SP7-18` (Dinh): Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Khóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`)
  * `US-SP7-19` (Dinh): Giáo viên Upload Tài liệu PDF cho Bối cảnh & Nhân vật Lịch sử Địa phương (Local Content)
  * `US-SP7-20` (Dinh): Sửa lỗi `NullPointerException` tại School Admin Dashboard & lỗi 500 tại API Danh sách Lớp học (`classrooms`)

### 2. 📂 Business Flows (Luồng Nghiệp vụ):
* 🔄 **[quiz_module_flows.md](business_flows/quiz_module_flows.md):** Sequence Diagram & Flowchart cho luồng làm bài, luồng quản lý nội dung, và luồng Question Bank.
* 🔄 **[school_subscription_daily_token_and_lifecycle_flow.md](business_flows/school_subscription_daily_token_and_lifecycle_flow.md):** State Diagram, Flowchart & Sequence Diagram cho Chu kỳ Gói Năm học (`01/09` – `31/05`), Pure Shared Pool Pay-As-You-Go khi Import & Chat AI, Luồng Giáo viên Cấp Token cho Học sinh & Escalate Ticket lên School Admin, Upload PDF Local Content, và Chuyển giao Năm học (`ARCHIVED` / `DEACTIVATED`).

### 3. 📂 Implemented Plans (Kế hoạch Triển khai Chi tiết):
* 📋 **[school_subscription_daily_token_and_lifecycle_plan.md](implemented_plan/school_subscription_daily_token_and_lifecycle_plan.md):** Kế hoạch chi tiết triển khai Flyway Migrations (`V38`, `V39`, `V40`), Pure Shared Pool Pay-As-You-Go, Hạn mức Giáo viên & Cấp Token trực tiếp / Ticket Admin, Upload PDF cho Teacher, và Sửa lỗi Dashboard/Classrooms (`US-SP7-14` ~ `US-SP7-20`).
* 📋 **[prometheus_grafana_full_system_monitoring_plan.md](implemented_plan/prometheus_grafana_full_system_monitoring_plan.md):** Kế hoạch chi tiết triển khai giám sát toàn hệ thống (Full-System Observability) với Prometheus, Grafana (10 Panels), Spring Boot Actuator Micrometer (bao gồm SSE Streaming & RAG Document Metrics), và FastAPI Prometheus Exporter.

### 4. 📦 FE Contracts (API Contract cho Frontend):
* 📑 **[school_subscription_daily_token_and_lifecycle_api_contract.md](fe_contracts/school_subscription_daily_token_and_lifecycle_api_contract.md):** Đặc tả chi tiết các REST API endpoints cho Chu kỳ Gói Trường học, Cấu hình Hạn mức Ngày (Học sinh & Giáo viên), Luồng Cấp Token & Ticket cho cả 3 Role, API Upload PDF của Giáo viên, và Chuyển giao Năm học.
