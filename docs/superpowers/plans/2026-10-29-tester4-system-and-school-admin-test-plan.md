# Kế hoạch & Ma trận Kiểm thử Toàn diện: Tester 4 (Quản trị Hệ thống & Quản trị Trường)

> **Tài liệu kiểm thử dành cho:** Tester 4 (Vai trò `SYSTEM_ADMIN` & `SCHOOL_ADMIN`)  
> **Dự án:** Nền tảng Học Lịch Sử Tương Tác HistoryTalk (SWD392 / Capstone Project)  
> **Mục tiêu ưu tiên:** Dựng toàn bộ Master Data trường học cho Tester 5 trước 11:30 ngày đầu, sau đó hoàn thành toàn bộ test cases E2E, UAT, UI, Dashboard và bảo mật.

---

## I. Tổng quan & Tiến độ Thực hiện (Timeline & SLA Bàn giao)

```
       BUỔI SÁNG NGÀY 1 (08:00 - 11:30)                    BUỔI CHIỀU NGÀY 1 (13:30 - 17:30)
 ┌──────────────────────────────────────────────┐    ┌──────────────────────────────────────────────┐
 │ • Tạo Trường học & Tài khoản School Admin     │    │ • Test Quản trị Hệ thống (System Admin UI)   │
 │ • Tạo Giáo viên & Phân quyền                 │    │ • Test Gói dịch vụ & Đối soát PayOS (Tester 1)│
 │ • Tạo Lớp học & Gán GV phụ trách             │    │ • Duyệt Token Request & Nội dung (Tester 5)  │
 │ • Import Học sinh (Mẫu chuẩn + Gán vào lớp)  │    │ • Test Khóa phiên Real-time & Edge Cases     │
 └──────────────────────┬───────────────────────┘    └──────────────────────────────────────────────┘
                        │
                 BÀN GIAO DỮ LIỆU
                        ▼
         Tester 5 nhận tài khoản & lớp học
```

### Checklist Bàn giao Dữ liệu cho Tester 5 (Xong trước 11:30)
- [ ] **1 Trường học hợp lệ:** `THPT Chu Văn An` (Mã trường hệ thống tự sinh: `THPT-CHU-VAN-AN`).
- [ ] **1 Tài khoản School Admin:** `admin.chuvanan@historytalk.edu.vn` (Mật khẩu: `Demo@1234`).
- [ ] **2 Tài khoản Giáo viên phụ trách:**
  - GV 1: `gv.su10@historytalk.edu.vn` / `Demo@1234` (Phụ trách Lớp 10A1).
  - GV 2: `gv.su11@historytalk.edu.vn` / `Demo@1234` (Phụ trách Lớp 11B2).
- [ ] **2 Lớp học có học sinh:**
  - Lớp `10A1`: 35 học sinh (Mã lớp: `CVA-10A1-2026`).
  - Lớp `11B2`: 30 học sinh (Mã lớp: `CVA-11B2-2026`).
- [ ] **Tài khoản Học sinh mẫu:** `HS1001` - `long.nh@thptchuvanan.edu.vn` (Mật khẩu được cấp / reset).
- [ ] **Hạn mức Token sẵn sàng:** Trường có `500,000` tokens, GV 1 có `100,000` tokens allowance, mỗi HS có `10,000` tokens ban đầu.

---

## II. Ma trận Kịch bản Kiểm thử Chi tiết

### PHẦN 1: SYSTEM ADMIN (`/staff/admin/*`)

#### 1. Quản trị Trường học (`/staff/admin/schools`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SA-SCH-01** | Tạo trường mới (UI & Logic) | 1. Vào `/staff/admin/schools` $\rightarrow$ Nhấn **"Thêm trường mới"**.<br>2. Kiểm tra form nhập liệu.<br>3. Điền Tên trường, Địa chỉ, Email, SĐT, Quota ban đầu.<br>4. Nhấn **"Lưu"**. | • Tên trường: `THPT Chu Văn An`<br>• Email: `contact@chuvanan.edu.vn`<br>• Quota: `500,000` | • **Không có ô nhập mã trường**: Backend tự sinh `schoolCode` theo quy tắc chuẩn hóa.<br>• Tự động tạo kèm tài khoản `SCHOOL_ADMIN` liên kết với trường.<br>• Toast thông báo tạo trường thành công (`201 CREATED`). | ☐ |
| **SA-SCH-02** | Bảo mật Phiếu in / Thông tin | 1. Sau khi tạo trường và tài khoản School Admin thành công.<br>2. Kiểm tra màn hình xác nhận / pop-up kết quả. | • Thao tác vừa hoàn thành ở SA-SCH-01 | • **Tuyệt đối KHÔNG hiện phiếu in/export thông tin đăng nhập** cho tài khoản `SCHOOL_ADMIN` (tuân thủ bảo mật tài khoản cấp quản trị; mật khẩu kích hoạt qua luồng email hoặc set password an toàn). | ☐ |
| **SA-SCH-03** | Tìm kiếm, Lọc & Phân trang | 1. Nhập từ khóa tên trường, mã trường hoặc email liên hệ.<br>2. Lọc theo trạng thái (`ACTIVE`, `INACTIVE`, `SUSPENDED`).<br>3. Chuyển trang (Pagination: 10, 20, 50 dòng). | • Search: `Chu Văn An` hoặc `chuvanan`<br>• Filter: `ACTIVE` | • Search phản hồi đúng theo cơ chế debounce (300-500ms).<br>• Bảng hiển thị đúng danh sách lọc.<br>• Phân trang cập nhật số trang, tổng số bản ghi và URL query param tương ứng. | ☐ |
| **SA-SCH-04** | Chi tiết & Xử lý 404 | 1. Bấm xem chi tiết 1 trường hợp lệ.<br>2. Thay đổi URL trình duyệt với UUID không tồn tại. | • URL: `/staff/admin/schools/00000000-0000-0000-0000-000000000000` | • ID hợp lệ: Hiển thị đầy đủ thông tin trường, ngày tạo, quota, danh sách quản trị viên trường.<br>• ID không tồn tại: Giao diện hiển thị trang 404/Empty State với thông điệp: **"Không tìm thấy trường"** (không crash trắng trang). | ☐ |
| **SA-SCH-05** | Khóa trường (E2E Cross-role) | 1. Chuyển trạng thái 1 trường sang `SUSPENDED` / `LOCKED`.<br>2. Mở trình duyệt ẩn danh khác, đăng nhập bằng tài khoản Giáo viên và Học sinh thuộc trường đó. | • School ID trường vừa khóa<br>• Tài khoản GV và HS thuộc trường | • Giáo viên/Học sinh khi đăng nhập hoặc thực hiện bất kỳ thao tác nào lập tức nhận **Modal thông báo: "Trường của bạn đã bị khóa/tạm ngưng dịch vụ. Vui lòng liên hệ quản trị viên"**.<br>• Toàn bộ tương tác chat AI, làm bài, tạo nội dung bị chặn hoàn toàn. | ☐ |

---

#### 2. Quản trị Tài khoản theo Vai trò (`/staff/admin/accounts/[role]`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SA-ACC-01** | Dashboard Thẻ chỉ số (Metric Cards) | 1. Mở lần lượt từng tab vai trò: `ADMIN`, `STAFF`, `TEACHER`, `SCHOOL_ADMIN`, `SCHOOL_STUDENT`, `USER`.<br>2. Đối soát 4 thẻ thống kê trên đầu trang: **"Tổng tài khoản"**, **"Đang hoạt động"**, **"Đang bị khoá"**, **"Tổng token còn lại"**. | • Chọn từng vai trò trên thanh tab menu | • Số liệu hiển thị trên 4 thẻ phải **khớp 100%** với dữ liệu thực tế trong DB và tổng số dòng tương ứng trong bảng dữ liệu phân trang.<br>• Khi chuyển vai trò, các thẻ cập nhật mượt mà theo đúng vai trò được chọn. | ☐ |
| **SA-ACC-02** | Validation khi Tạo tài khoản | 1. Nhấn **"Thêm tài khoản mới"**.<br>2. Điền thông tin cá nhân.<br>3. Nhập mật khẩu: `Password123!` và xác nhận mật khẩu: `PasswordDifferent456!`.<br>4. Nhấn **"Xác nhận"**. | • Password: `Password123!`<br>• Confirm: `PasswordMismatch456!` | • Form báo lỗi tại trường Xác nhận mật khẩu: **"Mật khẩu xác nhận không khớp"**.<br>• Nút Submit bị disable hoặc không gửi request lên backend.<br>• Sửa mật khẩu khớp $\rightarrow$ Tạo tài khoản thành công. | ☐ |
| **SA-ACC-03** | Chỉnh sửa & Xem Gói tài khoản | 1. Mở modal chỉnh sửa 1 tài khoản người dùng.<br>2. Thay đổi họ tên, số điện thoại.<br>3. Kiểm tra mục **"Gói dịch vụ hiện tại"** (Subscription Plan). | • User ID bất kỳ đang hoạt động | • Cập nhật thông tin profile thành công.<br>• Hiển thị đúng tên gói (Free/Standard/Enterprise), ngày kích hoạt, ngày hết hạn và lượng token khả dụng của gói. | ☐ |
| **SA-ACC-04** | Khóa phiên đồng thời (Real-time Revocation) | 1. Mở 2 trình duyệt: Trình duyệt A đăng nhập tài khoản User X (đang tương tác chat/lướt app).<br>2. Trình duyệt B đăng nhập `SYSTEM_ADMIN`, vào tìm User X và bấm **"Khóa tài khoản"**.<br>3. Ở trình duyệt A, User X bấm chuyển trang hoặc gửi 1 prompt chat. | • User X đang active ở Browser A<br>• Admin khóa User X ở Browser B | • User X lập tức bị chặn, màn hình bật hộp thoại: **"Tài khoản của bạn đã bị khóa bởi Quản trị viên"**.<br>• Session/JWT bị thu hồi (Revoked), tự động đăng xuất về trang Login.<br>• Khi Admin mở khóa $\rightarrow$ User X đăng nhập lại bình thường. | ☐ |

---

#### 3. Quản trị Gói Dịch vụ & Doanh thu (`/staff/admin/packages`, `/staff/admin/payment/history`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SA-PKG-01** | Quản lý Gói & Đồng bộ Pricing | 1. Tạo mới 1 gói dịch vụ.<br>2. Sửa giá và cập nhật thời hạn.<br>3. Bật/Tắt toggle Active của gói.<br>4. Mở tab khác vào `/pricing` (Giao diện của Tester 1). | • Tên: `Gói Trải Nghiệm Sử Học`<br>• Giá: `99,000 VND` $\rightarrow$ `89,000 VND`<br>• Thời hạn: `30 ngày`<br>• Token: `200,000` | • Thao tác CRUD gói dịch vụ phản hồi ngay lập tức.<br>• Thay đổi trạng thái/giá/tên gói **phản ánh lập tức tại trang `/pricing`** của Tester 1 (không bị cache sai lệch).<br>• Gói bị tắt (Inactive) lập tức ẩn khỏi trang `/pricing`. | ☐ |
| **SA-PKG-02** | Thống kê Tài chính & Token Pool | 1. Quan sát thẻ KPI: **"Doanh thu tổng gói"** và **"Tổng token pool"**.<br>2. Đối soát với tổng các giao dịch thành công trong hệ thống. | • Toàn bộ giao dịch `PAID` trong hệ thống | • Số tiền hiển thị đúng format tiền tệ VNĐ (vd: `15,450,000 ₫`).<br>• Số liệu trên thẻ khớp hoàn toàn với tổng tiền của các giao dịch thành công và tổng dung lượng token đã bán. | ☐ |
| **SA-PKG-03** | Lịch sử Thanh toán & Đối soát PayOS | 1. Phối hợp với **Tester 1**: Tester 1 thực hiện thanh toán mua gói qua PayOS/QR Code.<br>2. Tester 4 vào `/staff/admin/payment/history` kiểm tra. | • Giao dịch mới phát sinh từ Tester 1 | • Giao dịch mới nhất xuất hiện ngay trên đầu bảng lịch sử.<br>• Trạng thái hiển thị đúng: `PAID` / `THÀNH CÔNG`.<br>• Số tiền, mã tham chiếu PayOS, thời gian thanh toán và tên người mua khớp chuẩn xác. | ☐ |

---

### PHẦN 2: SCHOOL ADMIN (`/school/*`)

#### 1. Quản lý Giáo viên (`/school/teachers`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SCH-TCH-01** | Tạo Giáo viên & Bắt trùng | 1. Vào `/school/teachers` $\rightarrow$ Nhấn **"Thêm giáo viên"**.<br>2. Nhập họ tên, username, email trường.<br>3. Nhấn lưu $\rightarrow$ Thành công.<br>4. Tiếp tục bấm thêm mới và nhập lại đúng username đó. | • Username: `gv_lichsu01`<br>• Email: `gv01@school.edu.vn` | • Hệ thống bắt trùng và hiển thị thông báo lỗi rõ ràng: **"Tên tài khoản đã tồn tại trong hệ thống"** (trả về `409 CONFLICT` từ backend, không bị crash `500`). | ☐ |
| **SCH-TCH-02** | Khóa, Reset Mật khẩu & Xóa GV | 1. Chọn 1 giáo viên trong danh sách.<br>2. Thử lần lượt các thao tác: Khóa tài khoản, Đặt lại mật khẩu, Xóa tài khoản. | • 1 tài khoản Giáo viên được chọn | • Khi bấm Xóa/Khóa: Luôn hiển thị **Hộp thoại xác nhận (Confirmation Dialog)** cảnh báo hành động.<br>• Đặt lại mật khẩu: Hiển thị popup chứa mật khẩu mới an toàn.<br>• Xóa GV: Tài khoản chuyển trạng thái Soft-deleted, không thể đăng nhập. | ☐ |
| **SCH-TCH-03** | Thao tác Hàng loạt (Batch Operations) | 1. Tích chọn checkbox nhiều giáo viên (3-5 dòng) hoặc bấm **"Chọn tất cả"**.<br>2. Chọn action hàng loạt: **"Khóa các tài khoản đã chọn"** / **"Mở khóa các tài khoản đã chọn"**. | • Danh sách checkbox các GV | • Thanh công cụ Batch Action nổi lên báo số lượng dòng đã chọn.<br>• Thao tác áp dụng đồng thời cho tất cả các bản ghi được chọn; bảng tự động refresh trạng thái mới. | ☐ |

---

#### 2. Quản lý Học sinh & Import Excel/CSV (`/school/students`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SCH-STU-01** | Import CSV Preview & Inline Edit | 1. Tải template chuẩn: `/api/v1/school-admin/students/template`.<br>2. Upload file có 10 dòng (trong đó có 2 dòng cố tình sai định dạng email hoặc thiếu ngày sinh).<br>3. Xem màn hình Bản xem trước (Preview Grid). | • File CSV có dòng lỗi định dạng | • Preview Grid hiển thị dữ liệu dạng bảng có thể **chỉnh sửa trực tiếp từng ô (Inline edit)**.<br>• Dòng lỗi được đánh dấu màu nổi bật (viền đỏ/vàng) kèm tooltip chú thích lỗi cụ thể.<br>• Khi bấm "Nhập": **Chỉ nhập các dòng hợp lệ** (hoặc sau khi sửa trực tiếp trên ô lỗi thì nhập được toàn bộ). | ☐ |
| **SCH-STU-02** | Boundary & Edge Cases File Import | Thử nghiệm lần lượt 4 kịch bản file:<br>a) File sai định dạng/cột lạ.<br>b) File rỗng (0 bytes hoặc chỉ có header).<br>c) File chứa mã học sinh bị trùng lặp.<br>d) File chứa ~500 dòng học sinh. | • 4 file test fixtures tương ứng | • File sai mẫu/rỗng: Báo lỗi định dạng/file không chứa dữ liệu hợp lệ.<br>• Trùng mã HS: Báo rõ dòng bị trùng mã và từ chối dòng trùng.<br>• File 500 dòng: Quá trình import hiển thị thanh tiến trình (Progress Bar), không bị timeout/crash bộ nhớ, phân bổ quota chính xác. | ☐ |
| **SCH-STU-03** | Khóa, Vô hiệu hóa & Đặt lại Mật khẩu | 1. Chọn 1 học sinh $\rightarrow$ Đổi trạng thái sang `INACTIVE` / Khóa.<br>2. Bấm **"Đặt lại mật khẩu"**.<br>3. Dùng tài khoản học sinh vừa đổi mật khẩu đăng nhập tại `/login`. | • 1 học sinh trong danh sách | • Popup hiển thị mật khẩu ngẫu nhiên mới sinh.<br>• Học sinh dùng mật khẩu mới đăng nhập thành công vào hệ thống. | ☐ |

---

#### 3. Quản lý Lớp học (`/school/classrooms`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SCH-CLS-01** | Tạo Lớp & Dropdown Trạng thái | 1. Vào `/school/classrooms` $\rightarrow$ Nhấn **"Tạo lớp học"**.<br>2. Nhập tên lớp, chọn Khối, Niên khóa, gán Giáo viên phụ trách.<br>3. Kiểm tra danh sách trạng thái lớp học. | • Tên lớp: `10A1`<br>• Khối: `10`<br>• Niên khóa: `2026`<br>• GV: `gv.su10@historytalk.edu.vn` | • Backend sinh mã lớp semantic `classCode` (vd: `CVA-10A1-2026`).<br>• Dropdown trạng thái lớp học **KHÔNG CÒN lựa chọn "Tạm ngưng"** (chỉ có các trạng thái hợp lệ: `ACTIVE` / `COMPLETED` / `ARCHIVED`). | ☐ |
| **SCH-CLS-02** | Thêm Học sinh vào Lớp bằng File | 1. Vào chi tiết lớp `10A1` $\rightarrow$ Chọn **"Thêm học sinh từ file"**.<br>2. Upload danh sách học sinh.<br>3. Xem trước, chỉnh sửa nếu cần, tích chọn các dòng cần thêm và xác nhận. | • File CSV danh sách học sinh của lớp | • Chỉ các học sinh được tick chọn mới được thêm vào lớp.<br>• Chỉ số **Sĩ số lớp (Student Count)** trên bảng và chi tiết lớp cập nhật tức thì (vd: từ 0 lên 35 học sinh). | ☐ |
| **SCH-CLS-03** | Điều phối GV & Bàn giao Tester 5 | 1. Gỡ 1 học sinh khỏi lớp $\rightarrow$ Sĩ số giảm 1.<br>2. Đổi giáo viên phụ trách lớp từ GV A sang GV B.<br>3. Phối hợp với **Tester 5**: Đăng nhập tài khoản GV A và GV B. | • Lớp `10A1`<br>• Chuyển từ GV A sang GV B | • GV A không còn quyền quản trị bài tập/học sinh lớp đó.<br>• GV B lập tức nhìn thấy lớp xuất hiện trong danh sách lớp phụ trách tại Dashboard Giáo viên của Tester 5. | ☐ |

---

#### 4. Token & Giám sát Nội dung (`/school/tokens`, `/school/content`)

| Mã TC | Hạng mục | Các bước thực hiện | Dữ liệu đầu vào (Input) | Kết quả kỳ vọng (Expected Output) | Trạng thái |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **SCH-TOK-01** | Quản lý Quota & Chặn Cấp Vượt Mức | 1. Vào `/school/tokens`: Xem hạn mức tổng trường (Allocated vs Unallocated Quota).<br>2. Cấp thêm token cho 1 Lớp học hoặc Giáo viên.<br>3. Nhập số lượng token vượt quá lượng Unallocated Quota khả dụng của trường. | • Unallocated: `100,000`<br>• Nhập cấp: `150,000` | • Cấp trong hạn mức: Thành công, quota tự động trừ ở kho trường và cộng vào tài khoản nhận.<br>• Cấp vượt hạn mức: **Hệ thống chặn ngay lập tức** và cảnh báo: **"Vượt quá hạn mức token chưa phân bổ của trường"**. | ☐ |
| **SCH-TOK-02** | Workflow Phê duyệt Yêu cầu Token (Ticket) | 1. Phối hợp **Tester 5**: Giáo viên gửi Token Request (xin cấp thêm 50,000 token).<br>2. School Admin vào `/school/tokens` (tab Yêu cầu duyệt).<br>3. Thử nghiệm: Bấm **Duyệt (Approve)** hoặc **Từ chối (Reject)** kèm lý do ghi chú. | • Ticket xin token của Tester 5 | • Khi Duyệt: Token tự động chuyển từ Quota trường sang cho Giáo viên, status chuyển `APPROVED`.<br>• Khi Từ chối: Status chuyển `REJECTED`, lý do từ chối phản hồi về tài khoản của Tester 5. | ☐ |
| **SCH-CNT-01** | Giám sát & Phê duyệt Nội dung Địa phương | 1. Tester 5 (GV) tạo 1 Bối cảnh lịch sử / Bộ câu hỏi trắc nghiệm địa phương và submit duyệt (`PENDING_REVIEW`).<br>2. School Admin vào `/school/content`: Lọc theo trạng thái `PENDING_REVIEW`.<br>3. Chọn tất cả các nội dung $\rightarrow$ Bấm **"Duyệt" (ACTIVE)** hoặc **"Ẩn" (INACTIVE)**.<br>4. Dùng tài khoản Học sinh kiểm tra. | • Nội dung local context do Tester 5 tạo | • Bộ lọc hoạt động chính xác.<br>• Hành động Duyệt/Ẩn cập nhật trạng thái ngay lập tức.<br>• Phía Học sinh: **Chỉ xem được nội dung đã Duyệt**, hoàn toàn không thấy nội dung ở trạng thái Ẩn hoặc Chờ duyệt. | ☐ |
| **SCH-DB-01** | Đối soát Dashboard Tổng quan Trường | 1. Vào trang tổng quan `/school` (Dashboard).<br>2. Kiểm tra các chỉ số: Số lượng lớp học, Tổng số giáo viên, Tổng số học sinh, Tỷ lệ token tiêu thụ HS vs GV. | • Dữ liệu các module con | • Số liệu hiển thị trên Dashboard **khớp 100% với thực tế cộng dồn** từ các trang con (Classrooms, Teachers, Students, Token Analytics). | ☐ |

---

## III. Dữ liệu Test Mẫu Chuẩn bị (Test Fixtures)

### 1. Template CSV Hợp lệ (`students_valid_template.csv`)
```csv
student_code,full_name,email,dob,gender,phone_number,password,additional_token
HS1001,Nguyễn Hoàng Long,long.nh@thptchuvanan.edu.vn,2008-03-12,MALE,0912345601,,10000
HS1002,Trần Thị Mai Anh,maianh.tt@thptchuvanan.edu.vn,2008-07-25,FEMALE,0912345602,,10000
HS1003,Lê Quốc Bảo,bao.lq@thptchuvanan.edu.vn,2008-11-05,MALE,0912345603,,10000
HS1004,Phạm Quỳnh Chi,chi.pq@thptchuvanan.edu.vn,2008-01-19,FEMALE,0912345604,,10000
HS1005,Vũ Tuấn Kiệt,kiet.vt@thptchuvanan.edu.vn,2008-09-30,MALE,0912345605,,10000
```

### 2. File Chứa Dòng Lỗi Định Dạng (`students_error_format.csv`)
```csv
student_code,full_name,email,dob,gender,phone_number,password,additional_token
HS1006,Đỗ Gia Huy,invalid-email-format,2008-05-10,MALE,0912345606,,10000
HS1007,Hoàng Yến Nhi,nhi.hy@thptchuvanan.edu.vn,,FEMALE,0912345607,,10000
HS1008,Bùi Tiến Dũng,dung.bt@thptchuvanan.edu.vn,2008-04-18,UNKNOWN_GENDER,0912345608,,10000
```

### 3. File Trùng Mã Học Sinh (`students_duplicate_code.csv`)
```csv
student_code,full_name,email,dob,gender,phone_number,password,additional_token
HS1001,Nguyễn Văn Trùng,trung1@thptchuvanan.edu.vn,2008-02-14,MALE,0912345609,,10000
HS1001,Trần Văn Trùng,trung2@thptchuvanan.edu.vn,2008-06-20,MALE,0912345610,,10000
```

---

## IV. Tiêu chuẩn Đánh giá Hoàn thành (Definition of Done - DoD)
1. **Dữ liệu hạt nhân:** Tester 5 đăng nhập được bằng tài khoản GV, truy cập đúng lớp 10A1, 11B2 và học sinh có thể chat AI/làm bài trước 11:30 ngày 1.
2. **Khớp nối Dashboard:** Toàn bộ thẻ số liệu trên Dashboard System Admin và School Admin khớp chính xác 100% với database queries.
3. **Bảo mật:** Khóa tài khoản/trường học có tác dụng ngay lập tức đối với các phiên đang hoạt động (Real-time Revocation).
4. **Đồng bộ Gói:** Bật/tắt/sửa giá gói hiển thị tức thì bên trang `/pricing` của Tester 1.
