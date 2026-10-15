# 📋 USER STORIES: LOCAL HISTORY CONTENT WORKFLOW & DISCLAIMER POLICY
> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Phân hệ:** Quản lý Nội dung Lịch sử Địa phương, Cơ chế Chia sẻ Nội bộ & Phê duyệt  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP6-02`** | Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương | `Teacher` | 🟢 Khải (KhaiVDD) | `POST /api/v1/teacher/local-contents`<br>`GET /api/v1/teacher/local-contents`<br>`GET /api/v1/teacher/local-contents/{id}` |
| **`US-SP6-03`** | School Admin Xem & Duyệt Local Content (Disclaimer Policy) | `School Admin` | 🟢 Khải (KhaiVDD) | `GET /api/v1/school-admin/local-contents`<br>`GET /api/v1/school-admin/local-contents/{id}`<br>`PUT /api/v1/school-admin/local-contents/{id}/status` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP6-02: Giáo viên Tạo & Chia sẻ Nội dung Lịch sử Địa phương Nội bộ Trường (Local Content Sharing)
* **Actor:** `Teacher`
* **Thời gian thực hiện:** 12/10/2026 – 13/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là Giáo viên, tôi muốn tạo mới Bối cảnh, Nhân vật hoặc Quiz mang tính chất Lịch sử Địa phương và tự động chia sẻ nội bộ với các Giáo viên khác trong trường, để phục vụ việc giao bài tập đặc thù của nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/teacher/local-contents` tạo nội dung địa phương gắn liền với `school_id` và `created_by_teacher_id`. Mặc định trạng thái là `DRAFT`.
  2. API `GET /api/v1/teacher/local-contents` cho phép Giáo viên lấy danh sách nội dung địa phương thuộc cùng trường (hỗ trợ phân trang, lọc trạng thái `DRAFT` / `PUBLISHED` / `INACTIVE`, tìm kiếm từ khóa).
  3. API `GET /api/v1/teacher/local-contents/{id}` cho phép Giáo viên xem chi tiết nội dung địa phương để tham khảo và dùng giao bài tập.
  4. Tất cả Giáo viên thuộc **cùng `school_id` đó** đều xem và dùng chung được nội dung địa phương này để tham khảo và giao bài tập.
  5. Người dùng thuộc trường khác hoặc B2C Customer **tuyệt đối không thể truy cập** được nội dung địa phương này (chặn bảo mật cấp `school_id` - `403 Forbidden`).

---

### 🆔 US-SP6-03: School Admin Xem Thẩm Định & Duyệt Local Content (Policy Disclaimer Enforcement)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 13/10/2026 – 14/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn xem danh sách và xem chi tiết toàn bộ nội dung Lịch sử Địa phương do Giáo viên trường mình soạn thảo để thẩm định nội dung, và phê duyệt (`PUBLISHED`) hoặc từ chối/yêu cầu sửa đổi (`DRAFT`/`INACTIVE`), đảm bảo tuân thủ Policy nhà trường trước khi cho học sinh tiếp cận.
* **Acceptance Criteria (AC):**
  1. **API Get All:** `GET /api/v1/school-admin/local-contents`
     * Lấy danh sách nội dung lịch sử địa phương thuộc trường của School Admin đăng nhập.
     * Hỗ trợ bộ lọc: `status` (`DRAFT`, `PUBLISHED`, `INACTIVE`), `teacherId`, từ khóa tìm kiếm (`keyword`), phân trang (`page`, `size`, `sort`).
  2. **API Get Detail:** `GET /api/v1/school-admin/local-contents/{id}`
     * Trả về toàn bộ chi tiết nội dung (thông tin bối cảnh/nhân vật, tài liệu tham khảo, bộ quiz đi kèm, thông tin giáo viên tạo, lịch sử trạng thái) để School Admin thẩm định trước khi quyết định duyệt.
  3. **API Update Status:** `PUT /api/v1/school-admin/local-contents/{id}/status`
     * Cho phép chuyển trạng thái giữa `PUBLISHED`, `DRAFT`, và `INACTIVE` (có thể kèm ghi chú nhận xét `reviewNote` để phản hồi cho giáo viên).
     * Khi duyệt thành công, hệ thống ghi nhận `approved_by_school_admin_id` và `approved_at` phục vụ kiểm toán đối soát nội bộ.
  4. **Phân quyền & Bảo mật (Tenant Isolation):**
     * School Admin chỉ có quyền xem và duyệt nội dung có `school_id` trùng với trường của mình. Truy cập nội dung trường khác sẽ bị trả mã lỗi `403 Forbidden`.
  5. **Disclaimer Policy:**
     * Theo Điều khoản dịch vụ (Policy), bên cung cấp ứng dụng HistoryTalk hoàn toàn **miễn trừ trách nhiệm pháp lý** đối với tính xác thực của các nội dung Lịch sử Địa phương do nhà trường tự biên soạn và đăng tải.
