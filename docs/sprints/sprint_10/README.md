# Sprint 10 Documentation

Tài liệu tổng hợp các thay đổi Backend trong Sprint 10 nhằm xử lý toàn bộ các lỗi System Admin Dashboard từ quá trình System Testing (`WF05 + Dashboard`) và **đồng bộ cấu trúc Dashboard sang School Admin Dashboard** (fit theo B2B và phạm vi từng trường học).

## Danh sách tài liệu

1. **FE Contracts & Hướng dẫn tích hợp cho FE (`fe_contracts/`)**:
   - [system_dashboard_api_contract.md](./fe_contracts/system_dashboard_api_contract.md) — API Contract đầy đủ cho cụm `/api/v1/system-admin/dashboard/*`.
   - [school_admin_dashboard_api_contract.md](./fe_contracts/school_admin_dashboard_api_contract.md) — API Contract đầy đủ cho cụm `/api/v1/school-admin/dashboard/*` (bê cấu trúc từ System Admin qua School Admin, chỉ lấy dữ liệu Local của trường & thay Doanh thu bằng Quản lý Quỹ Token của trường).
   - [system_dashboard_fe_migration_guide.md](./fe_contracts/system_dashboard_fe_migration_guide.md) — Chi tiết từng bug đã sửa ở BE, những field/param thay đổi và **checklist những chỗ FE cần sửa/cập nhật lại cho cả System Admin và School Admin**.
2. **Business Flows (`business_flows/`)**:
   - [system_dashboard_b2b_b2c_separation_flow.md](./business_flows/system_dashboard_b2b_b2c_separation_flow.md) — Business flow và quy tắc lọc/tổng hợp dữ liệu B2B vs B2C, Global vs Local, soft-delete, active session 2 phút và đối xứng giữa System Admin vs School Admin.
