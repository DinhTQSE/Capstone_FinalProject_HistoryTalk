# SaaS Classroom Management & Token Allocation API Contract

> **Base URL (Production):** `https://historytalk.app/Historical-tell/api/v1`  
> **Base URL (Local):** `http://localhost:8080/Historical-tell/api/v1`  
> **Swagger UI:** `https://historytalk.app/Historical-tell/api/v1/swagger-ui/index.html`  
> **Auth:** `Authorization: Bearer <accessToken>`  
> **Response Wrapper:**
> ```typescript
> interface ApiResponse<T> {
>   success: boolean;
>   message: string;
>   data: T;
>   timestamp: string;
>   errorCode?: string;
> }
> ```

---

## 1. TypeScript Types & Data Models

### 1.1 Model Lớp học & Giáo viên

```typescript
export interface TeacherSummaryDto {
  uid: string;
  userName: string;
  fullName: string;
  email?: string;
  phoneNumber?: string;
}

export interface ClassroomResponse {
  id: string; // UUID
  className: string; // VD: "10A1", "12 Chuyên Sử"
  classCode: string; // Mã ngữ nghĩa thông minh: "CVA-10A1-2026"
  gradeLevel: number; // 10, 11, 12
  academicYear: number; // 2026
  status: "ACTIVE" | "INACTIVE" | "ARCHIVED";
  description?: string;
  teacher?: TeacherSummaryDto; // Giáo viên phụ trách (nếu có)
  studentCount: number; // Sĩ số học sinh hiện tại trong lớp
  createdAt: string; // ISO 8601
  updatedAt?: string; // ISO 8601
}
```

### 1.2 Phân trang `PaginatedResponse<T>`

```typescript
export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number; // 0-indexed
  pageSize: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
```

### 1.3 Request Payloads cho Lớp học

```typescript
export interface CreateClassroomRequest {
  className: string; // Bắt buộc, tối đa 100 ký tự (VD: "10A1")
  gradeLevel: number; // Bắt buộc, từ 1 đến 12 (VD: 10)
  academicYear: number; // Bắt buộc, từ 2020 đến 2100 (VD: 2026)
  teacherId?: string; // UUID giáo viên được phân công (tùy chọn)
  description?: string; // Mô tả lớp học (tùy chọn)
}

export interface UpdateClassroomRequest {
  className?: string;
  gradeLevel?: number;
  academicYear?: number;
  teacherId?: string; // Chỉ SCHOOL_ADMIN mới có quyền thay đổi giáo viên
  description?: string;
  status?: "ACTIVE" | "INACTIVE" | "ARCHIVED";
}
```

### 1.4 Request & Response Cấp phát Token

```typescript
export interface AllocateStudentTokenRequest {
  studentId?: string; // Cấp riêng cho 1 học sinh
  studentIds?: string[]; // Cấp hàng loạt cho danh sách học sinh
  tokenAmount: number; // Bắt buộc, tối thiểu 1 token/học sinh
}

export interface AllocateStudentTokenResponse {
  schoolId: string;
  totalTokensAllocated: number; // Tổng số token đã trừ từ quỹ trường
  affectedStudentCount: number; // Số học sinh được cộng token
  tokenAmountPerStudent: number; // Số token mỗi học sinh nhận được
  remainingSchoolTokens: number; // Số token còn lại trong quỹ trường
  message: string;
}
```

---

## 2. API Endpoints Quản lý Lớp học (Classrooms)

### 2.1 Tạo mới Lớp học (US-SP5-06)
- **Method:** `POST`
- **Path:** `/classrooms`
- **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
- **Mô tả:** Hệ thống tự động sinh `classCode` thông minh theo công thức: `{MÃ_TRƯỜNG}-{TÊN_LỚP_CHUẨN_HÓA}-{NĂM_HỌC}` (ví dụ: trường Chu Văn An mã `CVA`, tạo lớp `10A1` năm `2026` -> mã là `CVA-10A1-2026`). Nếu trùng mã, hệ thống tự sinh 3 ký tự an toàn chống đụng độ (ví dụ: `CVA-10A1-2026-X89`).

**Request Body:**
```json
{
  "className": "10A1",
  "gradeLevel": 10,
  "academicYear": 2026,
  "teacherId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "description": "Lớp chuyên lịch sử niên khóa 2026"
}
```

**Response `201 Created`:**
```json
{
  "success": true,
  "message": "Classroom created successfully",
  "data": {
    "id": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
    "className": "10A1",
    "classCode": "CVA-10A1-2026",
    "gradeLevel": 10,
    "academicYear": 2026,
    "status": "ACTIVE",
    "description": "Lớp chuyên lịch sử niên khóa 2026",
    "teacher": {
      "uid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "userName": "cva_gv_01",
      "fullName": "Nguyễn Thị Mai",
      "email": "mai.nt@cva.edu.vn",
      "phoneNumber": "0987654321"
    },
    "studentCount": 0,
    "createdAt": "2026-10-08T22:30:00"
  },
  "timestamp": "2026-10-08T22:30:00.123"
}
```

---

### 2.2 Xem chi tiết Lớp học
- **Method:** `GET`
- **Path:** `/classrooms/{id}`
- **Quyền:** `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`
- **Mô tả:** Lấy thông tin chi tiết lớp học kèm sĩ số học sinh và thông tin giáo viên.
  - School Admin: Xem được bất kỳ lớp nào trong trường.
  - Teacher: Chỉ xem được lớp mình phụ trách.
  - Student: Chỉ xem được lớp mình đã tham gia.

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Classroom details retrieved successfully",
  "data": {
    "id": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
    "className": "10A1",
    "classCode": "CVA-10A1-2026",
    "gradeLevel": 10,
    "academicYear": 2026,
    "status": "ACTIVE",
    "studentCount": 38,
    "teacher": {
      "uid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "fullName": "Nguyễn Thị Mai"
    }
  }
}
```

---

### 2.3 Cập nhật thông tin Lớp học
- **Method:** `PUT`
- **Path:** `/classrooms/{id}`
- **Quyền:** `SCHOOL_ADMIN`, `TEACHER`
- **Lưu ý:** Giáo viên chỉ được sửa thông tin lớp do chính mình phụ trách và không được chuyển nhượng `teacherId` cho người khác. Chỉ School Admin mới có quyền gán lại `teacherId`.

**Request Body:**
```json
{
  "className": "10A1 Chuyên Lịch Sử",
  "description": "Cập nhật thông tin giáo án",
  "status": "ACTIVE"
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Classroom updated successfully",
  "data": { ... }
}
```

---

### 2.4 Xóa (Soft Delete) Lớp học
- **Method:** `DELETE`
- **Path:** `/classrooms/{id}`
- **Quyền:** `SCHOOL_ADMIN`

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Classroom deleted successfully",
  "data": null
}
```

---

## 3. API Truy vấn Lớp học theo vai trò (US-SP5-07)

### 3.1 School Admin xem toàn bộ Lớp học trong trường
- **Method:** `GET`
- **Path:** `/school-admin/classrooms`
- **Quyền:** `SCHOOL_ADMIN`
- **Query Params:**
  | Param | Type | Required | Mô tả |
  |---|---|---|---|
  | `search` | string | Không | Tìm kiếm theo tên lớp (`className`) hoặc mã lớp (`classCode`) |
  | `gradeLevel` | number | Không | Lọc theo khối (10, 11, 12) |
  | `academicYear` | number | Không | Lọc theo năm học (2025, 2026) |
  | `page` | number | Không | Số trang (bắt đầu từ 0, mặc định `0`) |
  | `size` | number | Không | Số phần tử/trang (mặc định `10`) |
  | `sort` | string | Không | Sắp xếp (mặc định `className,asc`) |

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "School classrooms retrieved successfully",
  "data": {
    "content": [
      {
        "id": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
        "className": "10A1",
        "classCode": "CVA-10A1-2026",
        "gradeLevel": 10,
        "academicYear": 2026,
        "status": "ACTIVE",
        "studentCount": 38,
        "teacher": {
          "uid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
          "fullName": "Nguyễn Thị Mai"
        }
      }
    ],
    "totalElements": 25,
    "totalPages": 3,
    "currentPage": 0,
    "pageSize": 10,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

---

### 3.2 Giáo viên xem danh sách lớp mình phụ trách
- **Method:** `GET`
- **Path:** `/teacher/classrooms`
- **Quyền:** `TEACHER`
- **Query Params:** `academicYear` (number, tùy chọn), `page`, `size`
- **Response `200 OK`:** `PaginatedResponse<ClassroomResponse>`

---

### 3.3 Học sinh xem danh sách lớp học của mình
- **Method:** `GET`
- **Path:** `/student/classrooms`
- **Quyền:** `SCHOOL_STUDENT`
- **Response `200 OK`:**
```json
{
  "success": true,
  "message": "Student classrooms retrieved successfully",
  "data": [
    {
      "id": "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a",
      "className": "10A1",
      "classCode": "CVA-10A1-2026",
      "gradeLevel": 10,
      "academicYear": 2026,
      "status": "ACTIVE",
      "studentCount": 38,
      "teacher": {
        "fullName": "Nguyễn Thị Mai"
      }
    }
  ]
}
```

---

## 4. API Phân bổ Token cho Học sinh (US-SP5-05)

### 4.1 Cấp phát Token từ Quỹ Trường
- **Method:** `PUT`
- **Path:** `/school-admin/students/token-allocation`
- **Quyền:** `SCHOOL_ADMIN`
- **Mô tả:** Trừ token từ quỹ trường (`unallocatedTokenQuota`) và cộng trực tiếp vào ví token của học sinh.

**Request Body (Cấp cho 1 học sinh):**
```json
{
  "studentId": "7b13a30c-255d-4f11-9252-01258d4a96b1",
  "tokenAmount": 5000
}
```

**Request Body (Cấp hàng loạt cho nhiều học sinh):**
```json
{
  "studentIds": [
    "7b13a30c-255d-4f11-9252-01258d4a96b1",
    "8e4a910d-311f-4b12-9111-21094f3b11a2"
  ],
  "tokenAmount": 2000
}
```

**Response `200 OK`:**
```json
{
  "success": true,
  "message": "Allocated 4,000 tokens to 2 students successfully",
  "data": {
    "schoolId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
    "totalTokensAllocated": 4000,
    "affectedStudentCount": 2,
    "tokenAmountPerStudent": 2000,
    "remainingSchoolTokens": 46000,
    "message": "Allocated 4,000 tokens to 2 students successfully"
  }
}
```

---

### 4.2 Tải File CSV Mẫu Import Học sinh
- **Method:** `GET`
- **Path:** `/school-admin/students/template`
- **Quyền:** `SCHOOL_ADMIN`
- **Response Headers:**
  - `Content-Type: text/csv; charset=UTF-8`
  - `Content-Disposition: attachment; filename="student_import_template.csv"`
- **Nội dung:** File CSV hỗ trợ ký tự tiếng Việt với UTF-8 BOM chuẩn để mở trực tiếp trong Microsoft Excel mà không bị lỗi font chữ.

---

## 5. Bảng mã lỗi & Hướng dẫn xử lý cho Frontend

| HTTP Status | Điều kiện phát sinh | `message` từ server | Hướng xử lý cho FE |
|---|---|---|---|
| **400 Bad Request** | Quỹ token trường không đủ để cấp | `"Insufficient token quota: Requires X tokens for Y students, but the school only has Z available tokens."` | Hiển thị thông báo Toast cảnh báo số dư quỹ trường không đủ; gợi ý School Admin mua thêm gói token. |
| **400 Bad Request** | Dữ liệu đầu vào không hợp lệ (token <= 0, thiếu trường bắt buộc) | Lỗi validation từ Bean Validation | Hiển thị viền đỏ và text lỗi ngay dưới ô input tương ứng trên form. |
| **403 Forbidden** | Giáo viên xem/sửa lớp không thuộc quyền phụ trách của mình | `"Bạn không được phân công phụ trách lớp học này!"` | Chặn thao tác, điều hướng về danh sách lớp của giáo viên. |
| **403 Forbidden** | Học sinh xem lớp mình chưa tham gia | `"Bạn không phải là thành viên của lớp học này!"` | Ẩn chi tiết, thông báo liên hệ giáo viên để được thêm vào lớp. |
| **404 Not Found** | Không tìm thấy ID lớp học hoặc học sinh | `"Classroom not found with id: ..."` | Hiển thị màn hình 404 hoặc quay lại danh sách. |
| **409 Conflict** | Trùng tên lớp trong cùng năm học của trường | `"Lớp học \"10A1\" đã tồn tại trong năm học 2026 của trường"` | Focus vào ô nhập `className`, báo người dùng chọn tên lớp khác. |

---

## 6. Hướng dẫn Tích hợp Màn hình Frontend (UI Recommendations)

1. **Màn hình Danh sách Lớp học (School Admin):**
   - Sử dụng thẻ Tag hiển thị `classCode` (nổi bật, có nút copy nhanh để giáo viên/học sinh dùng).
   - Hiển thị badge sĩ số `studentCount` học sinh.
   - Thanh tìm kiếm debounce 300ms gọi `GET /school-admin/classrooms?search=...`.
2. **Màn hình Cấp phát Token (School Admin):**
   - Hiển thị widget Số dư Quỹ Trường (`unallocatedTokenQuota`) ngay trên đầu trang.
   - Form cho phép chọn 1 học sinh hoặc tick chọn nhiều học sinh từ table, nhập `tokenAmount`, hiển thị tức thì tính toán: `Tổng token cần dùng = Số học sinh x tokenAmount`.
   - Vô hiệu hóa nút **Xác nhận Cấp phát** nếu `Tổng token > Số dư khả dụng`.
3. **Màn hình Lớp học của tôi (Giáo viên / Học sinh):**
   - Giáo viên gọi `GET /teacher/classrooms` -> chỉ hiển thị các lớp mình dạy.
   - Học sinh gọi `GET /student/classrooms` -> hiển thị các lớp mình tham gia kèm tên giáo viên phụ trách.
