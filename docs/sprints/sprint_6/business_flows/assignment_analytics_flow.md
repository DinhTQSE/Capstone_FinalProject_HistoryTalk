# 🔄 SPRINT 6 — BUSINESS FLOWS & ARCHITECTURE
> **Chủ đề:** Local Content Governance & Disclaimer Policy, Assignment & Deadline Engine, 5 Analytics Dashboards  
> **Áp dụng cho:** Sprint 6 (11/10/2026 – 17/10/2026)  

---

## 1. LUỒNG QUẢN LÝ & DUYỆT NỘI DUNG LỊCH SỬ ĐỊA PHƯƠNG (LOCAL CONTENT WORKFLOW)

```mermaid
sequenceDiagram
    autonumber
    actor T as Teacher (Trường A)
    actor ScA as School Admin (Trường A)
    actor St as Học sinh (Trường A)
    participant System as HistoryTalk Backend

    T->>System: 1. POST /teacher/local-contents (Soạn bối cảnh/nhân vật địa phương)
    System-->>T: Tạo bản ghi Local Content (Trạng thái: DRAFT, Gắn school_id)
    
    Note over T,System: Tất cả Giáo viên thuộc Trường A có thể thấy & dùng chung bản ghi DRAFT này

    ScA->>System: 2. PUT /school-admin/local-contents/{id}/status (Duyệt PUBLISHED)
    System-->>ScA: Lưu approved_by_school_admin_id. Nhà trường chịu trách nhiệm nội dung (Policy Disclaimer)

    St->>System: 3. GET /student/assignments (Xem & làm bài tập Địa phương đã duyệt)
    System-->>St: Học sinh tương tác AI với Nhân vật Địa phương của Trường A
```

---

## 2. LUỒNG GIAO BÀI TẬP, THU BÀI VÀ CẬP NHẬT DASHBOARD GIÁO VIÊN

```mermaid
flowchart TD
    A["Giáo viên giao Bài tập cho Lớp: Chọn bài, đặt Deadline"] --> B["Hệ thống tạo bản ghi Assignment_Submission (NOT_STARTED) cho toàn bộ HS"]
    B --> C["Học sinh vào làm bài tập: Chat AI hoặc Làm Quiz bấm giờ"]
    C --> D{"Thời điểm nộp bài <= Deadline?"}
    D -- "Đúng hạn" --> E["Cập nhật Trạng thái COMPLETED + Lưu Điểm/Message Count"]
    D -- "Nộp trễ" --> F["Cập nhật Trạng thái LATE_SUBMITTED + Lưu Điểm/Message Count"]
    E --> G["Tự động cập nhật Bảng điểm & Analytics trên Teacher Dashboard"]
    F --> G
```
