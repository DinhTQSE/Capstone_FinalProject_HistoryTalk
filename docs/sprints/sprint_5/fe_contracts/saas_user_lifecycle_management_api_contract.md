# SaaS User Lifecycle Management API Contract

> **Phân hệ:** Quản lý Vòng đời Tài khoản SaaS (System Admin, School Admin, Teacher, Student)  
> **Người biên soạn:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Dành cho:** Frontend Developers (Web Admin & School Portal)  
> **Base URL (Local):** `http://localhost:8080/Historical-tell`  
> **Swagger UI:** `http://localhost:8080/Historical-tell/api/v1/swagger-ui`  
> **Authentication Header:** `Authorization: Bearer <access_token>`  
> **Content-Type:** `application/json`

---

## 1. Response Wrapper & Common Types

### 1.1 Single Item Response Wrapper
```typescript
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  errorCode: string | null;
}
```

### 1.2 Paginated List Response Wrapper
```typescript
export interface PaginatedApiResponse<T> {
  success: boolean;
  message: string;
  data: T[];
  pagination: {
    page: number;          // 0-indexed
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
  };
  timestamp: string;
  errorCode: string | null;
}
```

---

## 2. TypeScript Interfaces & Data Models

### 2.1 Model Dữ liệu Phản Hồi (Responses)
```typescript
// Phản hồi chi tiết School Admin
export interface SchoolAdminResponse {
  uid: string;
  userName: string;
  email: string;
  fullName: string;
  phoneNumber?: string | null;
  role: "SCHOOL_ADMIN";
  schoolId: string;
  schoolName: string;
  active: boolean;
  initialPassword?: string | null; // Chỉ có khi vừa tạo mới tài khoản
  createdAt: string;
}

// Phản hồi chi tiết Giáo viên
export interface TeacherResponse {
  uid: string;
  userName: string;
  fullName: string;
  email: string;
  phoneNumber?: string | null;
  active: boolean;
  initialPassword?: string | null; // Chỉ có khi vừa tạo mới tài khoản
  createdAt: string;
}

// Phản hồi chi tiết Học sinh
export interface StudentResponse {
  uid: string;
  studentCode: string;
  userName: string;
  fullName: string;
  email: string;
  dob?: string | null;          // Format YYYY-MM-DD
  gender?: "MALE" | "FEMALE" | "OTHER" | null;
  phoneNumber?: string | null;
  token: number;
  active: boolean;
  schoolId: string;
  schoolName: string;
  createdAt: string;
  updatedAt?: string | null;
}

// Phản hồi khi Reset Mật khẩu (chung cho cả 3 vai trò)
export interface ResetPasswordResponse {
  uid: string;
  userName: string;
  fullName: string;
  email: string;
  initialPassword: string;      // Mật khẩu mới dạng plaintext để FE hiển thị/sao chép
  message: string;
}
```

### 2.2 Request Payloads
```typescript
// Cập nhật School Admin
export interface UpdateSchoolAdminRequest {
  fullName: string;             // 2 - 150 ký tự
  email: string;                // Format email hợp lệ
  phoneNumber?: string | null;  // Tối đa 20 ký tự
}

// Cập nhật trạng thái School Admin
export interface UpdateSchoolAdminStatusRequest {
  active: boolean;              // true: hoạt động, false: vô hiệu hóa
}

// Cập nhật Giáo viên
export interface UpdateTeacherRequest {
  fullName: string;
  email: string;
  phoneNumber?: string | null;
}

// Cập nhật trạng thái Giáo viên
export interface UpdateTeacherStatusRequest {
  active: boolean;
}

// Cập nhật Học sinh
export interface UpdateStudentRequest {
  fullName: string;
  dob?: string | null;          // Ngày trong quá khứ
  gender?: "MALE" | "FEMALE" | "OTHER" | null;
  phoneNumber?: string | null;
  email?: string | null;
}

// Cập nhật trạng thái Học sinh
export interface UpdateStudentStatusRequest {
  active: boolean;
}
```

---

## 3. Chi tiết API Endpoints

### 🏢 PHÂN HỆ 1: SYSTEM ADMIN QUẢN LÝ SCHOOL ADMIN (`ROLE_SYSTEM_ADMIN`)

---

#### 1.1 Xem danh sách School Admin của một trường
* **Endpoint:** `GET /api/v1/admin/schools/{schoolId}/school-admins`
* **Query Parameters:**
  * `search` *(string, optional)*: Tìm kiếm theo họ tên, username, email.
  * `active` *(boolean, optional)*: Lọc theo trạng thái (`true` / `false`). Bỏ trống để lấy tất cả.
  * `page` *(number, default: 0)*: Trang hiện tại (0-indexed).
  * `size` *(number, default: 10)*: Số dòng mỗi trang.
  * `sortBy` *(string, default: "createdAt")*: `fullName`, `userName`, `email`, `createdAt`.
  * `sortDirection` *(string, default: "desc")*: `asc` hoặc `desc`.
* **Response:** `200 OK` $\rightarrow$ `PaginatedApiResponse<SchoolAdminResponse>`
```json
{
  "success": true,
  "message": "School Admins retrieved successfully",
  "data": [
    {
      "uid": "1a2b3c4d-...",
      "userName": "lhp_hcm_admin",
      "email": "admin@lhp.edu.vn",
      "fullName": "Nguyễn Văn Hiệu Trưởng",
      "phoneNumber": "0901234567",
      "role": "SCHOOL_ADMIN",
      "schoolId": "e4b2d184-...",
      "schoolName": "Trường THPT Lê Hồng Phong",
      "active": true,
      "createdAt": "2026-10-12T08:00:00Z"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

#### 1.2 Cập nhật thông tin School Admin
* **Endpoint:** `PUT /api/v1/admin/school-admins/{id}`
* **Body:** `UpdateSchoolAdminRequest`
```json
{
  "fullName": "Nguyễn Văn Hiệu Trưởng Mới",
  "email": "hieutruong.moi@lhp.edu.vn",
  "phoneNumber": "0988776655"
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<SchoolAdminResponse>`

---

#### 1.3 Khóa / Mở khóa tài khoản School Admin
* **Endpoint:** `PUT /api/v1/admin/school-admins/{id}/status`
* **Body:** `UpdateSchoolAdminStatusRequest`
```json
{
  "active": false
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<SchoolAdminResponse>`

---

#### 1.4 Xóa (Soft Delete) School Admin
* **Endpoint:** `DELETE /api/v1/admin/school-admins/{id}`
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<null>`
```json
{
  "success": true,
  "message": "School Admin account deleted successfully",
  "data": null,
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

#### 1.5 Đặt lại mật khẩu (Reset Password) School Admin
* **Endpoint:** `POST /api/v1/admin/school-admins/{id}/reset-password`
* **Mô tả:** Hệ thống sinh chuỗi ngẫu nhiên 10 ký tự tiền tố `Ht@...`, cập nhật DB và gắn cờ `mustChangePassword = true`.
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<ResetPasswordResponse>`
```json
{
  "success": true,
  "message": "School Admin password reset successfully",
  "data": {
    "uid": "1a2b3c4d-...",
    "userName": "lhp_hcm_admin",
    "fullName": "Nguyễn Văn Hiệu Trưởng",
    "email": "hieutruong.moi@lhp.edu.vn",
    "initialPassword": "Ht@v8P!nK4",
    "message": "Password reset successfully. Please deliver this temporary password to the School Admin."
  },
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

### 👨‍🏫 PHÂN HỆ 2: SCHOOL ADMIN QUẢN LÝ TEACHER (`ROLE_SCHOOL_ADMIN`)

---

#### 2.1 Xem danh sách Giáo viên
* **Endpoint:** `GET /api/v1/school-admin/teachers`
* **Query Parameters:**
  * `search` *(string, optional)*: Tìm kiếm theo họ tên, username, email.
  * `active` *(boolean, optional)*: Lọc theo trạng thái (`true` / `false`). Bỏ trống để lấy tất cả.
  * `page` *(number, default: 0)*, `size` *(number, default: 10)*.
  * `sortBy` *(string, default: "createdAt")*: `fullName`, `userName`, `email`, `createdAt`.
  * `sortDirection` *(string, default: "desc")*.
* **Response:** `200 OK` $\rightarrow$ `PaginatedApiResponse<TeacherResponse>`

---

#### 2.2 Cập nhật thông tin Giáo viên
* **Endpoint:** `PUT /api/v1/school-admin/teachers/{id}`
* **Body:** `UpdateTeacherRequest`
```json
{
  "fullName": "Trần Thị Giáo Viên Mới",
  "email": "teacher.tran@lhp.edu.vn",
  "phoneNumber": "0911222333"
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<TeacherResponse>`

---

#### 2.3 Khóa / Mở khóa tài khoản Giáo viên
* **Endpoint:** `PUT /api/v1/school-admin/teachers/{id}/status`
* **Body:** `UpdateTeacherStatusRequest`
```json
{
  "active": false
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<TeacherResponse>`

---

#### 2.4 Xóa (Soft Delete) Giáo viên
* **Endpoint:** `DELETE /api/v1/school-admin/teachers/{id}`
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<null>`

---

#### 2.5 Đặt lại mật khẩu (Reset Password) Giáo viên
* **Endpoint:** `POST /api/v1/school-admin/teachers/{id}/reset-password`
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<ResetPasswordResponse>`
```json
{
  "success": true,
  "message": "Teacher password reset successfully",
  "data": {
    "uid": "2b3c4d5e-...",
    "userName": "lhp_hcm_gv_1042",
    "fullName": "Trần Thị Giáo Viên",
    "email": "teacher.tran@lhp.edu.vn",
    "initialPassword": "Ht@k9X#m2P",
    "message": "Teacher password reset successfully. Please deliver this temporary password to the teacher."
  },
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

### 🎓 PHÂN HỆ 3: SCHOOL ADMIN QUẢN LÝ STUDENT (`ROLE_SCHOOL_ADMIN`)

---

#### 3.1 Xem danh sách Học sinh
* **Endpoint:** `GET /api/v1/school-admin/students`
* **Query Parameters:**
  * `search` *(string, optional)*: Tìm kiếm theo mã học sinh (`studentCode`), họ tên, username, email.
  * `active` *(boolean, optional)*: Lọc theo trạng thái (`true` / `false`). Bỏ trống để lấy tất cả.
  * `page` *(number, default: 0)*, `size` *(number, default: 10)*.
  * `sortBy` *(string, default: "createdAt")*: `studentCode`, `fullName`, `userName`, `email`, `token`, `createdAt`.
  * `sortDirection` *(string, default: "desc")*.
* **Response:** `200 OK` $\rightarrow$ `PaginatedApiResponse<StudentResponse>`
```json
{
  "success": true,
  "message": "Students retrieved successfully",
  "data": [
    {
      "uid": "3c4d5e6f-...",
      "studentCode": "LHP_HS2600001",
      "userName": "lhp_hcm_hs_lhphs2600001",
      "fullName": "Nguyễn Hoàng Nam",
      "email": "hs001@saas.historytalk.vn",
      "dob": "2008-05-20",
      "gender": "MALE",
      "phoneNumber": "0911222333",
      "token": 10000,
      "active": true,
      "schoolId": "e4b2d184-...",
      "schoolName": "Trường THPT Lê Hồng Phong",
      "createdAt": "2026-10-09T06:35:00Z",
      "updatedAt": null
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

#### 3.2 Xem chi tiết Học sinh theo ID
* **Endpoint:** `GET /api/v1/school-admin/students/{id}`
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<StudentResponse>`

---

#### 3.3 Cập nhật thông tin Học sinh
* **Endpoint:** `PUT /api/v1/school-admin/students/{id}`
* **Body:** `UpdateStudentRequest`
```json
{
  "fullName": "Nguyễn Hoàng Nam Cập Nhật",
  "dob": "2008-05-20",
  "gender": "MALE",
  "phoneNumber": "0988112233",
  "email": "nam.nguyen@gmail.com"
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<StudentResponse>`

---

#### 3.4 Khóa / Mở khóa tài khoản Học sinh
* **Endpoint:** `PUT /api/v1/school-admin/students/{id}/status`
* **Body:** `UpdateStudentStatusRequest`
```json
{
  "active": false
}
```
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<StudentResponse>`

---

#### 3.5 Xóa (Soft Delete) Học sinh
* **Endpoint:** `DELETE /api/v1/school-admin/students/{id}`
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<null>`

---

#### 3.6 Đặt lại mật khẩu (Reset Password) Học sinh
* **Endpoint:** `POST /api/v1/school-admin/students/{id}/reset-password`
* **Mô tả:** Khôi phục mật khẩu về quy chuẩn mặc định `{studentCode}@2026` (VD: `LHP_HS2600001@2026`) và gắn cờ `mustChangePassword = true`.
* **Response:** `200 OK` $\rightarrow$ `ApiResponse<ResetPasswordResponse>`
```json
{
  "success": true,
  "message": "Student password reset successfully",
  "data": {
    "uid": "3c4d5e6f-...",
    "userName": "lhp_hcm_hs_lhphs2600001",
    "fullName": "Nguyễn Hoàng Nam",
    "email": "hs001@saas.historytalk.vn",
    "initialPassword": "LHP_HS2600001@2026",
    "message": "Student password reset successfully. Default pattern is {studentCode}@2026."
  },
  "timestamp": "2026-10-12T09:00:00Z",
  "errorCode": null
}
```

---

## 4. Xử lý Lỗi (Error Handling) Cho Frontend

| HTTP Status | Error Code | Lý do | Hành vi FE khuyến nghị |
| :---: | :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_FAILED` | Sai định dạng email, ngày sinh không nằm trong quá khứ... | Hiển thị lỗi đỏ bên dưới field tương ứng trên Form. |
| `403 Forbidden` | `ACCESS_DENIED` | Không đúng Role yêu cầu hoặc token bị thu hồi. | Cảnh báo không có quyền, điều hướng về trang chủ. |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | ID không tồn tại hoặc tài khoản không thuộc về trường hiện tại (Cross-tenant). | Bật toast thông báo không tìm thấy hoặc tài khoản không thuộc trường của bạn. |
| `409 Conflict` | `DATA_CONFLICT` | Email đã được sử dụng bởi người dùng khác trong hệ thống. | Bật thông báo lỗi trùng email để người dùng nhập email khác. |
