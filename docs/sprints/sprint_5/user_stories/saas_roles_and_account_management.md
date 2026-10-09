# 📋 USER STORIES: SAAS ROLES & ACCOUNT MANAGEMENT + SAAS SECURITY
> **Sprint:** Sprint 5 (04/10/2026 – 10/10/2026)  
> **Phân hệ:** Xác thực, Phân quyền SaaS RBAC & Quản lý Tài khoản Trường học  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Developer | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP5-01`** | Quản lý Trường học & Gói Hạn mức Token Tổng | `System Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/admin/schools`<br>`GET /api/v1/admin/schools`<br>`GET /api/v1/admin/schools/{id}` |
| **`US-SP5-02`** | Cấp Tài khoản School Admin & Security RBAC | `System Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/admin/school-admins` |
| **`US-SP5-03`** | Quản lý Tài khoản Giáo viên trong Trường | `School Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/school-admin/teachers` |
| **`US-SP5-04`** | Tạo lẻ & Import Excel Hàng loạt Tài khoản Học sinh | `School Admin` | 🟢 Khải (KhaiVDD) | `POST /api/v1/school-admin/students/import-excel` |
| **`US-SP5-05`** | Giám sát & Tra cứu Hạn mức Token Trường học | `School Admin` | 🟢 Khải (KhaiVDD) | `GET /api/v1/school-admin/token-quota` |

---

## 📑 CHI TIẾT USER STORIES

### 🆔 US-SP5-01: Quản lý Trường học & Gói Hạn mức Token Tổng (Enterprise Package Setup)
* **Actor:** `System Admin`
* **Thời gian thực hiện:** 04/10/2026 – 05/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là System Admin, tôi muốn tạo thông tin Trường học mới, gán Gói Enterprise Token cố định cho toàn trường, tra cứu danh sách và xem chi tiết trường học để thiết lập và quản lý các hợp đồng dịch vụ SaaS B2B với nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/schools` tiếp nhận: Tên trường, Mã trường (`school_code` - tùy chọn), Địa chỉ, Email liên hệ đại diện, Gói Enterprise (`ENTERPRISE_SMALL`: 5M Tokens/tháng, `ENTERPRISE_MEDIUM`: 20M Tokens/tháng, `ENTERPRISE_LARGE`: 50M Tokens/tháng).
  2. **Cơ chế Định danh Mã Trường học (Hybrid School Code Generation & Anti-Collision)**:
     - **Thủ công (Custom School Code):** Nếu System Admin nhập sẵn `school_code` (ví dụ dùng Mã định danh Sở GD&ĐT hoặc tên viết tắt quen thuộc như `LHP`, `AMS`), hệ thống chuẩn hóa Uppercase, validate định dạng regex `^[A-Za-z0-9_-]{2,30}$` và kiểm tra trùng lặp trên toàn hệ thống (báo lỗi `409 Data Conflict` nếu mã tùy chỉnh đã bị sử dụng).
     - **Tự động sinh (Auto-generated from School Name & Address):** Nếu để trống `school_code`, hệ thống **tự động kết hợp Tên trường và Địa chỉ**:
       * **Bóc tách Tên trường (Name Acronym):** Loại bỏ các định từ phổ biến (`"Trường"`, `"THPT"`, `"THCS"`, `"Tiểu học"`, `"Phổ thông"`, `"Chuyên"`), lấy các chữ cái đầu không dấu: *"THPT Lê Hồng Phong"* $\rightarrow$ `LHP`; *"THCS Nguyễn Tất Thành"* $\rightarrow$ `NTT`.
       * **Bóc tách Địa chỉ (Location Acronym):** Phân tích tỉnh/thành phố từ địa chỉ trường (dựa vào đoạn cuối địa chỉ hoặc danh sách alias tỉnh/thành chuẩn): *"TP. Hồ Chí Minh"* $\rightarrow$ `HCM`; *"Nam Định"* $\rightarrow$ `ND`; *"Hà Nội"* $\rightarrow$ `HN`; *"Đà Nẵng"* $\rightarrow$ `DN`.
       * **Định dạng Mã cơ sở:** `{TÊN_VIẾT_TẮT}-{ĐỊA_CHỈ}` $\rightarrow$ Ví dụ: Trường THPT Lê Hồng Phong tại TP.HCM sinh ra `LHP-HCM`; Trường THPT Lê Hồng Phong tại Nam Định sinh ra `LHP-ND`. (Nếu không có địa chỉ thì dùng `{TÊN_VIẾT_TẮT}`).
       * **Cơ chế chống trùng lặp (Anti-Collision Sequence):** Nếu trong hệ thống đã tồn tại trường có mã trùng (ví dụ có 2 trường THPT Lê Hồng Phong cùng ở TP.HCM đăng ký), hệ thống **tự động tăng hậu tố sequence theo gạch nối**: `{BASE_CODE}-01`, `{BASE_CODE}-02`, `{BASE_CODE}-03`... (VD: Trường thứ 1 nhận `LHP-HCM`, trường thứ 2 nhận `LHP-HCM-01`, trường thứ 3 nhận `LHP-HCM-02`), đảm bảo các trường đăng ký sau **không bao giờ bị lỗi trùng mã hay bị chặn đăng ký**.
  3. Lưu số Token tổng vào trường `total_school_token_quota` và khởi tạo số Token dư chưa phân bổ `unallocated_token_quota = total_school_token_quota`.
  4. Kiểm tra điều khoản Policy miễn trừ trách nhiệm về Nội dung Lịch sử Địa phương được chấp nhận trong hợp đồng trường (`localHistoryPolicyAccepted = true`).
  5. **Tra cứu & Quản lý Danh sách Trường học:**
     - API `GET /api/v1/admin/schools`: Hỗ trợ tìm kiếm theo từ khóa (`search` - tên trường, mã trường, contact email), phân trang chuẩn `page`, `size`, `sortBy`, `sortDirection` trả về `PaginatedApiResponse<SchoolResponse>` (gồm mảng `data` và metadata `pagination`).
     - API `GET /api/v1/admin/schools/{id}`: Trả về chi tiết trường học gồm hạn mức token tổng, token chưa phân bổ, địa chỉ, trạng thái hoạt động.

---

### 🆔 US-SP5-02: Cấp Tài khoản School Admin & Security RBAC Setup
* **Actor:** `System Admin`
* **Thời gian thực hiện:** 05/10/2026 – 06/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là System Admin, tôi muốn tạo tài khoản `School Admin` gắn liền với một `school_id` cụ thể và thiết lập kiểm soát phân quyền RBAC, để bàn giao quyền quản trị trường cho phía đại diện nhà trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/admin/school-admins` tạo user có role `SCHOOL_ADMIN` liên kết với `school_id`.
  2. Bổ sung `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT` vào Enum `UserRole`.
  3. Cấu hình Spring Security: `School Admin` chỉ truy cập và chỉnh sửa được các tài nguyên có `school_id` khớp với tài khoản của mình.
  4. Tự động mã hóa mật khẩu tạm thời bằng BCrypt và gửi Email kích hoạt cho School Admin.
* **Quy tắc Sinh Tên tài khoản & Mật khẩu khởi tạo (Account Credentials Generation Rules):**
  - **Quy tắc sinh `username`:**
    * Format: `{school_code}_admin` (toàn bộ chữ thường, không dấu, khoảng trắng đổi thành gạch dưới. Ví dụ: trường mã `LHP` $\rightarrow$ `lhp_admin`, trường mã `LHP-HCM` $\rightarrow$ `lhp_hcm_admin`).
    * Cơ chế chống trùng (Anti-collision Sequence): Nếu trường tạo thêm tài khoản admin, tự động tăng hậu tố sequence: `{school_code}_admin_1`, `{school_code}_admin_2`...
  - **Quy tắc sinh `initialPassword`:**
    * Format: Chuỗi 10 ký tự an toàn bắt đầu bằng prefix cố định `Ht@` kèm 7 ký tự ngẫu nhiên (chữ hoa, chữ thường, số, ký tự đặc biệt. VD: `Ht@KyLZBGU`, `Ht@9xP2mQ!z`).
    * Mã hóa bằng BCrypt trước khi lưu database.
    * Gán cờ `must_change_password = true` để bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên.
    * Trả về mật khẩu khởi tạo dạng plaintext trong response (`initialPassword`) để System Admin bàn giao cho nhà trường.

---

### 🆔 US-SP5-03: Quản lý Tài khoản Giáo viên trong Trường (Teacher Management)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 06/10/2026 – 07/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn tạo mới, cập nhật thông tin và khóa tài khoản các Giáo viên thuộc trường mình, để cấp quyền cho Giáo viên quản lý lớp học.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/teachers` tạo user có role `TEACHER` thuộc cùng `school_id`.
  2. Yêu cầu nhập: Họ tên, Email, Số điện thoại.
  3. API `GET /api/v1/school-admin/teachers` danh sách giáo viên trong trường có tìm kiếm và phân trang.
  4. API `PUT /api/v1/school-admin/teachers/{id}/status` cho phép chuyển trạng thái `ACTIVE` / `INACTIVE`.
* **Quy tắc Sinh Tên tài khoản & Mật khẩu khởi tạo (Teacher Credentials Generation Rules):**
  - **Quy tắc sinh `username`:**
    * Format: `{school_code}_gv_{4_chữ_số_ngẫu_nhiên}` (toàn bộ chữ thường, không dấu. Ví dụ: `lhp_hcm_gv_1042`, `lhp_gv_7153`).
    * Cơ chế kiểm tra chống trùng: Hệ thống thực hiện kiểm tra `existsByUserNameIgnoreCase` trong database trước khi lưu, đảm bảo tên đăng nhập là duy nhất 100% trên toàn hệ thống.
  - **Quy tắc sinh `initialPassword`:**
    * Format: Chuỗi 10 ký tự an toàn bắt đầu bằng prefix `Ht@` (VD: `Ht@nAScTPY`).
    * Mã hóa bằng BCrypt trước khi lưu database.
    * Gán cờ `must_change_password = true`.
    * Trả về `initialPassword` trong response tạo mới để School Admin bàn giao cho giáo viên.

---

### 🆔 US-SP5-04: Tạo lẻ & Import Excel/CSV Hàng loạt Tài khoản Học sinh (Student Bulk Import)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 07/10/2026 – 08/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn tạo lẻ từng học sinh hoặc upload file CSV/Excel danh sách học sinh theo mẫu chuẩn, để khởi tạo tài khoản `SCHOOL_STUDENT` cho học sinh toàn trường kèm mật khẩu được tự sinh hoặc thiết lập sẵn.
* **File Template Mẫu Chuẩn:**
  - [student_import_template.csv](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/student_import_template.csv) *(hoặc bản mẫu 10 học sinh: [student_import_classroom_10_students.csv](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/user_stories/student_import_classroom_10_students.csv))*.
* **Quy chuẩn Fields trong Template:**
  | Tên Cột (Header) | Kiểu Dữ Liệu | Bắt buộc | Mô tả & Ràng buộc Validation |
  | :--- | :--- | :---: | :--- |
  | `student_code` | String (3 - 30) | **Có (hoặc Tự sinh)** | Mã học sinh định danh duy nhất trong trường. <br>- **Nếu nhập**: Kiểm tra format `^[A-Za-z0-9_-]+$`, unique trong `school_id`. <br>- **Nếu để trống**: Hệ thống **tự động sinh theo Rule chuẩn**: `{school_code}_HS{YY}{5_chữ_số_tự_tăng}` (VD: `LHP_HS2600001`). |
  | `full_name` | String (2 - 150) | **Có** | Họ và tên đầy đủ của học sinh. Hỗ trợ tiếng Việt Unicode UTF-8. |
  | `email` | String (tối đa 100) | **Không (Tùy chọn)** | Email học sinh hoặc phụ huynh (chỉ để lưu trữ hồ sơ liên lạc nếu trường có sẵn). Học sinh chưa có sẽ tự liên kết Gmail cá nhân ở lần đăng nhập đầu tiên (First Login Onboarding). |
  | `dob` | Date (`YYYY-MM-DD`) | **Có** | Ngày sinh của học sinh theo định dạng chuẩn ISO (VD: `2009-03-15`). |
  | `gender` | Enum String | Không (Tùy chọn) | Giới tính: `MALE` hoặc `FEMALE`. Nếu để trống sẽ lưu null, không bắt buộc. |
  | `phone_number` | String (10 số) | Không | Số điện thoại học sinh hoặc phụ huynh (định dạng SĐT Việt Nam, VD: `0912345671`). |
  | `password` | String (6 - 50) | Không | **Mật khẩu khởi tạo**: <br>- **Nếu để trống**: Hệ thống **tự động generate mật khẩu ngẫu nhiên** (VD: `{student_code}@2026` hoặc chuỗi 8 ký tự an toàn `Ht@xxxxxx`). <br>- **Nếu nhập sẵn**: Hệ thống kiểm tra độ dài tối thiểu và mã hóa BCrypt. Đánh dấu cờ `must_change_password = true` khi đăng nhập lần đầu. |
  | `additional_token` | Integer (>= 0) | Không | **Token Bổ Sung Riêng (Extra Token)**: <br>- Số token sàn cơ bản đã được nhập trên UI (`default_initial_token`, VD: `10000`). <br>- **Cột này trong file chỉ là token cộng thêm**: Nếu để trống hoặc `0` $\rightarrow$ Nhận đúng mức sàn từ UI (`10000`). Nếu điền giá trị (VD: `5000`) $\rightarrow$ Nhận tổng = `10000 + 5000 = 15000` Tokens. |

* **Quy Tắc Định Danh & Chống Trùng Tài Khoản 100% (Anti-Collision Rules):**
  1. **Quy tắc sinh `student_code`**:
     - *Thủ công:* Cho phép nhập mã học sinh sẵn có của trường (Sổ điểm, vnEdu/SMAS). Regex: `^[A-Za-z0-9_-]{3,30}$`, bắt buộc duy nhất theo `school_id`.
     - *Tự động:* Nếu bỏ trống, hệ thống sinh theo Sequence của trường: `{school_code}_HS{Năm_Tuyển_Sinh}{Số_Tự_Tăng_5_Chữ_Số}` $\rightarrow$ Ví dụ: Trường THPT Lê Hồng Phong (`LHP`), năm 2026 sẽ sinh: `LHP_HS2600001`, `LHP_HS2600002`...
  2. **Quy tắc sinh `username`**:
     - Bắt buộc gắn prefix trường để chống trùng tuyệt đối trên toàn hệ thống SaaS: `{school_code}_hs_{student_code}` (lowercase, không dấu, khoảng trắng đổi thành gạch dưới) $\rightarrow$ Ví dụ: `lhp_hs_hs2600001`.
  3. **Quy tắc cấp Token**:
     - Tổng token cấp cho 1 học sinh: $\text{Token}(i) = \text{default\_initial\_token (từ UI)} + \text{additional\_token (từ File)}$.
     - Tổng Token cấp cho cả đợt import: $\sum \text{Token}(i) \le \text{unallocated\_token\_quota}$. Trừ trực tiếp vào quỹ chưa phân bổ của trường.
* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/school-admin/students/import-excel` (hỗ trợ file `.xlsx` và `.csv`) tiếp nhận và phân tích file theo danh sách header chuẩn.
  2. Validate toàn diện dữ liệu từng dòng:
     - Kiểm tra các trường bắt buộc (`student_code`, `full_name`, `dob`). Trường `email` và `phone_number` là tùy chọn.
     - Kiểm tra trùng lặp `student_code` (và `email` nếu có điền) trong nội bộ file và với dữ liệu đang có trong database.
     - Nếu có dòng lỗi: trả về danh sách chi tiết mã dòng (Row Number), tên trường lỗi và nguyên nhân cụ thể (Partial Fail hoặc Reject All theo cấu hình giao dịch).
  3. Cơ chế tạo tài khoản & Shadow SaaS Email (Tương thích DB B2C email NOT NULL):
     - Tự sinh username chuẩn: `{school_code}_hs_{student_code}` (lowercase, không dấu).
     - Xử lý Email (Tương thích với kiến trúc B2C hiện tại):
       * Nếu file có email: Lưu trực tiếp email được cung cấp.
       * Nếu file để trống email: Hệ thống tự động gán **Shadow SaaS Email** `{username}@saas.historytalk.vn` (đảm bảo thỏa mãn tuyệt đối ràng buộc `email NOT NULL` của DB hiện tại mà không làm vỡ các module B2C cũ).
     - Tự động sinh password ngẫu nhiên khi cột `password` bị bỏ trống, hash bằng BCrypt trước khi lưu. Gán cờ `must_change_password = true`.
     - Gán role `SCHOOL_STUDENT` và liên kết trực tiếp với `school_id` của School Admin đang thao tác.
     - Quy trình First Login Onboarding: Học sinh đăng nhập bằng `username` + mật khẩu tạm, bắt buộc đổi mật khẩu và được gợi ý nhập Gmail cá nhân xác thực qua OTP để ghi đè email chính thức.
  4. Trả về kết quả import:
     - Trả về thống kê số lượng thành công / thất bại.
     - Xuất kèm/cho phép download file kết quả import chứa danh sách tài khoản (`student_code`, `user_name`, `email`) kèm **Mật khẩu khởi tạo ban đầu (Plaintext Initial Password)** để nhà trường in/phát cho học sinh đăng nhập lần đầu.

---

### 🆔 US-SP5-05: Giám sát & Tra cứu Hạn mức Token Trường học (School Token Quota Monitoring)
* **Actor:** `School Admin`
* **Thời gian thực hiện:** 08/10/2026 – 09/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **User Story:**  
  Là School Admin, tôi muốn theo dõi gói dịch vụ Enterprise hiện tại, số lượng token tổng, số token đã phân bổ và số token còn dư chưa phân bổ của trường mình, để nắm rõ tình trạng tài nguyên và có kế hoạch phân bổ token hợp lý cho học sinh/giáo viên trước mỗi đợt import tài khoản.
* **Acceptance Criteria (AC):**
  1. API `GET /api/v1/school-admin/token-quota` bảo mật bằng RBAC role `SCHOOL_ADMIN`, tự động trích xuất `school_id` an toàn từ ngữ cảnh đăng nhập.
  2. Trả về thông tin hạn mức token trường học:
     - `schoolId`, `schoolName`, `schoolCode`
     - `packageType` (VD: `ENTERPRISE_SMALL`, `ENTERPRISE_MEDIUM`, `ENTERPRISE_LARGE`)
     - `totalSchoolTokenQuota` (Tổng hạn mức theo gói)
     - `unallocatedTokenQuota` (Số token còn lại chưa phân bổ)
     - `allocatedTokenQuota` (Số token đã phân bổ = `total - unallocated`)
     - `usagePercentage` (Tỷ lệ đã sử dụng/phân bổ dạng %)
  3. Bọc kết quả bằng `ApiResponse<SchoolTokenQuotaResponse>` chuẩn.

---

* **Kiến trúc Kỹ thuật & Nguyên tắc Triển khai (Technical Design & Guidelines for Devs):**
  1. **Pattern xử lý đa hình (Template Method Pattern)**:
     - Định nghĩa Interface `StudentImportProcessor` và Skeletal Abstract Class `AbstractStudentImportProcessor` để tái sử dụng toàn bộ logic parse file, validate format cơ bản, hash BCrypt và persist user.
     - Triển khai `SchoolWideImportProcessor` cho School Admin (đọc `class_code` từ file, trừ `unallocated_token_quota` của trường).
     - Sẵn sàng mở rộng cho `ClassScopedImportProcessor` khi Giáo viên import theo lớp ở các Sprint kế tiếp.
  2. **Quy tắc Dependency Injection (Strict Constructor Injection)**:
     - **Tuyệt đối KHÔNG dùng `@Autowired` field injection**.
     - Bắt buộc dùng **`@RequiredArgsConstructor`** và khai báo các dependencies dạng `private final` (hoặc `protected final` trong Abstract Class) để đảm bảo tính bất biến, dễ viết Unit Test và tuân thủ Spring Framework Best Practices.
  3. **Chuẩn hóa DTO & Controller Response**:
     - **Không xây dựng DTO trong Controller**: Controller chỉ đóng vai trò Adapter tiếp nhận request, chuyển tham số vào Service. Toàn bộ logic mapping entity, xử lý dữ liệu và build Response DTO phải được thực hiện tại **Service Layer** (hoặc Mapper chuyên trách).
     - **Chuẩn hóa phản hồi qua `ApiResponse<T>`**: Controller bọc kết quả trả về bằng DTO chuẩn của hệ thống `com.historytalk.dto.ApiResponse<T>`:
       `return ResponseEntity.ok(ApiResponse.success(resultDto, "Import danh sách học sinh thành công"));`
       Không trả về raw Entity hoặc `ResponseEntity.ok(data)` không có mã code/message chuẩn.
  4. **Cấu hình Quota & Tham số Tập trung (No Magic Numbers)**:
     - Các giá trị mặc định như `defaultInitialToken` (10,000) hay prefix mật khẩu phải được đưa vào file cấu hình `application.properties` (ví dụ `historytalk.saas.student.default-initial-token=10000`) hoặc lớp Constant dùng chung (`SaasConstant`), tuyệt đối không hardcode trong Controller.
  5. **Quy tắc Kiểm tra Token Quota Atomic**:
     - Trước khi khởi tạo tài khoản, Service phải tính tổng token cần cấp: $\sum \text{token}(i)$ và đối soát với `unallocated_token_quota` của Trường trong cùng một `@Transactional`. Nếu vượt quá, ném lỗi `InsufficientQuotaException` (map mã lỗi qua Global Exception Handler) và rollback toàn bộ.
  6. **Chiến lược Kiến trúc Database Multi-Tenancy (Shared Database, Shared Schema)**:
     - **Mô hình kiến trúc:** Áp dụng mô hình **Shared Database, Shared Schema với Logical Data Isolation** (phân tách dữ liệu theo cột `school_id`).
     - **Lý do lựa chọn (Design Rationale):**
       * *Tối ưu tài nguyên Cloud (Supabase):* Tránh cạn kiệt Connection Pool của Supabase Free/Basic tier (vốn giới hạn 60-100 kết nối). Dùng chung 1 DB chỉ tốn 1 pool duy nhất (HikariCP), đảm bảo hệ thống vận hành ổn định trong suốt quá trình demo bảo vệ đồ án.
       * *Đồng bộ Migration (Flyway):* Schema được quản lý tập trung, Flyway chạy tự động 1 lần khi khởi động Spring Boot, không lo lệch cấu trúc giữa các tenant.
       * *Báo cáo tổng hợp hiệu năng cao:* Hỗ trợ System Admin truy vấn dashboard toàn hệ thống (tổng token tiêu thụ, xếp hạng trường học) bằng câu lệnh SQL thông thường, không cần truy vấn phân tán phức tạp (Cross-database query).
     - **Cơ chế đảm bảo an toàn & cô lập dữ liệu (Logical Isolation):**
       * *Token-driven Isolation:* `school_id` luôn được trích xuất an toàn từ Claims của JWT Token (`UserPrincipal`), nghiêm cấm nhận `school_id` từ Request Body của Client ở các role `SCHOOL_ADMIN` và `TEACHER`.
       * *Repository Scope:* Toàn bộ các câu lệnh truy vấn tài nguyên trường học bắt buộc gắn điều kiện `WHERE school_id = :schoolId`.
       * *Performance Indexing:* Đánh Composite Index `(school_id, student_code)` để tối ưu hóa tốc độ tìm kiếm dù số lượng học sinh toàn trường lên đến hàng chục nghìn bản ghi.
