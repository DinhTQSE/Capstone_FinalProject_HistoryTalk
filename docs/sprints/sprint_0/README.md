# 🏆 SPRINT 0 OVERVIEW & DOCUMENTATION (13/10/2026 – 19/10/2026)
> **Mục tiêu chính:** Nâng cấp toàn diện kiến trúc Bảo mật, Xác thực Dual-Token (Access Token 15p + Refresh Token Rotation & Reuse Detection), và Chuẩn hóa Ma trận Phân quyền (RBAC Matrix) 6 vai trò trên toàn bộ hệ thống HistoryTalk.  
> **Thành viên phụ trách:** Võ Đồng Đức Khải (`KhaiVDD`)  

---

## 📂 THƯ MỤC CẤU TRÚC TÀI LIỆU SPRINT 0

### 1. 📂 User Stories (Theo Nội dung Task):
* 📄 **[auth_and_dual_token_security.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/user_stories/auth_and_dual_token_security.md):**
  * `US-SP0-01` (🟢 Khải): Sửa lỗi JJWT 0.12 Claims Overwrite & Chuẩn hóa Access Token Payload (`sub`, `uid`, `role`, `iat`, `exp`).
  * `US-SP0-02` (🟢 Khải): Triển khai Dual-Token Architecture với Refresh Token Rotation (RTR) & Tự động phát hiện xâm phạm (Automatic Reuse Detection).
  * `US-SP0-03` (🟢 Khải): Cơ chế Đăng xuất thực tế (Database Revocation) & Vô hiệu hóa phiên tức thì khi Đổi mật khẩu / Khóa tài khoản (`password_changed_at`).
* 📄 **[global_rbac_and_account_matrix.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/user_stories/global_rbac_and_account_matrix.md):**
  * `US-SP0-04` (🟢 Khải): Chuẩn hóa Ma trận phân quyền 27 tính năng (RBAC Foundation) cho 6 vai trò người dùng.
  * `US-SP0-05` (🟢 Khải): Triển khai Bộ lọc Phân quyền Controller & Prefix Endpoint Security (`@PreAuthorize` + `SecurityConfig`).

### 2. 📂 Business Flows (Luồng Nghiệp vụ):
* 🔄 **[authentication_and_rbac_flows.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_0/business_flows/authentication_and_rbac_flows.md):** Sơ đồ Sequence & Flowchart cho Luồng Đăng nhập, Silent Refresh Xoay vòng Token, Báo động đỏ Reuse Detection và Kiểm tra Quyền RBAC.

---

## 🎯 DANH SÁCH 6 VAI TRÒ CHUẨN TRONG HỆ THỐNG
1. **`SYSTEM_ADMIN`**: Quản trị viên tối cao của nền tảng, quản lý trường học, tài khoản quản trị, hệ thống và thanh toán.
2. **`CONTENT_ADMIN`**: Quản trị viên học thuật, kiểm duyệt nội dung lịch sử, tài liệu và câu hỏi dùng chung toàn quốc.
3. **`SCHOOL_ADMIN`**: Quản trị viên cấp trường (B2B SaaS), quản lý giáo viên, lớp học, học sinh và duyệt nội dung địa phương của trường.
4. **`TEACHER`**: Giáo viên, quản lý học sinh trong lớp được giao, soạn nội dung lịch sử địa phương, tạo và giao bài tập.
5. **`SCHOOL_STUDENT`**: Học sinh thuộc trường học, tham gia lớp học, làm bài tập, luyện thi và trò chuyện với AI theo quota trường.
6. **`CUSTOMER`**: Người dùng cá nhân B2C (Free / Plus / Pro), mua gói qua PayOS, tự học và luyện tập độc lập.
