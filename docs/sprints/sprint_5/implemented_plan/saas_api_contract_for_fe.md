# 📄 API CONTRACT & INTEGRATION GUIDE FOR FRONTEND
> **Phân hệ:** SaaS B2B Multi-tenancy & Quản lý Tài khoản Trường học (Sprint 5)  
> **Người biên soạn:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Target Audience:** Frontend Developers (Web / Mobile)  
> **Phiên bản:** `v1.0.0` (Cập nhật ngày 09/10/2026)

---

## 📌 1. THÔNG TIN KẾT NỐI CHUNG (GENERAL CONNECTION)

* **Base URL:** `http://localhost:8080/Historical-tell` (hoặc cấu hình qua biến môi trường `VITE_API_BASE_URL`)
* **Swagger UI:** `http://localhost:8080/Historical-tell/api/v1/swagger-ui`
* **Authentication Header:** Bắt buộc gửi kèm JWT Bearer Token đối với các API yêu cầu đăng nhập:
  ```http
  Authorization: Bearer <access_token>
  ```
* **Content-Type:**
  - Mặc định: `application/json`
  - Riêng API Import file: `multipart/form-data`

---

## 🛡️ 2. MA TRẬN PHÂN QUYỀN (ROLES & PERMISSIONS)

| Role Code | Tên vai trò | Phạm vi truy cập API | Ghi chú |
| :--- | :--- | :--- | :--- |
| `ROLE_SYSTEM_ADMIN` | Quản trị viên Hệ thống | `/api/v1/admin/**` | Quản lý trường học, hợp đồng B2B, tạo School Admin. |
| `ROLE_SCHOOL_ADMIN` | Quản trị viên Trường | `/api/v1/school-admin/**` | Quản lý giáo viên, import học sinh, kiểm tra hạn mức token trường. |
| `ROLE_TEACHER` | Giáo viên | `/api/v1/teacher/**` | Quản lý lớp học, giao bài quiz (Sprint kế tiếp). |
| `ROLE_SCHOOL_STUDENT` | Học sinh trường | `/api/v1/student/**` | Học lịch sử, làm bài tập theo lớp (Sprint kế tiếp). |

---

## 📦 3. CẤU TRÚC PHẢN HỒI CHUẨN (STANDARD RESPONSE SCHEMAS)

Tất cả các API được chuẩn hóa thành 2 dạng cấu trúc phản hồi:

### 3.1. Dạng Đơn Lẻ (Single Item / Action Response)
```typescript
interface ApiResponse<T> {
  success: boolean;       // true nếu thành công, false nếu có lỗi
  message: string;        // Thông điệp phản hồi
  data: T;                // Dữ liệu trả về (hoặc null)
  timestamp: string;      // ISO-8601 timestamp (VD: "2026-10-09T06:30:00Z")
  errorCode: string | null; // Mã lỗi nếu có (VD: "DATA_CONFLICT", "RESOURCE_NOT_FOUND")
}
```

### 3.2. Dạng Danh Sách Phân Trang (Paginated Response)
> **Lưu ý quan trọng cho FE:** Mảng danh sách nằm trực tiếp ở trường `data: T[]`, không bị lồng sâu. Metadata phân trang nằm riêng ở object `pagination`.
```typescript
interface PaginatedApiResponse<T> {
  success: boolean;
  message: string;
  data: T[];              // Mảng danh sách kết quả trực tiếp
  pagination: {
    page: number;         // Trang hiện tại (bắt đầu từ 0)
    size: number;         // Số phần tử trên mỗi trang
    totalElements: number;// Tổng số bản ghi tìm thấy
    totalPages: number;   // Tổng số trang
    hasNext: boolean;     // Có trang kế tiếp hay không
    hasPrevious: boolean; // Có trang trước hay không
  };
  timestamp: string;
  errorCode: string | null;
}
```

---

## 🚀 4. CHI TIẾT CONTRACT CÁC API

### 🏢 PHÂN HỆ 1: SYSTEM ADMIN (`/api/v1/admin/**`)

---

#### 1.1. Tạo Trường Học Mới & Khởi Tạo Gói Token Enterprise
* **Method & Endpoint:** `POST /api/v1/admin/schools`
* **Authorization:** `Bearer Token` (`ROLE_SYSTEM_ADMIN`)
* **Request Body:**
  ```json
  {
    "name": "Trường THPT Lê Hồng Phong",
    "schoolCode": "LHP-HCM", 
    "address": "280 An Dương Vương, Phường 4, Quận 5, TP. Hồ Chí Minh",
    "contactEmail": "admin@thpt-lehongphong.edu.vn",
    "contactPhone": "02838398506",
    "packageType": "ENTERPRISE_SMALL",
    "localHistoryPolicyAccepted": true
  }
  ```
  *Giải thích fields:*
  - `name` *(bắt buộc, 3 - 200 ký tự)*: Tên đầy đủ của trường học.
  - `schoolCode` *(tùy chọn)*: Nếu FE để trống `null` hoặc `""`, Backend sẽ tự động phân tích Tên + Tỉnh/Thành từ Địa chỉ để sinh mã chuẩn kèm bộ đếm chống trùng (VD: `LHP-HCM`, `LHP-HCM-01`...). Nếu nhập tay, format regex: `^[A-Za-z0-9_-]{2,30}$`.
  - `packageType` *(bắt buộc)*: Một trong 3 enum:
    - `"ENTERPRISE_SMALL"`: Hạn mức **5.000.000** Tokens/tháng.
    - `"ENTERPRISE_MEDIUM"`: Hạn mức **20.000.000** Tokens/tháng.
    - `"ENTERPRISE_LARGE"`: Hạn mức **50.000.000** Tokens/tháng.
  - `localHistoryPolicyAccepted` *(bắt buộc)*: Phải là `true` (xác nhận đồng ý điều khoản lịch sử địa phương).
* **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "School and token quota initialized successfully",
    "data": {
      "id": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
      "name": "Trường THPT Lê Hồng Phong",
      "schoolCode": "LHP-HCM",
      "address": "280 An Dương Vương, Phường 4, Quận 5, TP. Hồ Chí Minh",
      "contactEmail": "admin@thpt-lehongphong.edu.vn",
      "contactPhone": "02838398506",
      "packageType": "ENTERPRISE_SMALL",
      "totalSchoolTokenQuota": 5000000,
      "unallocatedTokenQuota": 5000000,
      "status": "ACTIVE",
      "localHistoryPolicyAccepted": true,
      "createdAt": "2026-10-09T06:15:30Z"
    },
    "timestamp": "2026-10-09T06:15:30Z",
    "errorCode": null
  }
  ```

---

#### 1.2. Tra Cứu Danh Sách Trường Học (Phân Trang & Tìm Kiếm)
* **Method & Endpoint:** `GET /api/v1/admin/schools`
* **Authorization:** `Bearer Token` (`ROLE_SYSTEM_ADMIN`)
* **Query Parameters:**
  - `search` *(string, tùy chọn)*: Từ khóa tìm kiếm theo tên trường, mã trường hoặc email liên hệ.
  - `page` *(number, mặc định `0`)*: Chỉ số trang (bắt đầu từ 0).
  - `size` *(number, mặc định `10`)*: Số bản ghi mỗi trang.
  - `sortBy` *(string, mặc định `createdAt`)*: Trường sắp xếp (`createdAt`, `name`, `schoolCode`, `totalSchoolTokenQuota`).
  - `sortDirection` *(string, mặc định `desc`)*: Chiều sắp xếp (`asc` hoặc `desc`).
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Schools retrieved successfully",
    "data": [
      {
        "id": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
        "name": "Trường THPT Lê Hồng Phong",
        "schoolCode": "LHP-HCM",
        "address": "280 An Dương Vương, Phường 4, Quận 5, TP. Hồ Chí Minh",
        "contactEmail": "admin@thpt-lehongphong.edu.vn",
        "contactPhone": "02838398506",
        "packageType": "ENTERPRISE_SMALL",
        "totalSchoolTokenQuota": 5000000,
        "unallocatedTokenQuota": 3500000,
        "status": "ACTIVE",
        "localHistoryPolicyAccepted": true,
        "createdAt": "2026-10-09T06:15:30Z"
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
    "timestamp": "2026-10-09T06:20:00Z",
    "errorCode": null
  }
  ```

---

#### 1.3. Xem Chi Tiết Trường Học Theo ID
* **Method & Endpoint:** `GET /api/v1/admin/schools/{id}`
* **Authorization:** `Bearer Token` (`ROLE_SYSTEM_ADMIN`)
* **Path Variable:** `id` *(UUID của trường học)*
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "School details retrieved successfully",
    "data": {
      "id": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
      "name": "Trường THPT Lê Hồng Phong",
      "schoolCode": "LHP-HCM",
      "address": "280 An Dương Vương, Phường 4, Quận 5, TP. Hồ Chí Minh",
      "contactEmail": "admin@thpt-lehongphong.edu.vn",
      "contactPhone": "02838398506",
      "packageType": "ENTERPRISE_SMALL",
      "totalSchoolTokenQuota": 5000000,
      "unallocatedTokenQuota": 3500000,
      "status": "ACTIVE",
      "localHistoryPolicyAccepted": true,
      "createdAt": "2026-10-09T06:15:30Z"
    },
    "timestamp": "2026-10-09T06:21:00Z",
    "errorCode": null
  }
  ```

---

#### 1.4. Cấp Tài Khoản School Admin (Bàn Giao Cho Trường)
* **Method & Endpoint:** `POST /api/v1/admin/school-admins`
* **Authorization:** `Bearer Token` (`ROLE_SYSTEM_ADMIN`)
* **Request Body:**
  ```json
  {
    "schoolId": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
    "fullName": "Nguyễn Văn Hiệu Trưởng",
    "email": "hieutruong@thpt-lehongphong.edu.vn",
    "phoneNumber": "0901234567"
  }
  ```
* **Response (201 Created):**
  > **Lưu ý quan trọng cho FE:** Backend sẽ tự sinh `userName` định danh chuẩn theo mã trường (VD: `lhp_hcm_admin`) và tạo mật khẩu tạm thời ngẫu nhiên an toàn trả về ở `initialPassword`. FE cần hiển thị thông báo popup hoặc modal cho System Admin sao chép bàn giao.
  ```json
  {
    "success": true,
    "message": "School Admin account created successfully",
    "data": {
      "uid": "a1b2c3d4-0000-0000-0000-112233445566",
      "userName": "lhp_hcm_admin",
      "email": "hieutruong@thpt-lehongphong.edu.vn",
      "fullName": "Nguyễn Văn Hiệu Trưởng",
      "phoneNumber": "0901234567",
      "role": "SCHOOL_ADMIN",
      "schoolId": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
      "schoolName": "Trường THPT Lê Hồng Phong",
      "initialPassword": "Ht@k9X#m2P",
      "createdAt": "2026-10-09T06:25:00Z"
    },
    "timestamp": "2026-10-09T06:25:00Z",
    "errorCode": null
  }
  ```

---

### 🏫 PHÂN HỆ 2: SCHOOL ADMIN (`/api/v1/school-admin/**`)

---

#### 2.1. Tra Cứu Hạn Mức Token Hiện Tại Của Trường (Dashboard Quota Widget)
* **Method & Endpoint:** `GET /api/v1/school-admin/token-quota`
* **Authorization:** `Bearer Token` (`ROLE_SCHOOL_ADMIN`)
* **Mô tả:** Sử dụng hiển thị widget/thanh tiến trình phân bổ token trên Dashboard của School Admin.
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "School token quota retrieved successfully",
    "data": {
      "schoolId": "e4b2d184-7a91-4c11-9a74-d4b97d2643a1",
      "schoolName": "Trường THPT Lê Hồng Phong",
      "schoolCode": "LHP-HCM",
      "packageType": "ENTERPRISE_SMALL",
      "totalSchoolTokenQuota": 5000000,
      "unallocatedTokenQuota": 3500000,
      "allocatedTokenQuota": 1500000,
      "usagePercentage": 30.0
    },
    "timestamp": "2026-10-09T06:26:00Z",
    "errorCode": null
  }
  ```
  *Ý nghĩa dữ liệu cho UI:*
  - `totalSchoolTokenQuota`: Tổng số token mua theo gói (VD: 5,000,000).
  - `unallocatedTokenQuota`: Số token khả dụng còn lại để cấp cho các đợt import học sinh mới (VD: 3,500,000).
  - `allocatedTokenQuota`: Số token đã cấp phát cho học sinh/giáo viên (VD: 1,500,000).
  - `usagePercentage`: Tỷ lệ % đã phân bổ để vẽ thanh Progress Bar (VD: `30.0%`).

---

#### 2.2. Danh Sách Giáo Viên Trong Trường (Tìm Kiếm & Phân Trang)
* **Method & Endpoint:** `GET /api/v1/school-admin/teachers`
* **Authorization:** `Bearer Token` (`ROLE_SCHOOL_ADMIN`)
* **Query Parameters:**
  - `search` *(string, tùy chọn)*: Tìm kiếm theo họ tên, tên tài khoản (`userName`), hoặc email.
  - `page` *(number, mặc định `0`)*: Chỉ số trang.
  - `size` *(number, mặc định `10`)*: Số giáo viên mỗi trang.
  - `sortBy` *(string, mặc định `createdAt`)*: Trường sắp xếp (`fullName`, `userName`, `email`, `createdAt`).
  - `sortDirection` *(string, mặc định `desc`)*: Chiều sắp xếp (`asc` hoặc `desc`).
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Teachers retrieved successfully",
    "data": [
      {
        "uid": "b2c3d4e5-1111-2222-3333-444455556666",
        "userName": "lhp_hcm_gv_1042",
        "fullName": "Trần Thị Lịch Sử",
        "email": "teacher.tran@thpt-lehongphong.edu.vn",
        "phoneNumber": "0918765432",
        "active": true,
        "initialPassword": null,
        "createdAt": "2026-10-09T06:28:00Z"
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
    "timestamp": "2026-10-09T06:28:00Z",
    "errorCode": null
  }
  ```

---

#### 2.3. Tạo Tài Khoản Giáo Viên Mới
* **Method & Endpoint:** `POST /api/v1/school-admin/teachers`
* **Authorization:** `Bearer Token` (`ROLE_SCHOOL_ADMIN`)
* **Request Body:**
  ```json
  {
    "fullName": "Trần Thị Lịch Sử",
    "email": "teacher.tran@thpt-lehongphong.edu.vn",
    "phoneNumber": "0918765432"
  }
  ```
* **Response (201 Created):**
  ```json
  {
    "success": true,
    "message": "Teacher account created successfully",
    "data": {
      "uid": "b2c3d4e5-1111-2222-3333-444455556666",
      "userName": "lhp_hcm_gv_1042",
      "fullName": "Trần Thị Lịch Sử",
      "email": "teacher.tran@thpt-lehongphong.edu.vn",
      "phoneNumber": "0918765432",
      "active": true,
      "initialPassword": "Pt9@kLm2#q",
      "createdAt": "2026-10-09T06:28:00Z"
    },
    "timestamp": "2026-10-09T06:28:00Z",
    "errorCode": null
  }
  ```

---

#### 2.4. Cập Nhật Trạng Thái Hoạt Động Của Giáo Viên (Khóa / Mở Khóa)
* **Method & Endpoint:** `PUT /api/v1/school-admin/teachers/{id}/status`
* **Authorization:** `Bearer Token` (`ROLE_SCHOOL_ADMIN`)
* **Path Variable:** `id` *(UUID của tài khoản giáo viên)*
* **Request Body:**
  ```json
  {
    "active": false
  }
  ```
  *(Gửi `false` để vô hiệu hóa/khóa tài khoản; gửi `true` để mở khóa kích hoạt lại).*
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Teacher account deactivated successfully",
    "data": {
      "uid": "b2c3d4e5-1111-2222-3333-444455556666",
      "userName": "lhp_hcm_gv_1042",
      "fullName": "Trần Thị Lịch Sử",
      "email": "teacher.tran@thpt-lehongphong.edu.vn",
      "phoneNumber": "0918765432",
      "subjectDepartment": "Lịch sử",
      "active": false,
      "initialPassword": null,
      "createdAt": "2026-10-09T06:28:00Z"
    },
    "timestamp": "2026-10-09T06:30:00Z",
    "errorCode": null
  }
  ```

---

#### 2.5. Import Hàng Loạt Tài Khoản Học Sinh Bằng File CSV
* **Method & Endpoint:** `POST /api/v1/school-admin/students/import-excel`
* **Authorization:** `Bearer Token` (`ROLE_SCHOOL_ADMIN`)
* **Content-Type:** `multipart/form-data`
* **Form-data Parameters:**
  - `file` *(bắt buộc, File .csv)*: File CSV danh sách học sinh.
  - `classCode` *(tùy chọn, string)*: Mã lớp học (VD: `HIS10A1-2026`). Dùng cho **Case 1 (Import theo Lớp)** khi file CSV không có cột `class_code`. Toàn bộ học sinh trong file sẽ được tự động phân vào lớp này.
  - `defaultInitialToken` *(tùy chọn, number)*: Mức token sàn cấp cho mỗi học sinh (VD: `10000`). Nếu không truyền, hệ thống tự động lấy mặc định là `10000`.
* **2 Loại File Mẫu CSV:**
  - **Case 1 (Theo Lớp học):** `docs/sprints/sprint_5/user_stories/student_import_classroom_template.csv` (hoặc bản mẫu 10 học sinh: `docs/sprints/sprint_5/user_stories/student_import_classroom_10_students.csv`). File này **không có cột `class_code`**; FE gửi mã lớp qua param `classCode`.
  - **Case 2 (Toàn Trường):** `docs/sprints/sprint_5/user_stories/student_import_school_template.csv`. File này **có cột `class_code`** ở từng dòng; FE không cần gửi param `classCode`.
* **Cơ Chế Nghiệp Vụ Cần Biết Khi Hiển Thị:**
  1. **Phân bổ mã lớp (Class Code Resolution):** Ưu tiên `class_code` riêng của từng dòng nếu có; nếu dòng để trống sẽ tự động lấy từ param `classCode` truyền ngoài.
  2. **Shadow SaaS Email:** Nếu cột `email` trong file bị để trống, hệ thống tự động gán email ảo `{username}@saas.historytalk.vn` để học sinh có thể đổi Gmail cá nhân khi đăng nhập lần đầu.
  3. **Atomic Quota Check:** Backend kiểm tra tổng số token cần cấp $\sum \text{token} \le \text{unallocatedTokenQuota}$. Nếu vượt quá số dư của trường, request sẽ bị từ chối kèm lỗi `400 INSUFFICIENT_TOKEN_QUOTA`.
* **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Import completed: 2 succeeded, 0 failed. Allocated 25,000 tokens.",
    "data": {
      "successCount": 2,
      "failureCount": 0,
      "totalTokensAllocated": 25000,
      "rows": [
        {
          "rowNumber": 2,
          "studentCode": "LHP_HS2600001",
          "userName": "lhp_hcm_hs_lhphs2600001",
          "email": "lhp_hcm_hs_lhphs2600001@saas.historytalk.vn",
          "fullName": "Nguyễn Hoàng Nam",
          "classCode": "HIS10A1",
          "initialPassword": "Ht@x8Kp9#z",
          "allocatedTokens": 10000,
          "status": "SUCCESS",
          "errorMessage": null
        },
        {
          "rowNumber": 3,
          "studentCode": "LHP_HS2600002",
          "userName": "lhp_hcm_hs_lhphs2600002",
          "email": "le.an@gmail.com",
          "fullName": "Lê Thảo An",
          "classCode": "HIS10A1",
          "initialPassword": "Ht@m3Wq7$r",
          "allocatedTokens": 15000,
          "status": "SUCCESS",
          "errorMessage": null
        }
      ],
      "errors": []
    },
    "timestamp": "2026-10-09T06:35:00Z",
    "errorCode": null
  }
  ```
  *Xử lý trên Frontend:*
  - Cung cấp nút **"Tải về file kết quả"** (hoặc bảng hiển thị danh sách tài khoản kèm `initialPassword`) để School Admin in/phát danh sách mật khẩu tạm thời cho từng học sinh.
  - Nếu có dòng bị lỗi (`failureCount > 0`), hiển thị mảng `errors` với vị trí dòng `rowNumber` và `errorMessage` để người dùng sửa lại file.

---

## ⚠️ 5. BẢNG MÃ LỖI VÀ HƯỚNG DẪN XỬ LÝ (ERROR CODES)

| HTTP Status | Message thường gặp | Nguyên nhân & Hướng dẫn FE xử lý |
| :---: | :--- | :--- |
| `400 Bad Request` | `Validation failed` | Thiếu trường bắt buộc hoặc sai định dạng email, phone, tên. FE cần kiểm tra form validate trước khi submit. |
| `400 Bad Request` | `Insufficient token quota...` | Số token cần cấp cho học sinh vượt quá số dư chưa phân bổ của trường. FE cảnh báo School Admin liên hệ mua thêm gói. |
| `401 Unauthorized` | `Vui lòng đăng nhập...` | Token hết hạn hoặc chưa gửi header `Authorization: Bearer <token>`. FE redirect về màn hình Login. |
| `403 Forbidden` | `Access Denied` | User đăng nhập tài khoản không đúng Role yêu cầu (VD: Teacher cố tình gọi API của School Admin). |
| `404 Not Found` | `School not found...` | ID trường học hoặc giáo viên không tồn tại trong hệ thống. |
| `409 Conflict` | `School code already exists...` | Mã trường học tùy chỉnh đã bị sử dụng bởi trường khác. Nhắc user chọn mã khác hoặc để trống cho hệ thống tự sinh. |
| `409 Conflict` | `Email is already in use...` | Email giáo viên hoặc admin đã tồn tại trong hệ thống. |

---

## 🔑 6. QUY CHUẨN ĐỊNH DANH TÀI KHOẢN & MẬT KHẨU KHỞI TẠO (CREDENTIAL RULES)

Để đảm bảo tính nhất quán trên toàn hệ thống SaaS Multi-tenancy, Backend áp dụng các quy chuẩn tự sinh thông tin đăng nhập như sau:

| Đối tượng | Quy tắc sinh `userName` | Quy tắc sinh `initialPassword` | Cơ chế Chống trùng (Collision) |
| :--- | :--- | :--- | :--- |
| **School Admin** | `{school_code}_admin`<br>*(VD: `lhp_admin`, `lhp_hcm_admin`)* | Chuỗi 10 ký tự prefix `Ht@` ngẫu nhiên<br>*(VD: `Ht@KyLZBGU`)* | Tự động tăng hậu tố sequence nếu trường có thêm admin: `{school_code}_admin_1`, `{school_code}_admin_2`... |
| **Teacher** | `{school_code}_gv_{4_số_ngẫu_nhiên}`<br>*(VD: `lhp_gv_7153`, `lhp_hcm_gv_1042`)* | Chuỗi 10 ký tự prefix `Ht@` ngẫu nhiên<br>*(VD: `Ht@nAScTPY`)* | Tự động kiểm tra trong DB trước khi cấp, đảm bảo duy nhất 100%. |
| **Student** | `{school_code}_hs_{student_code}`<br>*(VD: `lhp_hcm_hs_lhphs2600001`)* | Lấy từ cột `password` trong file CSV. Nếu trống: chuỗi 10 ký tự prefix `Ht@` ngẫu nhiên<br>*(VD: `Ht@x8Kp9#z`)* | Kết hợp prefix mã trường và mã học sinh duy nhất trong trường. |

* **Lưu ý bảo mật (Security Policy):**
  - Tất cả các tài khoản khi vừa được khởi tạo đều được hash bằng BCrypt và gắn cờ `must_change_password = true`.
  - Mật khẩu khởi tạo (`initialPassword`) chỉ được trả về **duy nhất 1 lần** trong response của API tạo mới/import để quản trị viên sao chép hoặc in ấn bàn giao cho người dùng.

