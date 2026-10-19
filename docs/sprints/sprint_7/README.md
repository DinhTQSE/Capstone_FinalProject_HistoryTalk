# 🏆 SPRINT 7 OVERVIEW & DOCUMENTATION (16/10/2026 – 30/10/2026)
> **Mục tiêu chính:**  
> 1. Nâng cấp toàn bộ module Quiz — thống nhất pipeline `question_data` cho mọi loại câu hỏi (Multiple Choice, Fill-in-Blank, Matching), bổ sung Question Bank độc lập, và hoàn thiện luồng làm bài cho learner.  
> 2. Chuẩn hóa Vòng đời Gói B2B Trường học theo Năm học (`01/09` – `31/05`), Cơ chế Quỹ Token Chung + Hạn mức Ngày (Daily Allowance), Luồng Xin/Duyệt thêm Token trong ngày và Quản lý Vòng đời Học sinh qua các Năm học (`ARCHIVED` / `DEACTIVATED`).  
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
  * `US-SP7-15` (Dinh): Quỹ Token Chung (Shared Pool) & Tự động Cấp Hạn mức Ngày (Daily Token Limit)
  * `US-SP7-16` (Dinh): Cấu hình Định mức Token Ngày & Bảng Ước tính Gói Enterprise
  * `US-SP7-17` (Dinh): Học sinh Xin thêm Token trong ngày & Giáo viên / School Admin Duyệt cấp nhanh
  * `US-SP7-18` (Dinh): Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Khóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`)

### 2. 📂 Business Flows (Luồng Nghiệp vụ):
* 🔄 **[quiz_module_flows.md](business_flows/quiz_module_flows.md):** Sequence Diagram & Flowchart cho luồng làm bài, luồng quản lý nội dung, và luồng Question Bank.
* 🔄 **[school_subscription_daily_token_and_lifecycle_flow.md](business_flows/school_subscription_daily_token_and_lifecycle_flow.md):** State Diagram, Flowchart & Sequence Diagram cho Chu kỳ Gói Năm học (`01/09` – `31/05`), Lazy Daily Token Allowance, Luồng Xin/Duyệt Token trong ngày và Chuyển giao Năm học (`ARCHIVED` / `DEACTIVATED`).

### 3. 📂 Implemented Plans (Kế hoạch Triển khai Chi tiết):
* 📋 **[school_subscription_daily_token_and_lifecycle_plan.md](implemented_plan/school_subscription_daily_token_and_lifecycle_plan.md):** Kế hoạch 6 Phase triển khai Flyway Migration `V38`, Quỹ Token Chung theo Ngày, Khóa phân tầng Gói vs AI và Quản lý Vòng đời Học sinh (`US-SP7-14` ~ `US-SP7-18`).

### 4. 📦 FE Contracts (API Contract cho Frontend):
* 📑 **[school_subscription_daily_token_and_lifecycle_api_contract.md](fe_contracts/school_subscription_daily_token_and_lifecycle_api_contract.md):** Đặc tả chi tiết 10 REST API endpoints cho Chu kỳ Gói Trường học (`01/09` – `31/05`), Cấu hình Hạn mức Ngày, Xin/Duyệt Token trong ngày và Chuyển giao Năm học (`ARCHIVED` / `DEACTIVATED`).

