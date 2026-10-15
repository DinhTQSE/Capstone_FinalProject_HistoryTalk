# 🏆 SPRINT 6 OVERVIEW & DOCUMENTATION (11/10/2026 – 17/10/2026)
> **Mục tiêu chính:** Class Student Association, Local History Content Workflow & Policy Disclaimer, Assignment & Deadline Engine, 5 System Dashboards APIs.  
> **Lập trình viên phụ trách:** Trần Quốc Dinh (`DinhTQ`) & Võ Đồng Đức Khải (`KhaiVDD`)

---

## 📊 TIẾN ĐỘ

| User Story | Phụ trách | Trạng thái |
| :--- | :--- | :--- |
| `US-SP6-01` Class Roster Association | Khải | ✅ Done (Sprint 5) |
| `US-SP6-02` Local Content — Teacher Create & Share | Khải | ✅ Done 14/10 |
| `US-SP6-03` Local Content — School Admin Approve | Khải | ✅ Done 14/10 |
| `UC-14` Local Content — Student View | Khải | ✅ Done 14/10 |
| `US-SP6-04` Giao Bài tập cho Lớp kèm Deadline | Định | 🔄 In Progress |
| `US-SP6-05` Học sinh Nộp bài & Đánh giá Tiến độ | Định | 🔄 In Progress |
| `US-SP6-06~10` 5 System Dashboards APIs | Định | 🔄 In Progress |

---

## 📂 THƯ MỤC CẤU TRÚC TÀI LIỆU SPRINT 6

### 1. 📂 User Stories (Theo Nội dung Task):
* 📄 **[class_association_and_assignment_management.md](user_stories/class_association_and_assignment_management.md):**
  * `US-SP6-01` (Khải): Gán & Chuyển Học sinh vào Lớp học (Class Roster Association).
  * `US-SP6-04` (Định): Giáo viên Giao Bài tập Đa nhiệm cho Lớp kèm Deadline.
  * `US-SP6-05` (Định): Học sinh Nộp bài & Tự động Đánh giá Tiến độ.
* 📄 **[local_history_content_workflow.md](user_stories/local_history_content_workflow.md):** ✅ Updated 14/10
  * `US-SP6-02` (Khải): Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương (CLASS_ONLY / SCHOOL_WIDE scope).
  * `US-SP6-03` (Khải): School Admin Duyệt Local Content & Policy Disclaimer Enforcement.
  * `UC-14` (Khải): Học sinh Xem Nội dung Địa phương đã Duyệt.
* 📄 **[dashboards_and_analytics_apis.md](user_stories/dashboards_and_analytics_apis.md):**
  * `US-SP6-06` (Định): API Dashboard Báo cáo Trường học (School Admin).
  * `US-SP6-07` (Định): API Dashboard Giáo viên & Analytics Lớp học.
  * `US-SP6-08` (Định): API Dashboard Tiến độ Học sinh Cá nhân.
  * `US-SP6-09` (Định): API Dashboard Khách hàng B2C Cá nhân.
  * `US-SP6-10` (Định): API Dashboard Admin Hệ thống & Sức khỏe JVM.

### 2. 📂 Business Flows (Luồng Nghiệp vụ):
* 🔄 **[assignment_analytics_flow.md](business_flows/assignment_analytics_flow.md):** ✅ Updated 14/10 — Sequence diagrams & Flowcharts cho Local Content Workflow (CLASS_ONLY / SCHOOL_WIDE / Approval), DB Schema V32, Isolation Rules, Assignment Engine.
* 🔄 **[assignment_end_to_end_test_flows.md](business_flows/assignment_end_to_end_test_flows.md):** ✅ Hướng dẫn nghiệp vụ chi tiết & chuỗi API thực thi End-to-End cho Developers (6 flows: Tạo bài tập, AI Chat, Quiz, Đọc bối cảnh, Gradebook, Anti-cheat).

### 3. 📂 Implemented Plans (Ghi chép triển khai):
* 📋 **[local_content_implementation_plan.md](implemented_plan/local_content_implementation_plan.md):** ✅ 14/10 — Design decisions, danh sách files tạo/sửa, business rules, scope logic, status lifecycle, known constraints.
* 📋 **[class_roster_association_and_student_import_plan.md](implemented_plan/class_roster_association_and_student_import_plan.md):** Kế hoạch chi tiết triển khai Gán & Chuyển Học sinh vào Lớp học (`US-SP6-01`), tích hợp tái sử dụng Import Engine từ Sprint 5.
* 📋 **[assignment_management_and_submission_plan.md](implemented_plan/assignment_management_and_submission_plan.md):** Kế hoạch chi tiết triển khai Giao Bài tập Đa nhiệm & Tự động Đánh giá Nộp bài (`US-SP6-04`, `US-SP6-05`), quản lý vòng đời bài nộp và xử lý Edge Cases.

### 4. 📦 FE Contract (API Contract cho Frontend):
* 📄 **[LocalContent_APIContract.md](../../FE-contract/LocalContent_APIContract.md):** ✅ 14/10 — Full API contract với request/response schema cho Teacher, School Admin, Student endpoints (16 endpoints).
* 📑 **[class_roster_association_and_student_import_api_contract.md](fe_contracts/class_roster_association_and_student_import_api_contract.md):** Hợp đồng API chi tiết, TypeScript types, payloads, mã lỗi và hướng dẫn tích hợp màn hình Roster lớp học cho Frontend.
* 📑 **[assignment_management_and_submission_api_contract.md](fe_contracts/assignment_management_and_submission_api_contract.md):** Hợp đồng API chi tiết cho Quản lý Bài tập Giáo viên (`US-SP6-04`) và Nộp bài Tự động Chấm điểm Server-side (`US-SP6-05`).

