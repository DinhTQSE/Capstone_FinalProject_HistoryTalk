# 🗺️ MASTER SPRINTS REPOSITORY (HISTORYTALK PROJECT)
> **Dự án:** HistoryTalk — SaaS Historical Education Platform  
> **Thành viên Lập trình Backend:** Trần Quốc Dinh (`DinhTQ`) & Võ Đồng Đức Khải (`KhaiVDD`)  

---

## 📂 CẤU TRÚC TÀI LIỆU CÁC SPRINT (PHÂN THEO NỘI DUNG TASK)

Tất cả các kế hoạch phát triển, User Stories (được đặt tên theo nội dung task), Sơ đồ Luồng Nghiệp vụ (Business Flows) và Tài liệu Kỹ thuật theo từng Sprint được tổ chức thống nhất trong thư mục `docs/sprints/`:

```
docs/sprints/
├── README.md                                                   <-- Master Sprints Index
├── sprint_agent_guide_template.md                              <-- 🤖 Guide & Coding Conventions cho Agent & Devs
├── sprint_0/                                                   <-- Sprint 0 (13/10/2026 – 19/10/2026)
│   ├── README.md                                               <-- Sprint 0 Overview (Dual-Token, Global RBAC & Cloudflare R2)
│   ├── user_stories/
│   │   ├── auth_and_dual_token_security.md                     <-- Task: Dual-Token, RTR & Session Invalidation
│   │   ├── global_rbac_and_account_matrix.md                   <-- Task: 27-Feature RBAC Matrix & Prefix Security
│   │   └── cloudflare_r2_storage_migration.md                  <-- Task: Cloudflare R2 Storage Service, Presigned URLs & Migration
│   └── business_flows/
│       ├── authentication_and_rbac_flows.md                    <-- Flows: Login, RTR & RBAC Authorization
│       └── cloudflare_r2_storage_flows.md                      <-- Flows: Media Upload, Presigned URLs & Storage Routing
├── sprint_5/                                                   <-- Sprint 5 (04/10/2026 – 10/10/2026)
│   ├── README.md                                               <-- Sprint 5 Overview & Roadmap
│   ├── user_stories/
│   │   ├── saas_roles_and_account_management.md                <-- Task: Roles, Accounts & Security
│   │   ├── saas_classroom_management.md                        <-- Task: Classroom & Token Allocation
│   │   └── student_import_template.csv                         <-- File mẫu import học sinh
│   └── business_flows/
│       └── saas_school_business_flow.md                        <-- Flows: Onboarding & Token Allocation
└── sprint_6/                                                   <-- Sprint 6 (11/10/2026 – 17/10/2026)
    ├── README.md                                               <-- Sprint 6 Overview & Roadmap
    ├── user_stories/
    │   ├── class_association_and_assignment_management.md     <-- Task: Class Roster & Assignment Engine
    │   ├── local_history_content_workflow.md                   <-- Task: Local Content & Disclaimer Policy
    │   └── dashboards_and_analytics_apis.md                    <-- Task: 5 Systems & User Dashboards
    └── business_flows/
        └── assignment_analytics_flow.md                        <-- Flows: Local Content & Dashboards
```

---

## 🔗 QUẢN LÝ LỘ TRÌNH CHI TIẾT CỦA TỪNG SPRINT

0. 📂 **[Sprint 0 (13/10/2026 – 19/10/2026)](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/README.md):**
   * **Nội dung:** Nâng cấp Kiến trúc Xác thực Dual-Token (Access Token 15p + Refresh Token Rotation & Reuse Detection), Xóa bỏ Blacklist ảo, Chuẩn hóa Ma trận Phân quyền RBAC 27 tính năng, và Chuyển đổi Toàn diện Hệ thống Lưu trữ tệp sang Cloudflare R2 (AWS S3 SDK v2, Zero Egress Fees, CDN Caching, Instant Rollback).
   * **Tài liệu Tasks:**
     * 📄 [auth_and_dual_token_security.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/user_stories/auth_and_dual_token_security.md)
     * 📄 [global_rbac_and_account_matrix.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/user_stories/global_rbac_and_account_matrix.md)
     * 📄 [cloudflare_r2_storage_migration.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/user_stories/cloudflare_r2_storage_migration.md)
     * 🔄 [authentication_and_rbac_flows.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/business_flows/authentication_and_rbac_flows.md)
     * 🔄 [cloudflare_r2_storage_flows.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/business_flows/cloudflare_r2_storage_flows.md)

1. 📂 **[Sprint 5 (04/10/2026 – 10/10/2026)](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/README.md):**
   * **Nội dung:** Fundamental SaaS Architecture, Security RBAC, School Onboarding, Enterprise Token Allocation Engine, Classroom Management & Class Code Generation.
   * **Tài liệu Tasks:**
     * 📄 [saas_roles_and_account_management.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/saas_roles_and_account_management.md)
     * 📄 [saas_classroom_management.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/saas_classroom_management.md)
     * 🔄 [saas_school_business_flow.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/business_flows/saas_school_business_flow.md)

2. 📂 **[Sprint 6 (11/10/2026 – 17/10/2026)](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/README.md):**
   * **Nội dung:** Class Roster Association, Quy trình Lịch sử Địa phương (Teacher soạn/share, School Admin duyệt, Disclaimer Policy), Engine Giao bài tập đa nhiệm kèm Deadline, 5 Systems & User Dashboards APIs.
   * **Tài liệu Tasks:**
     * 📄 [class_association_and_assignment_management.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/user_stories/class_association_and_assignment_management.md)
     * 📄 [local_history_content_workflow.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/user_stories/local_history_content_workflow.md)
     * 📄 [dashboards_and_analytics_apis.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/user_stories/dashboards_and_analytics_apis.md)
     * 🔄 [assignment_analytics_flow.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/business_flows/assignment_analytics_flow.md)
