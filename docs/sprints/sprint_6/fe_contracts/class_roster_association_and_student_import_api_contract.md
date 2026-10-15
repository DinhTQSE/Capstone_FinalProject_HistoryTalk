# Class Roster Association & Student Import API Contract (Sprint 6)

> **Sprint:** Sprint 6 (11/10/2026 – 17/10/2026)  
> **Story ID:** `US-SP6-01`  
> **Người biên soạn:** 🟢 Võ Đồng Đức Khải (`KhaiVDD`)  
> **Dành cho:** Frontend Developers (Web Admin & School Portal)  
> **Base URL (Local):** `http://localhost:8080/Historical-tell`  
> **Swagger UI:** `http://localhost:8080/Historical-tell/api/v1/swagger-ui`  
> **Authentication Header:** `Authorization: Bearer <access_token>`  
> **Content-Type:** `application/json` (hoặc `multipart/form-data` cho upload CSV)

---

## 1. Response Wrappers & Common Interfaces

### 1.1 Single Item Response Wrapper
```typescript
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  errorCode?: string | null;
}
```

### 1.2 Paginated List Response Wrapper (`PaginatedApiResponse<T>`)
```typescript
export interface PaginationMeta {
  page: number;          // 0-indexed
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PaginatedApiResponse<T> {
  success: boolean;
  message: string;
  data: T[];
  pagination: PaginationMeta;
  timestamp: string;
  errorCode?: string | null;
}
```

---

## 2. TypeScript Types & Data Models

### 2.1 Model Dữ liệu Học sinh trong Lớp (Classroom Roster)
```typescript
export interface ClassroomStudentResponse {
  studentId: string;           // UUID
  studentCode: string;         // VD: "HS260101" hoặc "CVA_HS2600001"
  userName: string;            // VD: "cva_hs_hs260101"
  fullName: string;            // VD: "Nguyễn Văn An"
  email: string;
  dob?: string | null;         // YYYY-MM-DD
  gender?: "MALE" | "FEMALE" | "OTHER" | null;
  phoneNumber?: string | null;
  token: number;               // Số token khả dụng hiện tại
  joinedAt: string;            // ISO 8601
  status: "ACTIVE" | "DROPPED";
}
```

### 2.2 Model Học sinh Khả dụng (`StudentResponse`)
```typescript
export interface StudentResponse {
  uid: string;                 // UUID học sinh
  studentCode: string;         // Mã định danh (VD: "HS260001")
  userName: string;            // Username đăng nhập
  fullName: string;            // Họ và tên
  email: string;               // Email học sinh
  dob?: string | null;         // Ngày sinh (YYYY-MM-DD)
  gender?: "MALE" | "FEMALE" | "OTHER" | null;
  phoneNumber?: string | null;
  token: number;               // Số dư token AI khả dụng
  active: boolean;             // Trạng thái tài khoản (true = hoạt động)
  schoolId: string;            // UUID trường học
  schoolName: string;          // Tên trường học
  createdAt: string;           // ISO 8601
  updatedAt?: string | null;   // ISO 8601
}
```

### 2.3 Request & Response Gán Hàng Loạt (Batch Enroll)
```typescript
export interface BatchEnrollStudentRequest {
  studentIds: string[];        // Danh sách UUID học sinh cần thêm vào lớp
}

export interface BatchEnrollResultResponse {
  classroomId: string;
  className: string;
  totalRequested: number;      // Tổng số ID gửi lên
  successCount: number;        // Số học sinh gán thành công
  alreadyEnrolledCount: number;// Số học sinh đã ở trong lớp từ trước
  currentStudentCount: number; // Sĩ số hiện tại của lớp
  notes: string[];             // Danh sách ghi chú/cảnh báo chi tiết
}
```

### 2.4 Request Chuyển Lớp (Transfer Student)
```typescript
export interface TransferStudentRequest {
  targetClassroomId: string;   // UUID lớp học đích chuyển đến
  studentIds: string[];        // Danh sách UUID học sinh chuyển lớp
}
```

### 2.5 Model Kết quả Import File CSV (`StudentImportResultDto`)
```typescript
export interface ImportErrorDetail {
  rowNumber: number;
  studentCode?: string;
  field: string;
  message: string;
}

export interface ImportedStudentAccountDto {
  uid: string;
  studentCode: string;
  userName: string;
  fullName: string;
  email: string;
  allocatedToken: number;
  initialPassword?: string;    // Plaintext mật khẩu ban đầu
}

export interface StudentImportResultDto {
  totalRows: number;
  successCount: number;
  failureCount: number;
  remainingSchoolTokens: number;
  errors: ImportErrorDetail[];
  successfulAccounts: ImportedStudentAccountDto[];
}
```

---

## 3. Danh sách API Endpoints Chi Tiết

### 3.1 Gán nhanh Học sinh vào Lớp bằng danh sách ID (Batch Enroll)
* **Method:** `POST`
* **Path:** `/api/v1/classrooms/{classId}/students/batch`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER` *(Teacher chỉ được gán vào lớp mình phụ trách)*
* **Xử lý Idempotent:** Nếu học sinh đã có trong lớp, hệ thống tự động bỏ qua và tăng `alreadyEnrolledCount`, không làm vỡ batch.

**Request Payload:**
```json
{
  "studentIds": [
    "ee30a4d1-dc98-4e0a-b3d4-f727f36c4cd9",
    "3bc390f2-b62e-4b3a-a3d8-0c330ba2f2ef"
  ]
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Thêm học sinh vào lớp học thành công",
  "data": {
    "classroomId": "555a6831-359b-4909-86f2-d336b3b2da2c",
    "className": "10A1",
    "totalRequested": 2,
    "successCount": 2,
    "alreadyEnrolledCount": 0,
    "currentStudentCount": 38,
    "notes": []
  },
  "timestamp": "2026-10-13T14:15:00.000"
}
```

---

### 3.2 Tải File CSV Import & Gán Học sinh vào Lớp (Smart Roster Sync)
* **Method:** `POST`
* **Path:** `/api/v1/classrooms/{classId}/students/import-csv`
* **Content-Type:** `multipart/form-data`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
* **File mẫu:** Sử dụng [`student_classroom_enrollment_test_cases.csv`](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/sprints/sprint_6/user_stories/student_classroom_enrollment_test_cases.csv)
* **Cơ chế xử lý:**
  - **Học sinh đã có tài khoản trong trường:** Tự động gán vào lớp, **không trừ thêm quota token**, giữ nguyên mật khẩu cũ.
  - **Học sinh mới tinh:** Tự tạo tài khoản, trừ token từ quỹ trường, gán luôn vào lớp và trả về plaintext password.

> [!NOTE]
> **Phân biệt 2 API Import CSV học sinh:**
> 1. `POST /api/v1/classrooms/{classId}/students/import-csv` (API này): Dành cho School Admin & Giáo viên import và **gán thẳng học sinh vào lớp học `{classId}`**.
> 2. `POST /api/v1/school-admin/students/import-csv`: Dành cho School Admin import danh sách học sinh **quy mô toàn trường** (chưa phân lớp).

**Form Data Params:**
- `file`: File `.csv` (bắt buộc)
- `defaultInitialToken`: Số token cấp cho học sinh mới (tùy chọn, mặc định: 10,000)

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Nhập dữ liệu thành công: 5 học sinh hợp lệ, 0 dòng lỗi.",
  "data": {
    "totalRows": 5,
    "successCount": 5,
    "failureCount": 0,
    "remainingSchoolTokens": 38000,
    "errors": [],
    "successfulAccounts": [
      {
        "uid": "a1b2c3d4-...",
        "studentCode": "HS260101",
        "userName": "cva_hs_hs260101",
        "fullName": "Trần Bảo Nam",
        "email": "cva_hs_hs260101@saas.historytalk.vn",
        "allocatedToken": 0,
        "initialPassword": "(Tài khoản hiện có)"
      },
      {
        "uid": "b2c3d4e5-...",
        "studentCode": "HS260201",
        "userName": "cva_hs_hs260201",
        "fullName": "Nguyễn Khánh Linh",
        "email": "cva_hs_hs260201@saas.historytalk.vn",
        "allocatedToken": 10000,
        "initialPassword": "HS260201@2026"
      }
    ]
  }
}
```

---

### 3.3 Lấy Danh sách Học sinh trong Lớp (Class Roster Listing)
* **Method:** `GET`
* **Path:** `/api/v1/classrooms/{classId}/students`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
* **Query Params:**
  | Param | Type | Required | Mô tả |
  |---|---|---|---|
  | `search` | string | Không | Tìm kiếm theo họ tên, mã HS, username hoặc email |
  | `page` | number | Không | Trang bắt đầu (0-indexed, mặc định: 0) |
  | `size` | number | Không | Số lượng mỗi trang (mặc định: 10) |
  | `sort` | string | Không | Sắp xếp (mặc định: `joinedAt,desc`) |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Danh sách học sinh trong lớp",
  "data": [
    {
      "studentId": "ee30a4d1-dc98-4e0a-b3d4-f727f36c4cd9",
      "studentCode": "HS260101",
      "userName": "cva_hs_hs260101",
      "fullName": "Trần Bảo Nam",
      "email": "cva_hs_hs260101@saas.historytalk.vn",
      "dob": "2009-05-15",
      "gender": "MALE",
      "phoneNumber": "0912345601",
      "token": 10000,
      "joinedAt": "2026-10-13T14:02:19",
      "status": "ACTIVE"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 38,
    "totalPages": 4,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### 3.4 Lấy Danh sách Học sinh Khả dụng để thêm vào Lớp (Available Pool)
* **Method:** `GET`
* **Path:** `/api/v1/classrooms/{classId}/available-students`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
* **Mô tả:** Lọc tất cả học sinh trong trường **chưa tham gia lớp học này** để FE hiển thị trong Modal/Dropdown chọn học sinh cần thêm vào lớp.
* **Query Params:**
  | Param | Type | Required | Mô tả |
  |---|---|---|---|
  | `search` | string | Không | Tìm kiếm theo họ tên, mã HS, username hoặc email |
  | `page` | number | Không | Trang bắt đầu (0-indexed, mặc định: 0) |
  | `size` | number | Không | Số lượng mỗi trang (mặc định: 10) |
  | `sort` | string | Không | Sắp xếp (mặc định: `createdAt,desc`) |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Danh sách học sinh khả dụng để thêm vào lớp",
  "data": [
    {
      "uid": "3bc390f2-b62e-4b3a-a3d8-0c330ba2f2ef",
      "studentCode": "HS260102",
      "userName": "cva_hs_hs260102",
      "fullName": "Lê Hoàng Long",
      "email": "cva_hs_hs260102@saas.historytalk.vn",
      "dob": "2009-08-20",
      "gender": "MALE",
      "phoneNumber": "0912345602",
      "token": 10000,
      "active": true,
      "schoolId": "d3b07384-d113-4e4e-8622-c3a44f2ef31a",
      "schoolName": "THPT Chuyên Chu Văn An",
      "createdAt": "2026-10-12T08:30:00",
      "updatedAt": "2026-10-12T08:30:00"
    }
  ],
  "pagination": {
    "page": 0,
    "size": 10,
    "totalElements": 15,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-10-15T14:00:00.000",
  "errorCode": null
}
```

---

### 3.5 Gỡ Học sinh khỏi Lớp học (Remove Student)
* **Method:** `DELETE`
* **Path:** `/api/v1/classrooms/{classId}/students/{studentId}`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
* **Mô tả:** Xóa liên kết khỏi `class_student`, bảo toàn tài khoản và lịch sử nộp bài tập.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Gỡ học sinh khỏi lớp học thành công",
  "data": null,
  "timestamp": "2026-10-15T14:10:00.000",
  "errorCode": null
}
```

---

### 3.6 Chuyển Học sinh giữa các Lớp học (Transfer Student)
* **Method:** `POST`
* **Path:** `/api/v1/classrooms/{fromClassId}/students/transfer`
* **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
* **Mô tả:** Gỡ học sinh khỏi lớp cũ và thêm vào lớp mới trong cùng 1 Transaction đảm bảo toàn vẹn dữ liệu.

**Request Payload:**
```json
{
  "targetClassroomId": "888b1111-2222-3333-4444-555566667777",
  "studentIds": [
    "ee30a4d1-dc98-4e0a-b3d4-f727f36c4cd9"
  ]
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Chuyển lớp học sinh thành công",
  "data": {
    "classroomId": "888b1111-2222-3333-4444-555566667777",
    "className": "10A2",
    "totalRequested": 1,
    "successCount": 1,
    "alreadyEnrolledCount": 0,
    "currentStudentCount": 35,
    "notes": []
  },
  "timestamp": "2026-10-15T14:15:00.000",
  "errorCode": null
}
```

---

## 4. Bảng Mã Lỗi & Hướng Dẫn Xử Lý Cho Frontend

| HTTP Status | Điều kiện phát sinh | `message` từ server | Hướng xử lý cho Frontend |
| :--- | :--- | :--- | :--- |
| **400 Bad Request** | Quỹ token trường không đủ cho học sinh mới | `"Insufficient unallocated token quota: Requires X tokens..."` | Hiển thị Toast cảnh báo số dư token không đủ; gợi ý School Admin mua thêm gói token. |
| **400 Bad Request** | Lớp đích trùng với lớp hiện tại khi chuyển lớp | `"Lớp học đích không được trùng với lớp học hiện tại"` | Chặn chọn cùng lớp trong dropdown đích. |
| **403 Forbidden** | Giáo viên thao tác trên lớp mình không phụ trách | `"Bạn không được phân công phụ trách lớp học này!"` | Ẩn các nút hành động (Add/Remove/Transfer) hoặc điều hướng về trang chủ. |
| **403 Forbidden** | Thao tác học sinh thuộc trường khác | `"User does not belong to any school tenant"` | Đăng xuất hoặc chuyển về màn hình đăng nhập. |
| **404 Not Found** | Không tìm thấy ID lớp học hoặc học sinh | `"Classroom not found with ID: ..."` / `"Học sinh không có trong lớp học này"` | Refresh lại danh sách thành viên lớp. |

---

## 5. Hướng Dẫn Tích Hợp Giao Diện (Frontend UI/UX Guidelines)

1. **Màn hình Danh sách Thành viên Lớp (Class Detail -> Tab "Học sinh"):**
   - Gọi `GET /classrooms/{classId}/students` để hiển thị Table danh sách học sinh: Mã HS, Họ tên, Username, Email, Ngày vào lớp, Nút **"Gỡ khỏi lớp"** và **"Chuyển lớp"**.
   - Có thanh tìm kiếm `search` debounce 300ms.
2. **Nút "Thêm học sinh" (Modal chọn học sinh):**
   - Mở Modal: Gọi `GET /classrooms/{classId}/available-students` hiển thị bảng học sinh trong trường chưa vào lớp.
   - Cho phép tick chọn nhiều checkbox $\rightarrow$ Bấm nút "Xác nhận thêm" $\rightarrow$ Gọi `POST /classrooms/{classId}/students/batch`.
3. **Nút "Import Danh sách Lớp (CSV)":**
   - Khu vực Drag & Drop file `.csv`.
   - Có nút tải file mẫu: `student_classroom_enrollment_test_cases.csv`.
   - Sau khi upload xong, hiển thị popup kết quả: Số học sinh đã thêm thành công, số dòng lỗi (nếu có).
4. **Nút "Chuyển lớp":**
   - Khi chọn 1 hoặc nhiều học sinh trên bảng $\rightarrow$ Bấm "Chuyển lớp" $\rightarrow$ Hiển thị dropdown chọn lớp đích trong cùng trường $\rightarrow$ Gọi `POST /classrooms/{fromClassId}/students/transfer`.
