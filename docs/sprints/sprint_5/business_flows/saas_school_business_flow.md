# 🔄 SPRINT 5 — BUSINESS FLOWS & ARCHITECTURE
> **Chủ đề:** SaaS School Onboarding, RBAC Phân quyền, Token Allocation Engine & Classroom Management  
> **Áp dụng cho:** Sprint 5 (04/10/2026 – 10/10/2026)  

---

## 1. LUỒNG NGHIỆP VỤ ONBOARDING TRƯỜNG HỌC & TOKEN ALLOCATION

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

## 2. LUỒNG QUẢN LÝ LỚP HỌC & SINH MÃ CLASS CODE

```mermaid
flowchart TD
    A["Bắt đầu: Giáo viên / School Admin"] --> B["Tạo Lớp học mới: Tên lớp, Năm học, Teacher ID"]
    B --> C["Hệ thống tự động sinh Class Code ngẫu nhiên (ví dụ: HIS10A1-2026)"]
    C --> D{"Kiểm tra trùng Tên lớp trong cùng Trường?"}
    D -- "Có trùng" --> E["Trả lỗi 400 Bad Request: Class name exists"]
    D -- "Không trùng" --> F["Lưu bản ghi Classroom thành công"]
    F --> G["Hiển thị Lớp trong Dashboard Giáo viên & School Admin"]
```
