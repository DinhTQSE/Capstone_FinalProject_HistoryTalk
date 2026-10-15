# 🏆 SPRINT 7 OVERVIEW & DOCUMENTATION (16/10/2026 – 30/10/2026)
> **Mục tiêu chính:** Nâng cấp toàn bộ module Quiz — thống nhất pipeline `question_data` cho mọi loại câu hỏi (Multiple Choice, Fill-in-Blank, Matching), bổ sung Question Bank độc lập, và hoàn thiện luồng làm bài cho learner.  
> **Thành viên phụ trách:** 🟢 Bao (Bao-2005)

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

### 2. 📂 Business Flows (Luồng Nghiệp vụ):
* 🔄 **[quiz_module_flows.md](business_flows/quiz_module_flows.md):** Sequence Diagram & Flowchart cho luồng làm bài, luồng quản lý nội dung, và luồng Question Bank.
