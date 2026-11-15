# 📑 FRONTEND API CONTRACT: B2B CUSTOM PLAN, LIVE QUOTE CALCULATOR & QUOTE REGISTRATION FLOW (HUMY.AI STYLE)

> **Sprint:** Sprint 9  
> **Modules:** B2B Subscription & Quote Flow (`/b2b/*`, `/admin/b2b-quotes/*`), B2C Tiers (`/payments/tiers`), Exam Mode (`/chat/sessions`)  
> **Base URL:** `/Historical-tell/api/v1`  
> **Authentication:**
> - **Public Endpoints (`/b2b/*`):** Không cần JWT Token (`permitAll`)
> - **System Admin Endpoints (`/admin/b2b-quotes/*`):** Yêu cầu `Authorization: Bearer <jwt_access_token>` với role `SYSTEM_ADMIN`

---

## 1. Tổng quan Luồng tích hợp cho Frontend

### 1.1. Màn hình Bảng giá & Đăng ký Trường học (Public — giống Humy.ai)
1. **Khi mở trang Bảng giá B2B (`/for-schools` hoặc `/pricing/b2b`):**
   - Gọi `GET /b2b/plans` để lấy:
     - 3 gói cố định (`ENTERPRISE_SMALL`, `ENTERPRISE_MEDIUM`, `ENTERPRISE_LARGE`)
     - Công thức tính đơn giá (`0.15đ/token`, Học sinh `112.000đ/năm`, Giáo viên `200.000đ/năm`)
     - 4 bậc chiết khấu theo quy mô học sinh (`0%`, `10%`, `20%`, `30%`)
2. **Khi người dùng chọn gói hoặc kéo thanh trượt Customize (`numberOfStudents`, `numberOfTeachers`, nhập `promoCode`):**
   - Gọi `POST /b2b/quotes/calculate` (debounce `200ms–300ms`) hoặc tính nhanh ở client theo công thức rồi đồng bộ với response từ BE.
   - Hiển thị trực tiếp ở cột phải (**Your Plan Summary**):
     - Tổng quỹ Token năm học (`totalTokenPool`)
     - Chi phí AI Token (`aiTokenCostVnd`) + Chi phí DB/LMS (`dbAndLmsCostVnd`)
     - Tạm tính (`subtotalVnd`), Chiết khấu (`discountPercent` & `discountAmountVnd`), và **Tổng thanh toán năm học (`finalPriceVnd`)** + Chi phí bình quân mỗi học sinh/tháng (`averageMonthlyCostPerStudentVnd`).
3. **Khi đại diện trường điền form và bấm "Gửi yêu cầu Báo giá & Đăng ký" (`Request Quote`):**
   - Gọi `POST /b2b/quotes/request`.
   - Hiển thị modal xác nhận kèm mã báo giá **`quoteCode`** (ví dụ: `QT-2026-4821`).

### 1.2. Màn hình Quản trị Báo giá B2B (System Admin Portal)
1. **Danh sách yêu cầu báo giá:** Gọi `GET /admin/b2b-quotes?status=PENDING&search=...&page=1&size=10`.
2. **Xem chi tiết 1 báo giá:** Gọi `GET /admin/b2b-quotes/{id}`.
3. **Cập nhật trạng thái tư vấn / thương lượng chiết khấu:** Gọi `PATCH /admin/b2b-quotes/{id}/status` (`CONTACTED` hoặc `REJECTED`, có thể chỉnh `customDiscountPercent`).
4. **Phê duyệt & Tự động khởi tạo Trường học + Tài khoản School Admin (1-Click Provisioning):**
   - Gọi `POST /admin/b2b-quotes/{id}/approve`.
   - BE tự động:
     1. Tạo `School` ở trạng thái `ACTIVE`, nạp đúng quỹ Token `totalTokenPool`, thiết lập `maxStudents`, `maxTeachers`, hạn mức mặc định `5.000 tokens/ngày` cho cả Học sinh & Giáo viên.
     2. Tạo tài khoản `SCHOOL_ADMIN` (`<school_code>_admin`) và sinh mật khẩu khởi tạo `initialPassword` (`Ht@...`).
     3. Chuyển trạng thái báo giá sang `APPROVED`.
   - FE hiển thị popup thông tin tài khoản `SCHOOL_ADMIN` vừa tạo (giống màn hình tạo School Admin hiện tại) để System Admin bàn giao cho trường.

### 1.3. Thay đổi ở Bảng giá B2C & Chế độ Vấn đáp (`EXAM`)
- **Bảng giá B2C (`GET /payments/tiers`):**
  - **`Free`:** `0đ` — `1.500 tokens/ngày` (`45.000 tokens/tháng`). **Chế độ Thi Vấn Đáp (`EXAM`) giới hạn tối đa 2 câu hỏi / session** (BE tự động giới hạn `examQuestionCount = 2` khi tạo session `EXAM` với tài khoản `CUSTOMER` gói Free).
  - **`Plus`:** `29.500đ/tháng` (`300.900đ/năm` - giảm 15%) — `5.000 tokens/ngày` (`150.000 tokens/tháng`). Mở khóa Quiz & Exam Mode **không giới hạn**.
  - **`Pro`:** `52.000đ/tháng` (`530.400đ/năm` - giảm 15%) — `10.000 tokens/ngày` (`300.000 tokens/tháng`). Mở khóa Quiz & Exam Mode **không giới hạn**.

---

## 2. Chi tiết các Endpoint Public B2B (`/b2b/*`)

### 2.1. Lấy danh mục gói B2B & bậc thang chiết khấu
- **Method & Path:** `GET /Historical-tell/api/v1/b2b/plans`
- **Auth:** Public (Không cần token)

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Lấy danh mục gói B2B trường học thành công",
  "data": {
    "tokenUnitPriceVnd": 0.15,
    "academicWeeksPerYear": 35,
    "studentDailyTokenLimit": 5000,
    "studentActiveDaysPerWeek": 4,
    "studentTokensPerYear": 700000,
    "studentAiTokenCostVnd": 105000,
    "studentDbAndQuizFeeVnd": 7000,
    "studentTotalAnnualCostVnd": 112000,
    "teacherDailyTokenLimit": 5000,
    "teacherActiveDaysPerWeek": 6,
    "teacherTokensPerYear": 1050000,
    "teacherAiTokenCostVnd": 157500,
    "teacherLmsAndStorageFeeVnd": 42500,
    "teacherTotalAnnualCostVnd": 200000,
    "discountTiers": [
      {
        "minStudents": 1,
        "maxStudents": 599,
        "discountPercent": 0,
        "description": "Dưới 600 Học sinh (< 15 lớp): Giá gốc tiêu chuẩn (0% chiết khấu)"
      },
      {
        "minStudents": 600,
        "maxStudents": 1199,
        "discountPercent": 10,
        "description": "Từ 600 đến 1.199 Học sinh (15–29 lớp - chuẩn gói Small): Giảm 10%"
      },
      {
        "minStudents": 1200,
        "maxStudents": 1799,
        "discountPercent": 20,
        "description": "Từ 1.200 đến 1.799 Học sinh (30–44 lớp - chuẩn gói Medium): Giảm 20%"
      },
      {
        "minStudents": 1800,
        "maxStudents": null,
        "discountPercent": 30,
        "description": "Từ 1.800 Học sinh trở lên (45+ lớp - chuẩn gói Large): Giảm 30%"
      }
    ],
    "plans": [
      {
        "packageType": "ENTERPRISE_SMALL",
        "displayName": "Gói Trường Nhỏ / THCS (Small)",
        "maxStudents": 600,
        "maxTeachers": 15,
        "studentDailyTokenLimit": 5000,
        "teacherDailyTokenLimit": 5000,
        "totalTokenPool": 435750000,
        "aiTokenCostVnd": 65362500,
        "dbAndLmsCostVnd": 4837500,
        "subtotalVnd": 70200000,
        "discountPercent": 10,
        "discountAmountVnd": 7020000,
        "finalPriceVnd": 63180000,
        "averageMonthlyCostPerStudentVnd": 11700,
        "popular": false,
        "highlights": [
          "Tối đa 600 Học sinh & 15 Giáo viên",
          "Quỹ Token chung: 435,750,000 tokens / năm học",
          "Hạn mức ngày: 5.000 tokens/ngày cho cả Học sinh & Giáo viên",
          "Kho nội dung Lịch sử Địa phương riêng biệt (PDF, Video, Nhân vật)",
          "Chế độ Thi Vấn Đáp (Exam Mode) & Quiz không giới hạn, lưu điểm theo lớp"
        ]
      },
      {
        "packageType": "ENTERPRISE_MEDIUM",
        "displayName": "Gói Trường Tiêu Chuẩn (Medium)",
        "maxStudents": 1400,
        "maxTeachers": 35,
        "studentDailyTokenLimit": 5000,
        "teacherDailyTokenLimit": 5000,
        "totalTokenPool": 1016750000,
        "aiTokenCostVnd": 152512500,
        "dbAndLmsCostVnd": 11287500,
        "subtotalVnd": 163800000,
        "discountPercent": 20,
        "discountAmountVnd": 32760000,
        "finalPriceVnd": 131040000,
        "averageMonthlyCostPerStudentVnd": 10400,
        "popular": true,
        "highlights": [
          "Tối đa 1400 Học sinh & 35 Giáo viên",
          "Quỹ Token chung: 1,016,750,000 tokens / năm học",
          "Hạn mức ngày: 5.000 tokens/ngày cho cả Học sinh & Giáo viên",
          "Kho nội dung Lịch sử Địa phương riêng biệt (PDF, Video, Nhân vật)",
          "Chế độ Thi Vấn Đáp (Exam Mode) & Quiz không giới hạn, lưu điểm theo lớp"
        ]
      },
      {
        "packageType": "ENTERPRISE_LARGE",
        "displayName": "Gói Trường Lớn / Đô thị (Large)",
        "maxStudents": 2000,
        "maxTeachers": 50,
        "studentDailyTokenLimit": 5000,
        "teacherDailyTokenLimit": 5000,
        "totalTokenPool": 1452500000,
        "aiTokenCostVnd": 217875000,
        "dbAndLmsCostVnd": 16125000,
        "subtotalVnd": 234000000,
        "discountPercent": 30,
        "discountAmountVnd": 70200000,
        "finalPriceVnd": 163800000,
        "averageMonthlyCostPerStudentVnd": 9100,
        "popular": false,
        "highlights": [
          "Tối đa 2000 Học sinh & 50 Giáo viên",
          "Quỹ Token chung: 1,452,500,000 tokens / năm học",
          "Hạn mức ngày: 5.000 tokens/ngày cho cả Học sinh & Giáo viên",
          "Kho nội dung Lịch sử Địa phương riêng biệt (PDF, Video, Nhân vật)",
          "Chế độ Thi Vấn Đáp (Exam Mode) & Quiz không giới hạn, lưu điểm theo lớp"
        ]
      }
    ]
  },
  "timestamp": "2026-11-06T17:45:00"
}
```

---

### 2.2. Live Quote Calculator (Tính giá trực tiếp cho gói cố định hoặc Customize)
- **Method & Path:** `POST /Historical-tell/api/v1/b2b/quotes/calculate`
- **Auth:** Public (Không cần token)

#### Request Body
```json
{
  "selectedPackage": "ENTERPRISE_CUSTOM",
  "numberOfStudents": 850,
  "numberOfTeachers": 22,
  "academicWeeks": 35,
  "studentDailyTokenLimit": 5000,
  "teacherDailyTokenLimit": 5000,
  "studentDaysPerWeek": 4,
  "teacherDaysPerWeek": 6,
  "promoCode": ""
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc / Ghi chú |
| :--- | :--- | :---: | :--- |
| `selectedPackage` | `String` | Không | `ENTERPRISE_SMALL`, `ENTERPRISE_MEDIUM`, `ENTERPRISE_LARGE`, hoặc `ENTERPRISE_CUSTOM` |
| `numberOfStudents` | `Integer` | **Có** | `1 <= numberOfStudents <= 50000` |
| `numberOfTeachers` | `Integer` | **Có** | `1 <= numberOfTeachers <= 5000` |
| `academicWeeks` | `Integer` | Không | Chu kỳ năm học (số tuần), `1 <= academicWeeks <= 60` (Mặc định: `35`) |
| `studentDailyTokenLimit` | `Long` | Không | Hạn mức token/ngày của Học sinh, `500 <= limit <= 100000` (Mặc định: `5000`) |
| `teacherDailyTokenLimit` | `Long` | Không | Hạn mức token/ngày của Giáo viên, `500 <= limit <= 100000` (Mặc định: `5000`) |
| `studentDaysPerWeek` | `Integer` | Không | Số ngày học/tuần của Học sinh, `1 <= days <= 7` (Mặc định: `4`) |
| `teacherDaysPerWeek` | `Integer` | Không | Số ngày dùng/tuần của Giáo viên, `1 <= days <= 7` (Mặc định: `6`) |
| `promoCode` | `String` | Không | Mã ưu đãi (ví dụ: `EDU2026` hoặc `HISTORYTALK5` được giảm thêm `+5%`) |

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Tính toán báo giá gói trường học thành công",
  "data": {
    "selectedPackage": "ENTERPRISE_CUSTOM",
    "numberOfStudents": 850,
    "numberOfTeachers": 22,
    "academicWeeks": 35,
    "studentDailyTokenLimit": 5000,
    "teacherDailyTokenLimit": 5000,
    "studentDaysPerWeek": 4,
    "teacherDaysPerWeek": 6,
    "studentTokensPerYear": 700000,
    "teacherTokensPerYear": 1050000,
    "totalStudentTokens": 595000000,
    "totalTeacherTokens": 23100000,
    "totalTokenPool": 618100000,
    "tokenUnitPriceVnd": 0.15,
    "aiTokenCostVnd": 92715000,
    "studentDbAndQuizFeeVnd": 7000,
    "teacherLmsAndStorageFeeVnd": 42500,
    "dbAndLmsCostVnd": 6885000,
    "subtotalVnd": 99600000,
    "discountPercent": 10,
    "discountAmountVnd": 9960000,
    "finalPriceVnd": 89640000,
    "averageMonthlyCostPerStudentVnd": 11717,
    "promoCodeApplied": null
  },
  "timestamp": "2026-11-06T17:45:00"
}
```

---

### 2.3. Gửi yêu cầu Báo giá & Đăng ký Trường học (Submit Quote Request)
- **Method & Path:** `POST /Historical-tell/api/v1/b2b/quotes/request`
- **Auth:** Public (Không cần token)

#### Request Body (Chuẩn form Humy.ai + Deep Customization)
```json
{
  "firstName": "Quốc Dinh",
  "lastName": "Trần",
  "contactEmail": "hieutruong@thpt-lequydon.edu.vn",
  "contactPhone": "0966622100",
  "jobTitle": "Hiệu trưởng",
  "schoolName": "Trường THPT Lê Quý Đôn",
  "schoolCode": "LQD-HCM",
  "schoolAddress": "110 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh",
  "selectedPackage": "ENTERPRISE_CUSTOM",
  "numberOfStudents": 850,
  "numberOfTeachers": 22,
  "academicWeeks": 35,
  "studentDailyTokenLimit": 5000,
  "teacherDailyTokenLimit": 5000,
  "studentDaysPerWeek": 4,
  "teacherDaysPerWeek": 6,
  "subscriptionStartDate": "2026-09-01",
  "subscriptionEndDate": "2027-05-31",
  "promoCode": "",
  "message": "Trường cần xuất hóa đơn VAT và triển khai trước ngày 01/09."
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc / Ghi chú |
| :--- | :--- | :---: | :--- |
| `firstName` | `String` | **Có** | Tên người liên hệ (tối đa 100 ký tự) |
| `lastName` | `String` | **Có** | Họ & chữ lót người liên hệ (tối đa 100 ký tự) |
| `contactEmail` | `String` | **Có** | Email công vụ hợp lệ (sẽ dùng để cấp tài khoản `SCHOOL_ADMIN` khi duyệt) |
| `contactPhone` | `String` | **Có** | Số điện thoại liên hệ (tối đa 30 ký tự) |
| `jobTitle` | `String` | **Có** | Chức vụ (`Hiệu trưởng`, `Phó Hiệu trưởng`, `Tổ trưởng Sử`, `Cán bộ CNTT`,...) |
| `schoolName` | `String` | **Có** | Tên trường học (tối đa 255 ký tự) |
| `schoolCode` | `String` | Không | Mã trường tùy chọn (nếu để trống BE tự sinh theo tên trường + tỉnh/thành) |
| `schoolAddress` | `String` | Không | Địa chỉ trường học (tối đa 500 ký tự) |
| `selectedPackage` | `String` | Không | Mặc định `ENTERPRISE_CUSTOM` (hoặc `ENTERPRISE_SMALL`, `ENTERPRISE_MEDIUM`, `ENTERPRISE_LARGE`) |
| `numberOfStudents` | `Integer` | **Có** | `1 <= numberOfStudents <= 50000` |
| `numberOfTeachers` | `Integer` | **Có** | `1 <= numberOfTeachers <= 5000` |
| `academicWeeks` | `Integer` | Không | Chu kỳ năm học (số tuần), `1 <= academicWeeks <= 60` (Mặc định: `35`) |
| `studentDailyTokenLimit` | `Long` | Không | Hạn mức token/ngày của Học sinh, `500 <= limit <= 100000` (Mặc định: `5000`) |
| `teacherDailyTokenLimit` | `Long` | Không | Hạn mức token/ngày của Giáo viên, `500 <= limit <= 100000` (Mặc định: `5000`) |
| `studentDaysPerWeek` | `Integer` | Không | Số ngày học/tuần của Học sinh, `1 <= days <= 7` (Mặc định: `4`) |
| `teacherDaysPerWeek` | `Integer` | Không | Số ngày dùng/tuần của Giáo viên, `1 <= days <= 7` (Mặc định: `6`) |
| `subscriptionStartDate` | `LocalDate` | Không | Ngày bắt đầu niên khóa (VD: `2026-09-01`) |
| `subscriptionEndDate` | `LocalDate` | Không | Ngày kết thúc niên khóa (VD: `2027-05-31`) |
| `promoCode` | `String` | Không | Mã ưu đãi (nếu có) |
| `message` | `String` | Không | Ghi chú thêm của trường (tối đa 2000 ký tự) |

#### Response `201 Created`
```json
{
  "success": true,
  "message": "Gửi yêu cầu báo giá và đăng ký trường học thành công",
  "data": {
    "id": "a9095b65-3f72-4a8e-9b25-1d88bc11f210",
    "quoteCode": "QT-2026-4821",
    "firstName": "Quốc Dinh",
    "lastName": "Trần",
    "fullName": "Trần Quốc Dinh",
    "contactEmail": "hieutruong@thpt-lequydon.edu.vn",
    "contactPhone": "0966622100",
    "jobTitle": "Hiệu trưởng",
    "schoolName": "Trường THPT Lê Quý Đôn",
    "schoolCode": "LQD-HCM",
    "schoolAddress": "110 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh",
    "selectedPackage": "ENTERPRISE_CUSTOM",
    "numberOfStudents": 850,
    "numberOfTeachers": 22,
    "academicWeeks": 35,
    "studentDailyTokenLimit": 5000,
    "teacherDailyTokenLimit": 5000,
    "studentDaysPerWeek": 4,
    "teacherDaysPerWeek": 6,
    "totalTokenPool": 618100000,
    "aiTokenCostVnd": 92715000,
    "dbAndLmsCostVnd": 6885000,
    "subtotalVnd": 99600000,
    "discountPercent": 10,
    "discountAmountVnd": 9960000,
    "finalPriceVnd": 89640000,
    "promoCode": null,
    "message": "Trường cần xuất hóa đơn VAT và triển khai trước ngày 01/09.",
    "status": "PENDING",
    "adminNote": null,
    "reviewedByUid": null,
    "reviewedAt": null,
    "createdSchoolId": null,
    "createdSchoolAdminUid": null,
    "createdAt": "2026-11-06T17:45:00",
    "updatedAt": null
  },
  "timestamp": "2026-11-06T17:45:00"
}
```

---

## 3. Chi tiết các Endpoint Quản trị Báo giá B2B (System Admin)

### 3.1. Danh sách yêu cầu báo giá B2B (Có phân trang & lọc)
- **Method & Path:** `GET /Historical-tell/api/v1/admin/b2b-quotes`
- **Auth:** `Bearer <jwt_access_token>` (`SYSTEM_ADMIN`)
- **Query Params:**
  - `status` (optional): `PENDING` | `CONTACTED` | `APPROVED` | `REJECTED`
  - `search` (optional): Tìm theo `quoteCode`, `schoolName`, `contactEmail`, `contactPhone`
  - `page` (optional, default `1`): Trang hiện tại (1-based)
  - `size` (optional, default `10`): Số bản ghi mỗi trang

---

### 3.2. Xem chi tiết 1 yêu cầu báo giá B2B
- **Method & Path:** `GET /Historical-tell/api/v1/admin/b2b-quotes/{id}`
- **Auth:** `Bearer <jwt_access_token>` (`SYSTEM_ADMIN`)

---

### 3.3. Cập nhật trạng thái tư vấn / thương lượng chiết khấu / điều chỉnh tham số gói
- **Method & Path:** `PATCH /Historical-tell/api/v1/admin/b2b-quotes/{id}/status`
- **Auth:** `Bearer <jwt_access_token>` (`SYSTEM_ADMIN`)

#### Request Body
```json
{
  "status": "CONTACTED",
  "customDiscountPercent": 15,
  "academicWeeks": 40,
  "studentDailyTokenLimit": 5000,
  "teacherDailyTokenLimit": 6000,
  "studentDaysPerWeek": 4,
  "teacherDaysPerWeek": 6,
  "adminNote": "Đã thương lượng với trường mở rộng năm học lên 40 tuần và chiết khấu 15%."
}
```

| Trường | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `status` | `String` | **Có** | `CONTACTED` hoặc `REJECTED` (Để duyệt và tạo trường, gọi API `/approve` bên dưới) |
| `customDiscountPercent` | `Integer` | Không | `0–100`. Nếu truyền vào, BE sẽ tính lại `discountAmountVnd` và `finalPriceVnd` theo chiết khấu mới |
| `academicWeeks` | `Integer` | Không | `1–60`. Tùy chỉnh lại số tuần học khi đàm phán hợp đồng (BE tự động tính lại toàn bộ chi phí) |
| `studentDailyTokenLimit` | `Long` | Không | `500–100000`. Điều chỉnh hạn mức token ngày của Học sinh |
| `teacherDailyTokenLimit` | `Long` | Không | `500–100000`. Điều chỉnh hạn mức token ngày của Giáo viên |
| `studentDaysPerWeek` | `Integer` | Không | `1–7`. Điều chỉnh số ngày dùng/tuần của Học sinh |
| `teacherDaysPerWeek` | `Integer` | Không | `1–7`. Điều chỉnh số ngày dùng/tuần của Giáo viên |
| `adminNote` | `String` | Không | Ghi chú nội bộ của System Admin |

---

### 3.4. Phê duyệt Báo giá & Tự động Khởi tạo Trường + School Admin
- **Method & Path:** `POST /Historical-tell/api/v1/admin/b2b-quotes/{id}/approve`
- **Auth:** `Bearer <jwt_access_token>` (`SYSTEM_ADMIN`)

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Phê duyệt báo giá và khởi tạo trường học thành công",
  "data": {
    "quote": {
      "id": "a9095b65-3f72-4a8e-9b25-1d88bc11f210",
      "quoteCode": "QT-2026-4821",
      "schoolName": "Trường THPT Lê Quý Đôn",
      "status": "APPROVED",
      "createdSchoolId": "d1428c19-8320-41ef-8a00-7218aa291001",
      "createdSchoolAdminUid": "f82a109c-1293-4b7a-991a-002918273645"
    },
    "school": {
      "id": "d1428c19-8320-41ef-8a00-7218aa291001",
      "name": "Trường THPT Lê Quý Đôn",
      "schoolCode": "LQD-HCM",
      "address": "110 Nguyễn Thị Minh Khai, Quận 3, TP. Hồ Chí Minh",
      "contactEmail": "hieutruong@thpt-lequydon.edu.vn",
      "contactPhone": "0966622100",
      "packageType": "ENTERPRISE_CUSTOM",
      "recommendedStudents": 850,
      "totalSchoolTokenQuota": 618100000,
      "unallocatedTokenQuota": 618100000,
      "defaultDailyStudentQuota": 5000,
      "defaultDailyTeacherQuota": 5000,
      "subscriptionStartDate": "2026-09-01",
      "subscriptionEndDate": "2027-05-31",
      "status": "ACTIVE"
    },
    "schoolAdmin": {
      "uid": "f82a109c-1293-4b7a-991a-002918273645",
      "userName": "lqd-hcm_admin",
      "email": "hieutruong@thpt-lequydon.edu.vn",
      "fullName": "Trần Quốc Dinh",
      "phoneNumber": "0966622100",
      "role": "SCHOOL_ADMIN",
      "schoolId": "d1428c19-8320-41ef-8a00-7218aa291001",
      "schoolName": "Trường THPT Lê Quý Đôn",
      "initialPassword": "Ht@9xK2mP7",
      "active": true
    }
  },
  "timestamp": "2026-11-06T17:45:00"
}
```

---

## 4. Mua Thêm Token Cho Trường Học (School Token Top-up via PayOS Self-Service)

> **Mục đích:** Khi nhà trường sử dụng hết hoặc sắp hết quỹ token chung (`unallocatedTokenQuota`) trước khi kết thúc chu kỳ năm học, Quản trị viên Trường (`SCHOOL_ADMIN`) có thể chủ động mua thêm token mà **không cần qua phê duyệt của System Admin**.
> **Cơ chế:** Chọn số token $\to$ Sinh link thanh toán PayOS & Mã VietQR $\to$ Quét mã trả tiền $\to$ Webhook PayOS tự động nạp thẳng token vào hồ chứa chung của trường ngay lập tức!
> **Đơn giá:** Cố định **`0,15 đ / 1 token`** (Tối thiểu 100.000 tokens).

### 4.1. Khởi tạo đơn mua thêm token qua PayOS
- **Method & Path:** `POST /Historical-tell/api/v1/school-admin/tokens/purchase`
- **Auth:** `Bearer <jwt_access_token>` (`ROLE_SCHOOL_ADMIN`)

#### Request Body
```json
{
  "tokens": 10000000
}
```

| Trường | Kiểu | Bắt buộc | Ràng buộc / Diễn giải |
| :--- | :--- | :---: | :--- |
| `tokens` | `Long` | **Có** | Số lượng token cần nạp thêm (`>= 100000`). Số tiền thanh toán: `tokens * 0.15đ` |

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Khởi tạo đơn mua token qua PayOS thành công",
  "data": {
    "orderId": "b1f48695-12c8-4712-9c1a-7b89d421e330",
    "orderCode": 20261112001,
    "schoolId": "d1428c19-8320-41ef-8a00-7218aa291001",
    "schoolName": "Trường THPT Lê Quý Đôn",
    "tokens": 10000000,
    "amount": 1500000,
    "paymentLinkId": "plink_68491029481",
    "checkoutUrl": "https://pay.payos.vn/web/20261112001",
    "qrCode": "00020101021238570010A00000072701270006970422...",
    "status": "PENDING",
    "expiredAt": "2026-11-12T13:45:00",
    "createdAt": "2026-11-12T13:30:00"
  },
  "timestamp": "2026-11-12T13:30:00"
}
```

---

### 4.2. Lịch sử mua thêm token của trường
- **Method & Path:** `GET /Historical-tell/api/v1/school-admin/tokens/purchase-history?page=0&size=10`
- **Auth:** `Bearer <jwt_access_token>` (`ROLE_SCHOOL_ADMIN`)

#### Response `200 OK`
```json
{
  "success": true,
  "message": "Lấy lịch sử mua token thành công",
  "data": {
    "content": [
      {
        "orderId": "b1f48695-12c8-4712-9c1a-7b89d421e330",
        "orderCode": 20261112001,
        "schoolId": "d1428c19-8320-41ef-8a00-7218aa291001",
        "schoolName": "Trường THPT Lê Quý Đôn",
        "tokens": 10000000,
        "amount": 1500000,
        "status": "PAID",
        "fulfillmentStatus": "FULFILLED",
        "paidAt": "2026-11-12T13:32:15",
        "createdAt": "2026-11-12T13:30:00"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 10,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-11-12T13:35:00"
}
```

---

## 5. API Gia Hạn Niên Khóa & Bảo Lưu Token (Renewal & Token Rollover)

### 5.1 School Admin Gia hạn Gói Năm học (`POST /api/v1/school-admin/subscription/renew`)
- **Mô tả:** Cho phép `ROLE_SCHOOL_ADMIN` đăng ký gia hạn gói trong tháng 5 (hoặc trong vòng 30 ngày trước khi hết hạn chu kỳ cũ).
- **Hỗ trợ linh hoạt:**
  - **Lựa chọn 1 (Giữ nguyên quy mô Custom cũ):** Chỉ truyền `packageType: "ENTERPRISE_CUSTOM"`, không cần truyền `customTokenQuota`. Hệ thống tự động lấy lại định mức token cơ sở của năm cũ.
  - **Lựa chọn 2 (Điều chỉnh quy mô Custom mới):** Truyền `packageType: "ENTERPRISE_CUSTOM"` và `customTokenQuota` cùng các thông số điều chỉnh mới (`maxStudents`, `maxTeachers`, `defaultDailyStudentQuota`, `defaultDailyTeacherQuota`, `subscriptionStartDate`, `subscriptionEndDate`).
  - **Lựa chọn 3 (Chuyển đổi giữa các gói):** Chuyển từ Custom sang Standard (`ENTERPRISE_SMALL` / `MEDIUM` / `LARGE`) hoặc ngược lại.

- **Request Headers:**
  - `Authorization: Bearer <school_admin_jwt>`

- **Request Body (Ví dụ: Gia hạn Custom điều chỉnh quy mô):**
```json
{
  "packageType": "ENTERPRISE_CUSTOM",
  "customTokenQuota": 1200000000,
  "maxStudents": 2000,
  "maxTeachers": 50,
  "defaultDailyStudentQuota": 5000,
  "defaultDailyTeacherQuota": 5000,
  "subscriptionStartDate": "2026-09-01",
  "subscriptionEndDate": "2027-05-31"
}
```

- **Request Body (Ví dụ: Gia hạn 1-Click giữ nguyên gói Custom cũ):**
```json
{
  "packageType": "ENTERPRISE_CUSTOM"
}
```

- **Response (`200 OK`):**
```json
{
  "success": true,
  "message": "School subscription renewed for next academic year",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "THPT Marie Curie",
    "schoolCode": "MC-HCM",
    "packageType": "ENTERPRISE_CUSTOM",
    "totalSchoolTokenQuota": 1452500000,
    "unallocatedTokenQuota": 85000000,
    "renewedForNextYear": true,
    "nextPackageType": "ENTERPRISE_CUSTOM",
    "nextCustomTokenQuota": 1200000000,
    "baseCustomTokenQuota": 1200000000,
    "maxStudents": 2000,
    "maxTeachers": 50,
    "status": "ACTIVE"
  },
  "timestamp": "2026-11-12T14:20:00"
}
```

### 5.2 Cơ chế Tính toán khi Kích hoạt Năm học Mới (`POST /api/v1/admin/schools/{id}/activate-academic-year`)
- **Thời điểm:** Ngày 01/09 hoặc sau khi gói cũ hết hạn.
- **Công thức:**
  $$\text{Quỹ mới} = \text{Token Gói năm mới} + \lfloor \text{Token dư cũ} \times 20\% \rfloor$$
- **Dữ liệu được cập nhật:**
  - `totalSchoolTokenQuota = newTotalQuota`
  - `unallocatedTokenQuota = newTotalQuota`
  - `packageType = nextPackageType`
  - Cập nhật trần học sinh, giáo viên, hạn mức ngày theo cấu hình mới.
  - Reset cờ `renewedForNextYear = false`, `nextPackageType = null`, `nextCustomTokenQuota = null`.

---

## 6. Bảng Mã Lỗi (Error Handling)

| HTTP Status | Tình huống | Message mẫu từ BE |
| :---: | :--- | :--- |
| `400 Bad Request` | Số học sinh `< 1` hoặc `> 50000`, số giáo viên `< 1` hoặc `> 5000` | `Number of students must be at least 1` |
| `400 Bad Request` | Thiếu thông tin bắt buộc trong form báo giá | `Contact email is required` |
| `400 Bad Request` | Mua token trường học với số lượng `< 100.000 tokens` | `Số lượng token mua thêm tối thiểu là 100.000 tokens` |
| `400 Bad Request` | Tài khoản School Admin chưa liên kết với trường học nào | `Tài khoản chưa được liên kết với bất kỳ trường học nào` |
| `404 Not Found` | Không tìm thấy ID báo giá | `B2B quote request not found with ID: ...` |
| `409 Conflict` | Báo giá đã được duyệt trước đó hoặc email School Admin đã tồn tại | `Quote request QT-2026-4821 has already been approved` |
