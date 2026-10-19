# 📑 FRONTEND API CONTRACT: SCHOOL SUBSCRIPTION, DAILY TOKEN POOL & ACADEMIC YEAR LIFECYCLE
> **Document Version:** 1.0.0  
> **Sprint:** Sprint 7  
> **Module:** B2B School Subscription (`01/09` – `31/05`), Shared Token Pool with Daily Allowance, Token Boost Workflow & Student Lifecycle (`US-SP7-14` to `US-SP7-18`)  
> **Author:** Trần Quốc Dinh (`DinhTQ`) - Backend Developer & DevOps  
> **Base URL:** `/Historical-tell/api/v1`  
> **Authentication:** Bearer Token (`Authorization: Bearer <jwt_access_token>`)

---

## 1. Overview & Business Rules for Frontend

### 1.1. Two-Level Locking Behavior (`US-SP7-14`)
Frontend cần phân biệt rõ 2 mã lỗi `403 Forbidden` trả về từ Backend:
1. **`SCHOOL_SUBSCRIPTION_EXPIRED` / `SCHOOL_SUBSCRIPTION_NOT_STARTED`:**
   * **Ý nghĩa:** Gói trường học đã hết hạn (sau ngày `31/05` mà chưa gia hạn) hoặc đang trong kỳ nghỉ hè chờ kích hoạt (`01/09`).
   * **Hành vi UI:** **Khóa toàn bộ nền tảng của trường** — Hiển thị màn hình thông báo *"Gói đăng ký năm học của nhà trường đã hết hạn. Vui lòng liên hệ Quản trị viên nhà trường để gia hạn gói năm học mới (01/09 – 31/05)."*
2. **`AI_QUOTA_EXCEEDED`:**
   * **Ý nghĩa:** Trường vẫn còn hạn gói (`01/09` – `31/05`), nhưng học sinh đã dùng hết hạn mức Token ngày (`dailyTokenLimit`) hoặc Quỹ Token chung của trường đã cạn.
   * **Hành vi UI:** **Chỉ khóa tính năng Chat AI** — Hiển thị popup/banner trong khung Chat AI kèm nút **"Xin thêm Token hôm nay"** (`POST /api/v1/student/token-requests`). Các trang Làm Quiz, Đọc Tài liệu Lịch sử, Nộp Bài tập, Xem Dashboard vẫn hoạt động bình thường 100%.
3. **`ACCOUNT_DEACTIVATED`:**
   * **Ý nghĩa:** Tài khoản học sinh đã bị vô hiệu hóa do đã tốt nghiệp ra trường (Khối 12) hoặc chuyển trường.
   * **Hành vi UI:** Chặn đăng nhập và đăng xuất ngay khỏi ứng dụng.

---

## 2. API Endpoints Specification

### 2.1. Gia hạn Gói Trường học trong Tháng 5 (`US-SP7-14`)
* **Endpoint:** `POST /api/v1/admin/schools/{id}/renew`
* **Role:** `ROLE_SYSTEM_ADMIN`
* **Mô tả:** Gia hạn gói cho trường học trong tháng 5 (`01/05` – `31/05`). Gói mới sẽ được kích hoạt từ ngày `01/09` đầu năm học mới kèm bảo lưu tối đa 20% token dư.

#### Request Body
```json
{
  "packageType": "ENTERPRISE_MEDIUM"
}
```

#### Response (`200 OK`)
```json
{
  "code": 200,
  "message": "School subscription renewed for next academic year",
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "THPT Chuyên Lê Hồng Phong",
    "schoolCode": "LHP-HCM",
    "packageType": "ENTERPRISE_SMALL",
    "recommendedStudents": 350,
    "totalSchoolTokenQuota": 5000000,
    "unallocatedTokenQuota": 1000000,
    "defaultDailyStudentQuota": 1000,
    "subscriptionStartDate": "2025-09-01",
    "subscriptionEndDate": "2026-05-31",
    "renewedForNextYear": true,
    "nextPackageType": "ENTERPRISE_MEDIUM",
    "status": "ACTIVE"
  }
}
```

---

### 2.2. Kích hoạt Gói Năm học Mới từ 01/09 (`US-SP7-14`)
* **Endpoint:** `POST /api/v1/admin/schools/{id}/activate-academic-year`
* **Role:** `ROLE_SYSTEM_ADMIN`
* **Mô tả:** Kích hoạt gói đã gia hạn bắt đầu từ ngày `01/09`, tự động thu hồi token dư trong ngày và cộng dồn tối đa **20% Quỹ token dư** của năm cũ sang năm mới.

---

### 2.3. Mua thêm / Nạp thêm Token giữa năm - Top-up (`US-SP7-14`)
* **Endpoint:** `POST /api/v1/admin/schools/{id}/top-up`
* **Role:** `ROLE_SYSTEM_ADMIN`
* **Mô tả:** Cộng thêm token thẳng vào Quỹ chung của trường (`totalSchoolTokenQuota` & `unallocatedTokenQuota`) mà không thay đổi ngày hết hạn `31/05`.

#### Request Body
```json
{
  "additionalTokens": 2000000,
  "note": "Mua bổ sung 2M token cho kỳ ôn thi HK2"
}
```

---

### 2.4. Cấu hình Hạn mức Token Ngày cho Trường / Khối / Lớp (`US-SP7-16`)
* **Endpoint:** `PUT /api/v1/school-admin/token-policy`
* **Role:** `ROLE_SCHOOL_ADMIN`
* **Mô tả:** Cài đặt định mức token mỗi học sinh được dùng mỗi ngày (`defaultDailyStudentQuota`, mặc định `1.000 token/ngày`) hoặc ghi đè định mức riêng cho một khối (`gradeLevel`, ví dụ Khối 12) hoặc một lớp cụ thể (`classroomId`).

#### Request Body
```json
{
  "defaultDailyStudentQuota": 1000,
  "gradeLevel": 12,
  "classroomDailyQuota": 2000
}
```

---

### 2.5. Học sinh Gửi yêu cầu Xin thêm Token trong ngày (`US-SP7-17`)
* **Endpoint:** `POST /api/v1/student/token-requests`
* **Role:** `ROLE_SCHOOL_STUDENT`
* **Mô tả:** Khi học sinh chạm trần hạn mức ngày, bấm gửi yêu cầu xin thêm token tới Giáo viên phụ trách lớp.

#### Request Body
```json
{
  "requestedTokens": 1000,
  "reason": "Em đang làm bài tập thảo luận sâu với nhân vật Quang Trung"
}
```

#### Response (`201 Created`)
```json
{
  "code": 201,
  "message": "Token boost request submitted successfully",
  "data": {
    "id": "b7777777-8888-9999-aaaa-bbbbbbbbbbbb",
    "schoolId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "classroomId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "className": "10A1",
    "studentUid": "u1111111-2222-3333-4444-555555555555",
    "studentName": "Trần Quốc Dinh",
    "studentCode": "HS1001",
    "requestedTokens": 1000,
    "approvedTokens": null,
    "reason": "Em đang làm bài tập thảo luận sâu với nhân vật Quang Trung",
    "status": "PENDING",
    "requestDate": "2026-10-19"
  }
}
```

---

### 2.6. Giáo viên / School Admin Xem & Duyệt Yêu cầu Xin thêm Token (`US-SP7-17`)
* **Danh sách yêu cầu:** `GET /api/v1/teacher/token-requests?status=PENDING&page=1&limit=10`
* **Duyệt / Từ chối:** `PUT /api/v1/teacher/token-requests/{requestId}/review`
* **Role:** `ROLE_TEACHER`, `ROLE_SCHOOL_ADMIN`

#### Request Body (`PUT /api/v1/teacher/token-requests/{requestId}/review`)
```json
{
  "action": "APPROVE",
  "approvedTokens": 1000,
  "reviewNote": "Duyệt cấp thêm 1.000 token trong ngày cho em hoàn thành bài"
}
```

---

### 2.7. Chuyển giao Năm học & Khóa Tài khoản Học sinh Ra trường / Chuyển trường (`US-SP7-18`)

#### A. Chuyển giao Năm học (`POST /api/v1/school-admin/academic-year/rollover`)
* **Role:** `ROLE_SCHOOL_ADMIN`
* **Mô tả:** Chuyển toàn bộ lớp học của năm học cũ sang trạng thái `ARCHIVED`, thu hồi toàn bộ token dư trong ngày của học sinh về Quỹ trường, và tự động vô hiệu hóa (`DEACTIVATED`) tài khoản học sinh Khối 12 đã tốt nghiệp.

```json
{
  "completedAcademicYear": 2025,
  "deactivateGraduatedGrade12": true
}
```

#### Response (`200 OK`)
```json
{
  "code": 200,
  "message": "Academic year rollover completed successfully",
  "data": {
    "schoolId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "completedAcademicYear": 2025,
    "archivedClassroomsCount": 18,
    "reclaimedTokensToPool": 64500,
    "deactivatedGraduatedStudentsCount": 210,
    "remainingSchoolTokenPool": 1064500
  }
}
```

#### B. Vô hiệu hóa Học sinh Chuyển trường / Ra trường lẻ (`PUT /api/v1/school-admin/students/{uid}/deactivate`)
* **Role:** `ROLE_SCHOOL_ADMIN`
* **Mô tả:** Thu hồi toàn bộ token dư của học sinh về Quỹ trường và chuyển trạng thái tài khoản sang `DEACTIVATED` (khóa đăng nhập vĩnh viễn).

```json
{
  "reason": "TRANSFERRED_SCHOOL"
}
```
