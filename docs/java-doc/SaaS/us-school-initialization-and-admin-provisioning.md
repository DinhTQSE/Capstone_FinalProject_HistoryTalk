# User Story Specification: Khởi Tạo Trường & Cấp Tài Khoản Admin
**Mã tài liệu:** US-SAAS-MOD2-01  
**Phân hệ:** Module 2 - Classroom SaaS (Multi-tenant B2B)  
**Dự án:** HistoryTalk Platform  
**Trạng thái:** Approved  

---

## 1. TỔNG QUAN & PHẠM VI (OVERVIEW & SCOPE)

### 1.1. Mục tiêu
Cung cấp giải pháp khởi tạo Trường học (Tenant) trên hệ thống HistoryTalk, tự động sinh mã định danh trường (`school_code`) theo quy chuẩn địa bàn và cấp tài khoản Quản trị viên Trường (`SCHOOL_ADMIN`). Giải quyết triệt để vấn đề phân biệt địa lý và cơ chế bàn giao mật khẩu ban đầu cho người dùng (bao gồm học sinh không có email cá nhân).

### 1.2. Các Vai trò Liên quan (Actors)
* **`SYSTEM_ADMIN`**: Quản trị viên tối cao của hệ thống HistoryTalk (người thực hiện khởi tạo trường).
* **`SCHOOL_ADMIN`**: Quản trị viên do Nhà trường chỉ định đại diện nhận hợp đồng SaaS.
* **`TEACHER`**: Giáo viên thuộc nhà trường.
* **`SCHOOL_STUDENT`**: Học sinh thuộc các lớp học của trường.

---

## 2. CHI TIẾT CÁC USER STORIES (DETAILED USER STORIES)

### 📌 US-01: Quy chuẩn Mã Trường (`school_code`) & Phân loại Địa lý

* **As a:** `SYSTEM_ADMIN` (Admin tổng)
* **I want to:** Khởi tạo trường học với mã định danh `school_code` được chuẩn hóa tự động theo Tỉnh/Thành phố, Cấp học và Tên viết tắt.
* **So that:** Hệ thống phân biệt chính xác các trường trên toàn quốc, không bị trùng lặp mã khi mở rộng quy mô (Tránh xung đột tên trường giữa các tỉnh thành).

#### 1. Công thức cấu trúc `school_code`:
$$\text{school\_code} = \text{\{MA\_TINH\}} \_\text{\{CAP\_HOC\}} \_\text{\{TEN\_RUT\_GON\}}$$

* **`MA_TINH`**: Mã ISO / Viết tắt Tỉnh Thành chuẩn (VD: `HCM` - TP.HCM, `HNO` - Hà Nội, `DAN` - Đà Nẵng, `NDH` - Nam Định, `BDG` - Bình Dương).
* **`CAP_HOC`**:
  * `THCS`: Trường Trung học Cơ sở.
  * `THPT`: Trường Trung học Phổ thông.
  * `PTLC`: Trường Phổ thông Liên cấp (THCS & THPT).
  * `GDTX`: Trung tâm Giáo dục Thường xuyên.
* **`TEN_RUT_GON`**: Tên viết tắt không dấu của trường (từ 2–8 ký tự).

#### 2. Bảng Ví dụ Thực tế:
| Tên Trường Thực Tế | Tỉnh/Thành | Cấp Học | Tên Rút Gọn | `school_code` Chuẩn |
| :--- | :--- | :--- | :--- | :--- |
| THPT Chuyên Lê Hồng Phong | TP. Hồ Chí Minh | THPT | LHP | `HCM_THPT_LHP` |
| THPT Lê Hồng Phong | Nam Định | THPT | LHP | `NDH_THPT_LHP` |
| THCS Nguyễn Du | Quận 1, TP.HCM | THCS | NGUYENDU | `HCM_THCS_NGUYENDU` |
| Phổ thông Liên cấp Vinschool | Hà Nội | PTLC | VINSCHOOL | `HNO_PTLC_VINSCHOOL` |

#### 3. Acceptance Criteria (AC):
* **AC-1.1**: `school_code` bắt buộc viết hoa, không chứa khoảng trắng hay ký tự đặc biệt ngoại trừ dấu gạch dưới (`_`). Độ dài từ 6 - 25 ký tự.
* **AC-1.2**: `school_code` phải **Duy nhất (Unique)** trên toàn hệ thống Database. Nếu nhập trùng, Backend ném lỗi Validation: `"Mã trường {school_code} đã tồn tại trên hệ thống!"`.
* **AC-1.3**: Hệ thống lưu trữ đầy đủ thông tin địa lý bao gồm: `province_code`, `district_code`, và `address` (Địa chỉ chi tiết).

---

### 📌 US-02: Khởi tạo Trường SaaS & Cấp Tài khoản School Admin

* **As a:** `SYSTEM_ADMIN`
* **I want to:** Khai báo thông tin hợp đồng trường và người đại diện (`SCHOOL_ADMIN`) với thông tin liên hệ thực tế.
* **So that:** Nhà trường nhận được tài khoản quản trị chính thức để tiếp nhận dịch vụ SaaS HistoryTalk.

#### 1. Business Rules & Acceptance Criteria (AC):
* **AC-2.1 (Bắt buộc Email thật đối với Admin)**: `SCHOOL_ADMIN` **BẮT BUỘC phải cung cấp 01 Email THẬT** đang hoạt động (Email công vụ hoặc Email cá nhân đại diện). Không chấp nhận email ảo ở bước này.
* **AC-2.2 (Quy tắc định danh Username Admin)**: Username của Admin nhà trường được tự động sinh theo công thức:
  $$\text{admin\_username} = \text{lower}(\text{school\_code}) + \text{"\_admin"}$$
  * *Ví dụ:* Trường `HCM_THPT_LHP` $\rightarrow$ Username Admin: **`hcm_thpt_lhp_admin`**.
* **AC-2.3 (Cơ chế Mật khẩu & Gửi Email SMTP)**:
  * Hệ thống tự động sinh Mật khẩu ngẫu nhiên an toàn (10-12 ký tự gồm chữ hoa, chữ thường, số, ký tự đặc biệt).
  * Hệ thống gửi Email chứa Mật khẩu khởi tạo về **đúng Email thật của `SCHOOL_ADMIN`**.
  * Tài khoản được đánh dấu cờ `is_first_login = true` trong cơ sở dữ liệu.

---

### 📌 US-03: Cơ chế Đăng nhập bằng Username & Bàn giao Mật khẩu cho Học sinh Không có Email

* **As a:** `SCHOOL_ADMIN` & `SCHOOL_STUDENT`
* **I want to:** Học sinh đăng nhập bằng Username do trường cấp (không bắt buộc có Email) và nhận mật khẩu qua Danh sách Bàn giao.
* **So that:** Giải quyết triệt để thực tế 90% học sinh phổ thông không có hòm thư Email cá nhân để nhận mật khẩu kích hoạt.

#### 1. Chiến lược Phân định Đăng nhập & Bàn giao Mật khẩu (Credential Strategy Matrix)

| Đối tượng | Email Required | Login Handle (Đăng nhập) | Phương thức Bàn giao Mật khẩu ban đầu |
| :--- | :--- | :--- | :--- |
| **`SCHOOL_ADMIN`** | **Bắt buộc 100% Email thật** | Email hoặc Username (`{school_code}_admin`) | Gửi qua **Email SMTP** của người đại diện. |
| **`TEACHER`** | **Bắt buộc Email thật** | Email hoặc Username (`{school_code}_gv_{ma_gv}`) | Gửi qua **Email SMTP** cá nhân từng giáo viên. |
| **`SCHOOL_STUDENT`** | **Không bắt buộc** (Có thể NULL) | **Username Bắt buộc** (`{school_code}_hs_{ma_hs}`) | **Export File Excel / PDF Thẻ Học Sinh** cho Admin/GVCN in & phát tận tay. |

#### 2. Quy trình Kỹ thuật Xử lý Email NULL & Tài khoản Nội bộ:
1. **Lưu trữ DB**: Cột `email` trong bảng `user` cho phép `NULL` đối với role `SCHOOL_STUDENT`. Nếu DB áp dụng ràng buộc `NOT NULL`, hệ thống tự sinh định danh nội bộ: `{username}@internal.saas`.
2. **Bộ lọc Mail Service**: `MailService` kiểm tra nếu email đuôi `@internal.saas` hoặc `NULL` sẽ **TỰ ĐỘNG BỎ QUA**, không gọi SMTP Server (tránh lỗi Failed Mail/Bounce).
3. **File Báo cáo Bàn giao (Credential Report)**: Khi `SCHOOL_ADMIN` import danh sách học sinh, hệ thống xuất file Excel gồm: `[Mã Lớp] | [Mã HS] | [Họ và Tên] | [Username Đăng nhập] | [Mật khẩu Khởi tạo]`.

#### 3. Acceptance Criteria (AC):
* **AC-3.1**: API Auth (`POST /api/v1/auth/login`) chấp nhận tham số `emailOrUsername`. Học sinh gõ Username (vd: `hcm_thpt_lhp_hs192645`) và Mật khẩu ban đầu để đăng nhập thành công.
* **AC-3.2 (Yêu cầu Đổi mật khẩu lần đầu)**: Khi đăng nhập bằng mật khẩu ban đầu (`is_first_login == true`), người dùng bị giới hạn quyền và bắt buộc phải chuyển sang màn hình Đổi Mật Khẩu trước khi sử dụng ứng dụng.

---

## 3. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE DDL MIGRATION)

Cập nhật Schema `historical_schema` (Flyway Migration Script `V26__init_school_saas.sql`):

```sql
-- 1. Tạo bảng Trường học (School / SaaS Tenant)
CREATE TABLE IF NOT EXISTS historical_schema.school (
    school_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_code VARCHAR(50) NOT NULL UNIQUE,      -- e.g. HCM_THPT_LHP
    school_name VARCHAR(255) NOT NULL,            -- Trường THPT Chuyên Lê Hồng Phong
    school_type VARCHAR(20) NOT NULL,              -- THCS, THPT, PTLC, GDTX
    province_code VARCHAR(20) NOT NULL,            -- HCM, HNO, NDH...
    district_code VARCHAR(20),
    address VARCHAR(500),
    max_teachers INT DEFAULT 50,
    max_students INT DEFAULT 2000,
    monthly_token_quota INT DEFAULT 500000,        -- Quỹ Token AI dùng chung
    subscription_expires_at TIMESTAMP NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP                           -- Soft delete support
);

-- 2. Cập nhật bảng "user" nâng cấp thuộc tính Multi-tenant & First Login
ALTER TABLE historical_schema."user" 
ADD COLUMN IF NOT EXISTS school_id UUID NULL REFERENCES historical_schema.school(school_id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS is_first_login BOOLEAN DEFAULT TRUE;

-- 3. Tạo Indexes tối ưu hiệu năng truy vấn
CREATE INDEX IF NOT EXISTS idx_user_school_id ON historical_schema."user"(school_id);
CREATE INDEX IF NOT EXISTS idx_school_code ON historical_schema.school(school_code);
```

---

## 4. API SPECIFICATION & DTO CONTRACTS

### Endpoint: `POST /api/v1/schools`
* **Authorization:** `Bearer <JWT_TOKEN>` (Yêu cầu Role `SYSTEM_ADMIN` hoặc `ADMIN`).

#### Request Body (`CreateSchoolRequest`):
```json
{
  "schoolCode": "HCM_THPT_LHP",
  "schoolName": "Trường THPT Chuyên Lê Hồng Phong",
  "schoolType": "THPT",
  "provinceCode": "HCM",
  "districtCode": "Q5",
  "address": "235 Nguyễn Văn Cừ, Phường 4, Quận 5, TP.HCM",
  "maxTeachers": 100,
  "maxStudents": 3000,
  "subscriptionExpiresAt": "2027-09-01T00:00:00",
  "adminFullName": "Trần Văn Khải",
  "adminEmail": "khai.tran@lehongphong.edu.vn",
  "adminPhone": "0901234567"
}
```

#### Response Body 201 Created (`SchoolResponse`):
```json
{
  "status": 201,
  "message": "Khởi tạo trường học và cấp tài khoản Admin thành công!",
  "data": {
    "schoolId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "schoolCode": "HCM_THPT_LHP",
    "schoolName": "Trường THPT Chuyên Lê Hồng Phong",
    "schoolType": "THPT",
    "provinceCode": "HCM",
    "maxTeachers": 100,
    "maxStudents": 3000,
    "subscriptionExpiresAt": "2027-09-01T00:00:00",
    "adminAccount": {
      "username": "hcm_thpt_lhp_admin",
      "email": "khai.tran@lehongphong.edu.vn",
      "fullName": "Trần Văn Khải",
      "role": "SCHOOL_ADMIN",
      "isFirstLogin": true,
      "emailSent": true
    }
  }
}
```
