# 📋 USER STORIES: SCHOOL SUBSCRIPTION, DAILY TOKEN POOL & ACADEMIC YEAR LIFECYCLE
> **Sprint:** Sprint 7 (18/10/2026 – 30/10/2026)  
> **Phân hệ:** Quản trị Gói B2B Trường học theo Năm học, Quỹ Token Chung theo Ngày (Daily Allowance) & Vòng đời Học sinh qua các Năm học  
> **Lập trình viên phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP7-14`** | Chu kỳ Gói Trường học (01/09 – 31/05), Gia hạn Tháng 5, Top-up & Khóa phân tầng | `System Admin`, `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/admin/schools/{id}/renew`<br>`POST /api/v1/admin/schools/{id}/top-up` |
| **`US-SP7-15`** | Quỹ Token Chung (Shared Pool) & Tự động Cấp Hạn mức Ngày (Daily Token Limit) | `System`, `School Student` | 🔵 Dinh (`DinhTQ`) | Internal Interceptor / Lazy Daily Reset & AI Chat Guard |
| **`US-SP7-16`** | Cấu hình Định mức Token Ngày & Bảng Ước tính Gói Enterprise | `School Admin` | 🔵 Dinh (`DinhTQ`) | `PUT /api/v1/school-admin/token-policy` |
| **`US-SP7-17`** | Học sinh Xin thêm Token trong ngày & Giáo viên / School Admin Duyệt cấp nhanh | `School Student`, `Teacher`, `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/student/token-requests`<br>`PUT /api/v1/teacher/token-requests/{id}/review` |
| **`US-SP7-18`** | Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Khóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`) | `School Admin` | 🔵 Dinh (`DinhTQ`) | `POST /api/v1/school-admin/academic-year/rollover`<br>`PUT /api/v1/school-admin/students/{uid}/deactivate` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP7-14: Chu kỳ Gói Trường học (01/09 – 31/05), Gia hạn Tháng 5, Top-up & Cơ chế Khóa phân tầng
* **Actor:** `System Admin`, `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Trước đây gói trường học chỉ là "một cục token" vô thời hạn, không rõ trường mua trong bao lâu, hết năm học hay hết token giữa chừng xử lý thế nào.
* **User Story:**  
  Là **School Admin / System Admin**, tôi muốn gói B2B của trường được định nghĩa theo chu kỳ năm học chính thức (**từ 01/09 đến hết 31/05**), có cửa sổ gia hạn trong **tháng 5** (kích hoạt từ **01/09** năm học mới), hỗ trợ nạp thêm (**Top-up**) khi cạn token giữa năm, và phân tách rõ ràng giữa hành vi **Hết hạn gói** với **Hết Token AI**.
* **Acceptance Criteria (AC):**
  1. **Chu kỳ gói chuẩn (Academic Subscription Cycle):**
     * Mọi gói đăng ký B2B của trường bắt đầu có hiệu lực từ **`01/09/{startYear} 00:00:00`** và kết thúc vào **`31/05/{endYear} 23:59:59`** (9 tháng năm học).
  2. **Quy tắc Gia hạn (Renewal in May):**
     * Trường phải thực hiện gia hạn **trong tháng 5** (`01/05` – `31/05`).
     * Sau khi gia hạn thành công trong tháng 5, gói của năm học kế tiếp ở trạng thái `RENEWED_PENDING` và **bắt đầu sử dụng được từ ngày `01/09`** đầu năm học mới.
     * Nếu trường gia hạn đúng hạn trong tháng 5, tối đa **20% lượng token còn dư** của năm cũ được cộng dồn (rollover) sang Quỹ chung của năm học mới vào ngày `01/09`.
  3. **Hết hạn gói mà chưa gia hạn (Subscription Expired $\rightarrow$ Khóa toàn bộ nền tảng):**
     * Sau ngày `31/05`, nếu trường chưa gia hạn (hoặc đang trong kỳ nghỉ hè chưa đến ngày `01/09` kích hoạt gói mới), trạng thái trường chuyển sang `EXPIRED`.
     * Khi trường ở trạng thái `EXPIRED` hoặc `SUSPENDED`: **Khóa toàn bộ nền tảng của trường** (Mọi API nghiệp vụ của `ROLE_SCHOOL_ADMIN`, `ROLE_TEACHER`, `ROLE_STUDENT` thuộc trường đều bị chặn với mã lỗi `403 SCHOOL_SUBSCRIPTION_EXPIRED`, ngoại trừ API xem thông tin gói/yêu cầu gia hạn của School Admin).
  4. **Còn hạn gói nhưng Hết Token giữa chừng (Out of Tokens $\rightarrow$ Chỉ khóa tính năng AI):**
     * Khi trường vẫn trong thời hạn (`01/09` – `31/05`, `status = ACTIVE`) nhưng Quỹ chung của trường (`unallocatedTokenQuota == 0`) và học sinh đã hết hạn mức ngày:
     * **Chỉ khóa tính năng AI** (Chat với nhân vật lịch sử trả về `403 AI_QUOTA_EXCEEDED`).
     * **Các tính năng khác dùng bình thường:** Làm bài Quiz, xem tài liệu lịch sử/Local Content, Giáo viên giao bài tập, Học sinh nộp bài, xem Dashboard thống kê vẫn hoạt động 100%.
  5. **Luồng Nạp thêm giữa năm (Top-up Token):**
     * Khi hết hoặc sắp hết token giữa năm học, School Admin gửi yêu cầu hoặc System Admin thực hiện `POST /api/v1/admin/schools/{id}/top-up` để cộng thêm token thẳng vào Quỹ chung (`totalSchoolTokenQuota` và `unallocatedTokenQuota`) mà không làm thay đổi ngày hết hạn `31/05`.

---

### 🆔 US-SP7-15: Quỹ Token Chung (Shared Pool) & Tự động Cấp Hạn mức Ngày (Daily Token Limit)
* **Actor:** `System`, `School Student`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Nếu chia đứt token 1 lần cho cả năm vào ví từng học sinh: học sinh dùng nhiều sẽ tiêu sạch trong vài tuần đầu, còn học sinh ít dùng thì token bị "chết" (*stranded tokens*) trong tài khoản; học sinh vào trường giữa năm cũng không rõ lấy token từ đâu.
* **User Story:**  
  Là **Hệ thống**, tôi muốn giữ toàn bộ token của trường trong **Quỹ chung (`School Shared Pool`)** và cấp cho mỗi học sinh theo **Hạn mức tiêu dùng theo ngày (`Daily Token Allowance`)**, tự động làm mới sau `00:00` mỗi ngày, để không bao giờ xảy ra tình trạng "token chết" hay tiêu lố sạch quỹ cả năm.
* **Acceptance Criteria (AC):**
  1. **Mô hình Quỹ chung + Hạn mức Ngày:**
     * Không chia đứt token cả năm khi import học sinh.
     * Học sinh vào đầu năm hay bổ sung vào giữa năm đều tự động hưởng định mức ngày (`dailyTokenLimit`, mặc định `1.000 token/học sinh/ngày`).
  2. **Cơ chế Cấp & Khấu trừ theo Ngày (Lazy Daily Reset):**
     * Mỗi ngày, khi học sinh thực hiện phiên tương tác AI đầu tiên trong ngày (kiểm tra `user.lastTokenResetAt < today.atStartOfDay()`):
       * Nếu `user.token` của ngày hôm trước còn dư chưa dùng hết: Thu hồi phần dư đó trả về Quỹ chung của trường (`school.unallocatedTokenQuota`) để **không bị mất/chết token**.
       * Rút tối đa `dailyTokenLimit` (ví dụ `1.000 token`, hoặc phần còn lại nếu quỹ trường còn ít hơn) từ `school.unallocatedTokenQuota` nạp vào `user.token` cho ngày hôm nay và cập nhật `user.lastTokenResetAt = now()`.
     * Học sinh nào **không sử dụng AI trong ngày** thì không rút token khỏi Quỹ trường $\rightarrow$ Tiết kiệm 100% token cho nhà trường.
  3. **Chống xài lố trong ngày:**
     * Khi học sinh tiêu hết số token trong ngày (`user.token <= 0`), hệ thống chỉ khóa tính năng Chat AI của học sinh đó trong phần còn lại của ngày; các tính năng làm Quiz, đọc bài, nộp bài tập vẫn dùng bình thường. Qua `00:00` ngày hôm sau tự động có lại hạn mức ngày mới.

---

### 🆔 US-SP7-16: Cấu hình Định mức Token Ngày & Bảng Ước tính Gói Enterprise cho School Admin
* **Actor:** `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  School Admin không có căn cứ ước tính một học sinh dùng bao nhiêu token và gói trường mua đáp ứng được bao nhiêu học sinh.
* **User Story:**  
  Là **School Admin**, tôi muốn xem bảng tham chiếu định mức sử dụng của từng gói `EnterprisePackage` và có thể điều chỉnh hạn mức token ngày (`dailyTokenLimit`) chung cho trường hoặc riêng theo từng khối lớp (`gradeLevel`), để phân phối tài nguyên hợp lý (ví dụ ưu tiên Khối 12 ôn thi).
* **Acceptance Criteria (AC):**
  1. **Định mức chuẩn gợi ý (Benchmark Metadata):**
     * Định mức mặc định hệ thống: **`1.000 token/học sinh/ngày`** ($\approx$ 15–20 câu hỏi đáp sâu với AI mỗi ngày học).
     * Khuyến nghị quy mô trường cho 9 tháng năm học (`01/09` – `31/05`):
       * `ENTERPRISE_SMALL (5.000.000 token)`: Phù hợp quy mô **~350 – 400 học sinh**.
       * `ENTERPRISE_MEDIUM (20.000.000 token)`: Phù hợp quy mô **~1.500 học sinh**.
       * `ENTERPRISE_LARGE (50.000.000 token)`: Phù hợp quy mô **~3.500+ học sinh**.
  2. **API Cấu hình Hạn mức Ngày (`PUT /api/v1/school-admin/token-policy`):**
     * Cho phép School Admin cập nhật `defaultDailyStudentQuota` của trường (ví dụ từ `1.000` lên `1.500 token/ngày`) hoặc thiết lập `dailyStudentQuota` riêng cho từng lớp/khối (`Classroom.dailyTokenQuota`).

---

### 🆔 US-SP7-17: Luồng Học sinh Xin thêm Token trong ngày & Giáo viên / School Admin Duyệt cấp nhanh
* **Actor:** `School Student`, `Teacher`, `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Khi học sinh tích cực học tập hoặc làm bài tập AI chạm trần hạn mức ngày thì không có luồng xin cấp thêm, làm gián đoạn việc học.
* **User Story:**  
  Là **Học sinh**, khi chạm hạn mức token trong ngày, tôi muốn gửi yêu cầu **Xin thêm Token (Daily Quota Boost)** kèm lý do để **Giáo viên phụ trách lớp** (hoặc **School Admin**) duyệt cấp bổ sung ngay trong ngày.
* **Acceptance Criteria (AC):**
  1. **Học sinh gửi yêu cầu (`POST /api/v1/student/token-requests`):**
     * Chỉ cho phép gửi khi học sinh đang thuộc lớp `ACTIVE` và chưa có yêu cầu nào đang ở trạng thái `PENDING` trong cùng ngày.
     * Payload gồm: `requestedTokens` (mặc định gợi ý `1.000` hoặc tối đa `3.000 token`), `reason` (lý do xin thêm).
  2. **Giáo viên / School Admin duyệt yêu cầu (`PUT /api/v1/teacher/token-requests/{requestId}/review`):**
     * Giáo viên chỉ được duyệt yêu cầu của học sinh thuộc lớp do chính mình phụ trách (`classroom.teacher_id == teacher.uid`); School Admin duyệt được mọi yêu cầu trong trường.
     * Khi duyệt (`APPROVED`): Trừ trực tiếp số token được duyệt từ `school.unallocatedTokenQuota` và cộng ngay vào `student.token` để học sinh tiếp tục chat AI ngay lập tức.
     * Khi từ chối (`REJECTED`): Lưu lý do phản hồi `reviewNote`.

---

### 🆔 US-SP7-18: Vòng đời Năm học: Lên lớp / Đổi lớp (`ARCHIVED`) & Vô hiệu hóa Tài khoản khi Ra trường / Chuyển trường (`DEACTIVATED`)
* **Actor:** `School Admin`
* **Phụ trách:** 🔵 Trần Quốc Dinh (`DinhTQ`)
* **Bối cảnh vấn đề:**  
  Chưa định nghĩa vòng đời qua các năm học: khi học sinh lên lớp, đổi lớp thì lớp cũ xử lý sao? Khi học sinh tốt nghiệp ra trường hoặc chuyển trường đi nơi khác thì tài khoản và token xử lý thế nào?
* **User Story:**  
  Là **School Admin**, tôi muốn lưu trữ (`ARCHIVED`) các lớp của năm học cũ để xếp học sinh lên lớp/đổi lớp trong năm học mới trên cùng tài khoản, đồng thời **vô hiệu hóa hoàn toàn (`DEACTIVATED` — khóa đăng nhập)** đối với tài khoản của học sinh đã ra trường hoặc chuyển trường.
* **Acceptance Criteria (AC):**
  1. **Kết thúc năm học & Lên lớp / Đổi lớp (Class Promotion & Transfer across Academic Years):**
     * Khi kết thúc năm học (`31/05`) hoặc khi School Admin chạy thao tác Chuyển giao năm học (`POST /api/v1/school-admin/academic-year/rollover`): Toàn bộ các lớp của năm học cũ chuyển `status` từ `ACTIVE` $\rightarrow$ `ARCHIVED`.
     * Học sinh vẫn giữ nguyên tài khoản `User` (cùng `uid`, `studentCode`, `email`) và toàn bộ lịch sử làm bài các năm trước.
     * Sang năm học mới (`01/09`), học sinh được gán/import vào lớp mới của `academicYear` mới. Quy tắc: Tại một thời điểm, mỗi học sinh chỉ thuộc **tối đa 1 lớp đang `ACTIVE`**, nhưng có thể có nhiều lớp lịch sử ở trạng thái `ARCHIVED`.
  2. **Ra trường (Tốt nghiệp Khối 12) hoặc Chuyển trường (Graduation / Transfer Out $\rightarrow$ Deactivate Account):**
     * Khi học sinh ra trường hoặc chuyển trường (`PUT /api/v1/school-admin/students/{uid}/deactivate` hoặc đánh dấu tốt nghiệp hàng loạt khối 12):
       * Thu hồi toàn bộ số `user.token` còn dư trong ngày của học sinh đó trả về Quỹ chung của trường (`school.unallocatedTokenQuota`), đặt `user.token = 0`.
       * Cập nhật trạng thái tài khoản học sinh sang **`DEACTIVATED`** (`accountStatus = DEACTIVATED` / `deletedAt = now()`).
       * **Khóa đăng nhập hoàn toàn:** Tài khoản học sinh đã `DEACTIVATED` không thể đăng nhập (`POST /api/v1/auth/login` trả về `403 ACCOUNT_DEACTIVATED`) và mọi JWT cũ bị từ chối tại `JwtAuthenticationFilter`.
