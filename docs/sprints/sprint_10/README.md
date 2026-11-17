# Sprint 10 Documentation

Tài liệu tổng hợp các thay đổi Backend trong Sprint 10 nhằm xử lý toàn bộ các lỗi System Admin Dashboard từ quá trình System Testing (`WF05 + Dashboard`).

## Danh sách tài liệu

1. **FE Contracts & Hướng dẫn tích hợp cho FE (`fe_contracts/`)**:
   - [system_dashboard_api_contract.md](./fe_contracts/system_dashboard_api_contract.md) — API Contract đầy đủ cho cụm `/api/v1/system-admin/dashboard/*`.
   - [system_dashboard_fe_migration_guide.md](./fe_contracts/system_dashboard_fe_migration_guide.md) — Chi tiết từng bug đã sửa ở BE, những field/param thay đổi và **checklist những chỗ FE cần sửa/cập nhật lại**.
2. **Business Flows (`business_flows/`)**:
   - [system_dashboard_b2b_b2c_separation_flow.md](./business_flows/system_dashboard_b2b_b2c_separation_flow.md) — Business flow và quy tắc lọc/tổng hợp dữ liệu B2B vs B2C, Global vs Local, soft-delete và active session 2 phút.
