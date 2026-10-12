# 📋 Implementation Plan: SaaS User Full Lifecycle Management (School Admin, Teacher, Student)

> **Sprint:** Sprint 5  
> **Phân hệ:** Quản lý Vòng đời Tài khoản SaaS (Full Lifecycle CRUD, Status, Soft-delete, Reset Password)  
> **Developer:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Target Date:** 12/10/2026  
> **Mapped User Stories:** `US-SP5-02`, `US-SP5-03`, `US-SP5-04`

---

## 1. Mục Tiêu (Objective)
Hiện tại hệ thống mới chỉ hỗ trợ chiều tạo mới (`POST`) tài khoản và một phần tra cứu giáo viên. Kế hoạch này hoàn thiện **100% vòng đời quản trị tài khoản** cho cả 3 đối tượng nhân sự SaaS:
1. **System Admin quản lý School Admin (`US-SP5-02`):** Xem danh sách School Admin theo trường, sửa thông tin, khóa/mở khóa, xóa mềm (soft-delete), đặt lại mật khẩu.
2. **School Admin quản lý Teacher (`US-SP5-03`):** Sửa thông tin giáo viên, xóa mềm (soft-delete), đặt lại mật khẩu.
3. **School Admin quản lý Student (`US-SP5-04`):** Tra cứu danh sách học sinh (tìm kiếm, lọc trạng thái, phân trang), xem chi tiết hồ sơ, sửa thông tin, khóa/mở khóa, xóa mềm (soft-delete), đặt lại mật khẩu.

---

## 2. Danh Sách API Chi Tiết (API Specifications)

### 2.1. Phân hệ System Admin Quản lý School Admin (`US-SP5-02`)
*Base Path: `/api/v1/admin` | Quyền yêu cầu: `ROLE_SYSTEM_ADMIN`*

| Method | Endpoint | Mô tả | Request Body / Params | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/schools/{schoolId}/school-admins` | Xem danh sách School Admin của một trường | `search`, `page`, `size`, `sortBy`, `sortDirection` | `PaginatedApiResponse<SchoolAdminResponse>` |
| `PUT` | `/school-admins/{id}` | Cập nhật thông tin cơ bản School Admin | `{ fullName, phoneNumber, email }` | `ApiResponse<SchoolAdminResponse>` |
| `PUT` | `/school-admins/{id}/status` | Khóa (`active=false`) / Mở khóa (`active=true`) | `{ active: boolean }` | `ApiResponse<SchoolAdminResponse>` |
| `DELETE` | `/school-admins/{id}` | Xóa mềm School Admin (`deleted_at = now()`) | N/A | `ApiResponse<Void>` |
| `POST` | `/school-admins/{id}/reset-password` | Đặt lại mật khẩu ngẫu nhiên cho School Admin | N/A | `ApiResponse<ResetPasswordResponse>` |

---

### 2.2. Phân hệ School Admin Quản lý Giáo Viên (`US-SP5-03`)
*Base Path: `/api/v1/school-admin/teachers` | Quyền yêu cầu: `ROLE_SCHOOL_ADMIN`*

| Method | Endpoint | Mô tả | Request Body / Params | Response |
| :--- | :--- | :--- | :--- | :--- |
| `PUT` | `/{id}` | Cập nhật thông tin giáo viên trong trường | `{ fullName, phoneNumber, email }` | `ApiResponse<TeacherResponse>` |
| `DELETE` | `/{id}` | Xóa mềm giáo viên (`deleted_at = now()`) | N/A | `ApiResponse<Void>` |
| `POST` | `/{id}/reset-password` | Đặt lại mật khẩu ngẫu nhiên cho giáo viên | N/A | `ApiResponse<ResetPasswordResponse>` |

---

### 2.3. Phân hệ School Admin Quản lý Học Sinh (`US-SP5-04`)
*Base Path: `/api/v1/school-admin/students` | Quyền yêu cầu: `ROLE_SCHOOL_ADMIN`*

| Method | Endpoint | Mô tả | Request Body / Params | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `` | Tra cứu danh sách học sinh trường (tìm kiếm, phân trang) | `search` (mã HS, tên, username, email), `active` (boolean, tùy chọn), `page`, `size`, `sortBy`, `sortDirection` | `PaginatedApiResponse<StudentResponse>` |
| `GET` | `/{id}` | Xem chi tiết hồ sơ học sinh | N/A | `ApiResponse<StudentResponse>` |
| `PUT` | `/{id}` | Cập nhật thông tin học sinh | `{ fullName, dob, gender, phoneNumber, email }` | `ApiResponse<StudentResponse>` |
| `PUT` | `/{id}/status` | Khóa (`active=false`) / Mở khóa (`active=true`) | `{ active: boolean }` | `ApiResponse<StudentResponse>` |
| `DELETE` | `/{id}` | Xóa mềm tài khoản học sinh (`deleted_at = now()`) | N/A | `ApiResponse<Void>` |
| `POST` | `/{id}/reset-password` | Đặt lại mật khẩu học sinh | N/A | `ApiResponse<ResetPasswordResponse>` |

---

## 3. Kiến Trúc Kỹ Thuật & Các Tệp Tin Cần Thay Đổi

### 3.1. DTOs (Data Transfer Objects)
- **Tạo mới:**
  - `UpdateSchoolAdminRequest.java`: Chứa `fullName` (bắt buộc), `phoneNumber`, `email` (bắt buộc, format email).
  - `UpdateTeacherRequest.java`: Chứa `fullName` (bắt buộc), `phoneNumber`, `email` (bắt buộc, format email).
  - `UpdateStudentRequest.java`: Chứa `fullName` (bắt buộc), `dob` (bắt buộc), `gender`, `phoneNumber`, `email`.
  - `StudentResponse.java`: Trả về `uid`, `studentCode`, `userName`, `fullName`, `email`, `dob`, `gender`, `phoneNumber`, `token`, `active`, `schoolId`, `schoolName`, `createdAt`, `updatedAt`.
  - `ResetPasswordResponse.java`: Dùng chung cho cả 3 role gồm `uid`, `userName`, `fullName`, `email`, `initialPassword` (plaintext), `message`.
- **Cập nhật:**
  - `SchoolAdminResponse.java`: Bổ sung trường `active` (dựa trên `deletedAt == null`).

### 3.2. Data Access Layer (`UserRepository.java`)
- Thêm method kiểm tra email trùng lặp loại trừ chính user đang sửa:
  `boolean existsByEmailIgnoreCaseAndUidNot(String email, UUID uid);`
- Thêm method truy vấn danh sách School Admin theo trường:
  - `Page<User> findBySchoolIdAndRoleAndDeletedAtIsNull(UUID schoolId, UserRole role, Pageable pageable);`
  - Hoặc query có tìm kiếm keyword theo `fullName`, `userName`, `email`.
- Thêm JPQL method tìm kiếm danh sách Học sinh (`UserRole.SCHOOL_STUDENT`) theo `schoolId`:
  - Lọc theo keyword (tìm trên `studentCode`, `fullName`, `userName`, `email`).
  - Lọc theo cờ active (`deletedAt IS NULL` khi `active=true`, hoặc `deletedAt IS NOT NULL` khi `active=false`).
  - Phân trang `Pageable`.

### 3.3. Service Layer
- **`SchoolManagementService` & `SchoolManagementServiceImpl`:**
  - `PaginatedResponse<SchoolAdminResponse> getSchoolAdmins(UUID schoolId, String search, Pageable pageable)`
  - `SchoolAdminResponse updateSchoolAdmin(UUID adminId, UpdateSchoolAdminRequest request)`
  - `SchoolAdminResponse updateSchoolAdminStatus(UUID adminId, boolean active)`
  - `void deleteSchoolAdmin(UUID adminId)`
  - `ResetPasswordResponse resetSchoolAdminPassword(UUID adminId)`
- **`TeacherManagementService` & `TeacherManagementServiceImpl`:**
  - `TeacherResponse updateTeacher(UUID teacherId, UUID schoolId, UpdateTeacherRequest request)`
  - `void deleteTeacher(UUID teacherId, UUID schoolId)`
  - `ResetPasswordResponse resetTeacherPassword(UUID teacherId, UUID schoolId)`
- **`StudentManagementService` (Tạo mới Interface) & `StudentManagementServiceImpl` (Tạo mới Class):**
  - `PaginatedResponse<StudentResponse> getStudents(UUID schoolId, String search, Boolean active, Pageable pageable)`
  - `StudentResponse getStudentById(UUID studentId, UUID schoolId)`
  - `StudentResponse updateStudent(UUID studentId, UUID schoolId, UpdateStudentRequest request)`
  - `StudentResponse updateStudentStatus(UUID studentId, UUID schoolId, boolean active)`
  - `void deleteStudent(UUID studentId, UUID schoolId)`
  - `ResetPasswordResponse resetStudentPassword(UUID studentId, UUID schoolId)`

### 3.4. Controller Layer
- **`AdminSchoolController.java`:**
  - Bổ sung 5 endpoint quản lý School Admin (`GET /schools/{schoolId}/school-admins`, `PUT /school-admins/{id}`, `PUT /school-admins/{id}/status`, `DELETE /school-admins/{id}`, `POST /school-admins/{id}/reset-password`).
- **`SchoolAdminTeacherController.java`:**
  - Bổ sung 3 endpoint (`PUT /{id}`, `DELETE /{id}`, `POST /{id}/reset-password`).
- **`SchoolAdminStudentController.java`:**
  - Bổ sung 6 endpoint (`GET /`, `GET /{id}`, `PUT /{id}`, `PUT /{id}/status`, `DELETE /{id}`, `POST /{id}/reset-password`).

---

## 4. Nguyên Tắc Bảo Mật & Ràng Buộc Dữ Liệu (Security & Data Integrity)

1. **Multi-Tenancy Tenant Isolation:**
   - Đối với các thao tác của `School Admin`: Tuyệt đối kiểm tra `user.getSchool().getId().equals(currentSchoolId)`. Nếu không khớp, ném `ResourceNotFoundException("Tài khoản không thuộc quyền quản lý của trường")` để tránh rò rỉ ID.
2. **Soft-Delete An Toàn:**
   - Không dùng `userRepository.delete()`. Tất cả các thao tác xóa đều cập nhật `deletedAt = LocalDateTime.now()`.
   - Danh sách mặc định có thể lọc loại trừ các tài khoản đã bị xóa (hoặc hiển thị với trạng thái `active = false`).
3. **Chống Trùng Email Khi Cập Nhật:**
   - Khi cập nhật email cho bất kỳ user nào: nếu email mới khác email hiện tại, kiểm tra `existsByEmailIgnoreCaseAndUidNot`. Nếu đã có tài khoản khác dùng thì ném `DataConflictException`.
4. **Quy Tắc Đặt Lại Mật Khẩu:**
   - Sinh mật khẩu ngẫu nhiên 10 ký tự chuẩn an toàn `Ht@xxxxxxx` (hoặc `{studentCode}@2026` cho học sinh).
   - Mã hóa BCrypt trước khi cập nhật.
   - Bắt buộc đặt cờ `must_change_password = true` để người dùng phải đổi mật khẩu khi đăng nhập lần sau.

---

## 5. Kế Hoạch Triển Khai Theo Bước (Step-by-Step Execution Plan)

### Bước 1: DTOs & Repository Methods
- Tạo các file DTO: `UpdateSchoolAdminRequest`, `UpdateTeacherRequest`, `UpdateStudentRequest`, `StudentResponse`, `ResetPasswordResponse`.
- Bổ sung các phương thức truy vấn vào `UserRepository`.

### Bước 2: Triển Khai Phân Hệ School Admin Management (System Admin)
- Cập nhật `SchoolManagementService` và `SchoolManagementServiceImpl`.
- Cập nhật `AdminSchoolController`.
- Viết Unit Test cho Service.

### Bước 3: Triển Khai Phân Hệ Teacher Management (School Admin)
- Cập nhật `TeacherManagementService` và `TeacherManagementServiceImpl`.
- Cập nhật `SchoolAdminTeacherController`.
- Viết Unit Test cho Service.

### Bước 4: Triển Khai Phân Hệ Student Management (School Admin)
- Tạo Interface `StudentManagementService` và Implementation `StudentManagementServiceImpl`.
- Cập nhật `SchoolAdminStudentController`.
- Viết Unit Test cho Service.

### Bước 5: Kiểm Thử Toàn Diện & Cập Nhật API Contract Cho FE
- Chạy toàn bộ test suite Maven: `mvn test -Dtest=*School*Test,*Teacher*Test,*Student*Test`.
- Đồng bộ các endpoint mới vào file tài liệu FE contract: [`docs/sprints/sprint_5/fe_contracts/saas_roles_and_account_management_api_contract.md`](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_5/fe_contracts/saas_roles_and_account_management_api_contract.md).
