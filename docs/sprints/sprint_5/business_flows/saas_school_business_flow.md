# 🔄 SPRINT 5 — BUSINESS FLOWS & ARCHITECTURE
> **Chủ đề:** SaaS School Onboarding, RBAC Phân quyền, Token Allocation Engine & Classroom Management  
> **Áp dụng cho:** Sprint 5 (04/10/2026 – 10/10/2026)  

---

## 📑 TÀI LIỆU LUỒNG NGHIỆP VỤ CHI TIẾT

👉 **Xem tài liệu chi tiết từng bước (Step-by-step & Sequence Diagrams):**  
📄 **[saas_classroom_and_token_allocation_business_flow.md](file:///c:/Users/trand/OneDrive/Documents/Semester%207/SWD392/SWD392_FinalProject_Git/docs/sprints/sprint_5/business_flows/saas_classroom_and_token_allocation_business_flow.md)**

---

## 1. TỔNG QUAN LUỒNG ONBOARDING TRƯỜNG HỌC & TOKEN ALLOCATION

```mermaid
sequenceDiagram
    autonumber
    actor SA as System Admin
    actor ScA as School Admin
    actor T as Teacher
    actor St as School Student
    participant System as HistoryTalk Backend

    SA->>System: 1. POST /admin/schools (Tạo Trường & Gán gói Enterprise Token)
    System-->>SA: Trả về school_id & total_school_token_quota
    
    SA->>System: 2. POST /admin/school-admins (Cấp tài khoản School Admin)
    System-->>ScA: Gửi Email kích hoạt & Mật khẩu ban đầu
    
    ScA->>System: 3. POST /school-admin/teachers (Tạo tài khoản Giáo viên)
    ScA->>System: 4. POST /school-admin/students/import-excel (Import học sinh toàn trường)
    
    ScA->>System: 5. PUT /school-admin/students/token-allocation (Phân bổ Token riêng cho từng Học sinh)
    Note over System: Validate: Sum(allocated_tokens) <= total_school_token_quota
    System-->>ScA: Cập nhật unallocated_token_quota còn lại
```

---

## 2. TỔNG QUAN LUỒNG QUẢN LÝ LỚP HỌC & SINH MÃ CLASS CODE THÔNG MINH

```mermaid
flowchart TD
    A["Bắt đầu: Giáo viên / School Admin"] --> B["Tạo Lớp học mới: Tên lớp, Năm học, Teacher ID"]
    B --> C["Hệ thống chuẩn hóa tên lớp & ghép mã trường: {school_code}-{clean_class_name}-{academic_year}"]
    C --> D{"Kiểm tra trùng Tên lớp trong cùng Trường & Năm học?"}
    D -- "Có trùng" --> E["Trả lỗi 400 Bad Request: Class name exists in academic year"]
    D -- "Không trùng" --> F{"Mã Class Code có bị va chạm?"}
    F -- "Có va chạm" --> G["Tự động thêm hậu tố ngẫu nhiên 3 ký tự (VD: CVA-10A1-2026-X89)"]
    F -- "Không va chạm" --> H["Giữ nguyên mã chuẩn: CVA-10A1-2026"]
    G --> I["Lưu bản ghi Classroom thành công"]
    H --> I
    I --> J["Gán Teacher phụ trách & cho phép học sinh tham gia"]
```
