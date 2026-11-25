# Kịch Bản Tự Kiểm Thử Toàn Bộ WF-01 & Nghiệp Vụ Liên Quan

> **Người thực hiện:** Trần Quốc Dinh (`DinhTQSE`)  
> **Môi trường:** `https://historytalk.online` (hoặc Staging trước khi lên Production)  
> **Bộ CSV test đi kèm:** [`docs/wf1_defense/csv_test/`](./csv_test/) (toàn bộ dùng đuôi email riêng `@selftest26.edu.vn`, không trùng dữ liệu production cũ).

---

## Chuẩn bị trước khi Test

1. **Đảm bảo Server BE đã deploy bản sửa mới nhất** (đặc biệt các fix ở `PaymentService`, `AuthServiceImpl`, `granularity=year`, `SchoolManagementServiceImpl`, `AbstractStudentImportProcessor`) và nếu trên FE chưa sửa các lỗi trong [`FE_BUG_FIX_HANDOFF_WF1.md`](./FE_BUG_FIX_HANDOFF_WF1.md), nắm rõ những chỗ cần đối chiếu riêng.
2. **Mở trình duyệt ẩn danh (Incognito)** để tránh đụng cookie/session cũ; mở kèm **DevTools (`F12`) -> tab Console & Network (Preserve log)** để soi lỗi `4xx`/`5xx`.
3. **Tài khoản System Admin khởi điểm:** `systemadmin@historytalk.com` / `123456789`.
4. **Quy tắc đặt tên dữ liệu test:** Mỗi lượt test dùng một hậu tố ngày/giờ riêng (ví dụ `ST2611`) để không trùng `school_code` hay `email` với lượt trước.

---

## Giai đoạn 1: Public Marketing, Bảng giá & Báo giá B2B (`/for-schools`, `/pricing`, `/login`)

- [ ] **1.1. Bảng giá B2B (`/for-schools`):**
  - Kiểm tra 3 gói cố định hiển thị đúng định mức Sprint 9:
    - `Small`: `600` HS / `15` GV · `435.750.000` token · giảm `10%` · `63.180.000đ/năm học`.
    - `Medium`: `1.400` HS / `35` GV · `1.016.750.000` token · giảm `20%` · `131.040.000đ/năm học`.
    - `Large`: `2.000` HS / `50` GV · `1.452.500.000` token · giảm `30%` · `163.800.000đ/năm học`.
- [ ] **1.2. Máy tính giá B2B (`Gói tuỳ chỉnh`):**
  - Nhập `500` HS / `10` GV → Chiết khấu `0%`, Quỹ `360.500.000 token`, Tổng `58.000.000đ`.
  - Nhập `800` HS / `20` GV → Chiết khấu `10%`, Quỹ `581.000.000 token`, Tổng `84.240.000đ`.
  - Nhập `1800` HS / `45` GV → Chiết khấu `30%`, Quỹ `1.307.250.000 token`, Tổng `147.420.000đ`.
  - Nhập mã ưu đãi `EDU2026` → tăng thêm `+5%` (`35%`, còn `136.890.000đ`). Nhập `INVALID99` → báo `"Mã này không hợp lệ hoặc đã hết hạn."`. Xóa mã ưu đãi trước khi gửi.
- [ ] **1.3. Gửi yêu cầu báo giá (`ATC-018`):**
  - Chọn `600` HS / `15` GV (`Gói tuỳ chỉnh`), điền tên trường `THPT SelfTest Quote ST2611`, mã `STQ_2611`, email `quote.st2611@selftest26.edu.vn`, SĐT `0930000001` → Bấm gửi.
  - **Kỳ vọng:** Trả HTTP `200`/`201`, hiện mã báo giá dạng `QT-2026-XXXX`. Ghi lại mã này.
- [ ] **1.4. Bảng giá B2C (`/pricing`) & Trang pháp lý (`/login`, `/terms`, `/privacy`):**
  - Bật/tắt `Theo tháng` ↔ `Theo năm`: kiểm tra chỉ có 3 gói `Free`, `Plus`, `Pro` (không bị lặp dòng).
  - Mở `/login`: kiểm tra link `/terms` và `/privacy` mở được (`200 OK`, không `404`).
  - Thử đăng nhập sai mật khẩu (`systemadmin@historytalk.com` / `WrongPass123`) → HTTP `401`, toast `"Email hoặc mật khẩu không hợp lệ"`.

---

## Giai đoạn 2: System Admin (`/staff/admin/*`) — Dashboard, Duyệt Quote & Tạo Trường

- [ ] **2.1. Đăng nhập & System Admin Dashboard (`/staff/admin`):**
  - Đăng nhập `systemadmin@historytalk.com` / `123456789` → vào `/staff/admin`.
  - Chuyển qua lại `Tất cả` ↔ `B2C · Khách lẻ` ↔ `B2B · Trường học`: kiểm tra thẻ `Người dùng trường học`, `Nhân vật Hot nhất` (Global vs Local), `Kho nội dung`, `Câu hỏi hay sai nhất` (hiển thị chữ tiếng Việt, không phải UUID).
  - Bấm bộ lọc độ phân giải `Ngày` → `Tuần` → `Tháng` → `Năm`: sau khi deploy BE fix, **`Năm` phải trả HTTP `200`** (nếu test trên server cũ chưa deploy thì bỏ qua nút `Năm`).
- [ ] **2.2. Duyệt Báo giá B2B (`/staff/admin/b2b-quotes`):**
  - Tìm `QT-2026-XXXX` (`THPT SelfTest Quote ST2611`).
  - Đổi trạng thái sang `Đã liên hệ` (`CONTACTED`) + ghi chú → lưu thành công.
  - Bấm **`Duyệt và tạo trường`** → xác nhận → hộp thoại hiện `schoolCode` (`STQ_2611`), `username` và `initialPassword` cấp một lần.
- [ ] **2.3. Danh sách Trường & Tạo Trường Trực Tiếp (`F-02`, `DS-01a`):**
  - Vào `/staff/admin/schools`: gõ `ST2611` không cần nhấn Enter → bảng tự lọc sau ~300ms (`BUG-UXUI-004`). Thu nhỏ trình duyệt (~`1000px`) cuộn ngang bảng → cột tên trường giữ nền đục không đè chữ (`BUG-UXUI-001`).
  - Mở URL `/staff/admin/schools/00000000-0000-0000-0000-000000000000` → kiểm tra xem có hiện `"Không tìm thấy trường"` (`SA-SCH-04`, phụ thuộc FE fix `[FE-WF1-03]`).
  - Vào `/staff/admin/schools/new`:
    - Bước 1: Tên `THPT SelfTest Demo ST2611`, gói `Enterprise Medium`, email `contact.st2611@selftest26.edu.vn`, SĐT `0930000002`, địa chỉ `123 Lê Lợi, TP. Hồ Chí Minh`, **tick `Cam kết chính sách nội dung lịch sử địa phương`** → Lưu.
    - Bước 2: Tạo School Admin `Admin SelfTest`, email `admin.st2611@selftest26.edu.vn`, SĐT `0930000003`.
    - **Kỳ vọng:** Lưu lại **`schoolId`**, **`schoolCode`**, **`username`** và **`initialPassword`** hiển thị một lần (chỉ có modal copy/tải CSV, không hiện phiếu in lộ tài khoản admin).
- [ ] **2.4. Chi tiết Trường & Nạp Token (`/staff/admin/schools/[schoolId]`):**
  - Kiểm tra Quỹ token = `1.016.750.000`, chu kỳ `01/09/2026 – 31/05/2027`, hạn mức ngày HS & GV = `5.000`.
  - Bấm **`Nạp thêm token`**: nhập `100000` → xác nhận → Quỹ tăng thành `1.016.850.000`.
  - Mở thử `Gia hạn năm học tới` (kiểm tra thông báo tháng 5, bấm Hủy) và kiểm tra nút `Kích hoạt năm học mới` đang bị khóa.
- [ ] **2.5. Tài khoản, Gói & Lịch sử Thanh toán:**
  - Vào `/staff/admin/accounts/content-admin` → mở modal thêm tài khoản, nhập 2 mật khẩu lệch nhau → nút Tạo bị khóa & báo `"Mật khẩu xác nhận không khớp"`, bấm Hủy.
  - Vào `/staff/admin/subscriptions` → kiểm tra 5 gói tải bình thường (không chỉnh sửa).
  - Vào `/staff/admin/payment/history` → bảng giao dịch tải HTTP `200` (sau khi deploy fix `PaymentService` sẽ vẫn sống kể cả khi đã có đơn top-up trường).
  - Bấm **Đăng xuất** → thử truy cập thẳng `/staff/admin` → bị đá về `/login?next=%2Fstaff%2Fadmin`.

---

## Giai đoạn 3: School Admin — Đổi Mật Khẩu Lần Đầu, Giáo Viên & Lớp Học (`F-01`, `F-03`)

- [ ] **3.1. Đổi mật khẩu lần đầu bắt buộc (`mustChangePassword`):**
  - Đăng nhập bằng `admin.st2611@selftest26.edu.vn` + `initialPassword` ở bước `2.3`.
  - **Kỳ vọng:** Modal `Đổi mật khẩu lần đầu` bật lên, bấm `Esc` hoặc click nền ngoài không tắt được. Đổi sang `SchoolAdmin@2026` → vào thẳng `/school`.
- [ ] **3.2. Dashboard Trường (`/school`):**
  - Kiểm tra quỹ `1.016.850.000`, hạn mức `5.000 / 5.000`.
- [ ] **3.3. Quản lý Giáo viên (`/school/teachers`):**
  - Tạo GV 1: `GV Nguyễn Thị Lan Test`, email `gv1.st2611@selftest26.edu.vn`, SĐT `0931111001`. Ghi lại **`username`** & **`initialPassword`**.
  - Tạo GV 2: `GV Trần Văn Hùng Test`, email `gv2.st2611@selftest26.edu.vn`, SĐT `0931111002`. Ghi lại **`username`** & **`initialPassword`**.
  - Thử tạo lại với email trùng `gv1.st2611@selftest26.edu.vn` → trả HTTP `409` `"Email này đã được dùng cho một tài khoản khác"`.
  - Trên GV 2: thử `Đặt lại mật khẩu` (ghi lại mật khẩu mới), thử `Khóa tài khoản` (có dialog xác nhận) -> `Mở khóa`, thử tích checkbox batch action. Đảm bảo cả 2 GV đều `ACTIVE`.
- [ ] **3.4. Quản lý Lớp học (`/school/classes`):**
  - Kiểm tra modal tạo/sửa lớp không có trạng thái `"Tạm ngưng"` (`SCH-CLS-01`).
  - Tạo 3 lớp:
    - **`10A1`** (Khối `10`, Năm học `2026`, GVCN: GV 1)
    - **`10A2`** (Khối `10`, Năm học `2026`, GVCN: GV 2)
    - **`12A1`** (Khối `12`, Năm học `2026`, GVCN: GV 1)

---

## Giai đoạn 4: Kiểm Thử Toàn Bộ 9 File CSV (`DS-01a`, `DS-01b`, `EX01–EX04`)

> **Mẹo UI:** Nếu FE chưa sửa bug nút `"Xong"` ở modal CSV trong lớp, luôn đóng modal kết quả bằng dấu **`X`** ở góc trên phải trước khi mở file tiếp theo.

- [ ] **4.1. `DS-01a` — Import 30 học sinh chuẩn vào Lớp `10A1` (`/school/classes/<10A1>`):**
  - Mở `Nhập CSV` → chọn [`01_test_30_students_valid.csv`](./csv_test/01_test_30_students_valid.csv).
  - **Kỳ vọng:** Preview Grid báo `Hợp lệ (30) / Có lỗi (0)`. Xác nhận nhập → tạo đủ `30` tài khoản `{school_code}_hs_ts001`..`ts030` với mật khẩu `HistoryTalk@2026`. Sĩ số lớp `10A1` = `30`. Quỹ token trường vẫn nguyên **`1.016.850.000`** (không trừ trước).
- [ ] **4.2. `DS-01b` (File 1) — Dòng lỗi & trùng mã vào Lớp `10A2` (`/school/classes/<10A2>`):**
  - Chọn [`02_test_invalid_rows_and_duplicate.csv`](./csv_test/02_test_invalid_rows_and_duplicate.csv).
  - **Kỳ vọng:** Preview Grid báo `Tất cả (7)`, `Hợp lệ (4)`, `Có lỗi (3)`:
    - Dòng `3` (`TS102`): thiếu họ và tên
    - Dòng `5` (`TS104`): thiếu họ và tên
    - Dòng `7` (`TS101`): trùng mã với dòng 2
  - Thử bấm **sửa trực tiếp trên ô (inline edit)** dòng `3` (`TS102`) thành `Lê Văn Sửa Test` → dòng lỗi giảm từ `3` xuống `2`, hợp lệ tăng lên `5`.
  - Xác nhận nhập `5` học sinh → lớp `10A2` có `5` HS (`TS101`, `TS102`, `TS103`, `TS105`, `TS106`), 2 dòng lỗi còn lại bị bỏ qua sạch sẽ.
- [ ] **4.3. `DS-01b` (File 2) — Thiếu cột bắt buộc (`MSG27`) tại `/school/students`:**
  - Vào `/school/students` → bấm `Tải file mẫu` (kiểm tra tải `200 OK`).
  - Bấm `Import danh sách (CSV)` → chọn [`03_test_missing_required_columns_MSG27.csv`](./csv_test/03_test_missing_required_columns_MSG27.csv).
  - **Kỳ vọng:** Hiện banner đỏ `"File không đúng định dạng danh sách học sinh"` liệt kê thiếu cột `Họ và tên, Ngày sinh` (`MSG27`). Đóng modal, không có bản ghi rác nào được tạo.
- [ ] **4.4. Kiểm tra cột `student_code` tùy chọn (`04_test_missing_student_code_optional.csv`):**
  - Thử tải [`04_test_missing_student_code_optional.csv`](./csv_test/04_test_missing_student_code_optional.csv) lên Preview → xác nhận FE/BE coi `student_code` là tùy chọn (hiển thị hợp lệ; đóng hủy nếu không cần tạo thêm).
- [ ] **4.5. Import lớp 12 cho kịch bản cuối năm (`05_test_grade12_rollover.csv`):**
  - Vào lớp `12A1` → nhập [`05_test_grade12_rollover.csv`](./csv_test/05_test_grade12_rollover.csv) → tạo `2` HS khối 12 (`TS1201`, `TS1202`).
- [ ] **4.6. Test 4 file ngoại lệ biên (`EX01–EX04`) trong modal CSV (chỉ xem Preview, KHÔNG bấm lưu):**
  - [`06_test_ex01_empty.csv`](./csv_test/06_test_ex01_empty.csv) → Báo `"File trống."`, khóa nút nhập.
  - [`07_test_ex02_corrupted_binary.csv`](./csv_test/07_test_ex02_corrupted_binary.csv) → Báo `"Không nhận ra cột họ tên, mã HS hay email..."`, khóa nút nhập.
  - [`08_test_ex03_xss_sqli_security.csv`](./csv_test/08_test_ex03_xss_sqli_security.csv) → `<script>alert(1)</script>` hiển thị dạng text thuần, **không bật popup JS alert**. Đóng hủy.
  - [`09_test_ex04_extreme_boundary.csv`](./csv_test/09_test_ex04_extreme_boundary.csv) → Tất cả `6/6` dòng đều bị gắn lỗi (`>30` ký tự mã HS, `>150` ký tự tên, `2099-12-31` < 5 tuổi, `1800-01-01` > 30 tuổi, `ALIEN` sai giới tính, `token` âm có cảnh báo bỏ qua). Đóng hủy.

---

## Giai đoạn 5: Thao Tác Lớp, Danh Bạ Học Sinh, Token Policy & PayOS

- [ ] **5.1. Chuyển lớp & Gỡ học sinh (`10A1` ↔ `10A2`):**
  - Trong `10A1`, chuyển `TS030` sang `10A2` (`Chuyển lớp`) → `10A1` còn `29`, `10A2` lên `6`.
  - Trong `10A1`, gỡ `TS029` khỏi lớp (`Xóa khỏi lớp`) → `10A1` còn `28`. Giữ `TS001` (`ts001@selftest26.edu.vn`) `ACTIVE` trong `10A1`.
- [ ] **5.2. Danh bạ Học sinh (`/school/students`):**
  - Thu nhỏ trình duyệt (`1000px`) cuộn ngang → 3 cột `Checkbox`, `Mã HS`, `Học sinh` dính cố định nền đục (`BUG-UXUI-003`).
  - Gõ `TS001` không nhấn Enter → tự lọc (`BUG-UXUI-004`).
  - Mở Sửa `TS002`, nhập ngày sinh `01/01/1800` → bị chặn `"Ngày sinh quá xa: học sinh tối đa 30 tuổi"` (`BUG-UXUI-005`). Hủy.
  - Trên `TS028`: bấm `Đặt lại mật khẩu` → popup hiện `TS028@2026`; thử `Khóa` -> `Mở khóa`.
- [ ] **5.3. Hạn mức Token & PayOS Top-Up (`/school/tokens`):**
  - Nhập `0` vào hạn mức HS → bị chặn `"Tối thiểu 100 token"`.
  - Đổi hạn mức HS sang `2000`, GV sang `4000`, bấm Lưu → vào `/school` Dashboard xác nhận hiện `2.000 / 4.000`.
  - Ở `Mua thêm token`: nhập `50000` → báo `"Tối thiểu 100.000 token."`; nhập `100000` → hiện `15.000đ` → bấm `Thanh toán qua PayOS` → xác nhận chuyển sang trang `pay.payos.vn` (`15,000 vnd`), **KHÔNG thanh toán**, bấm quay lại `/school/tokens`, kiểm tra `Lịch sử mua token` có đơn `Chờ thanh toán`.

---

## Giai đoạn 6: Luồng Giáo Viên, Học Sinh, Xin Token 3 Cấp & RBAC

- [ ] **6.1. Đăng nhập Học sinh `TS001` (`ts001@selftest26.edu.vn` / `HistoryTalk@2026`):**
  - Bắt buộc đổi mật khẩu lần đầu → đổi sang `Student@2026`.
  - Vào `/classes` → thấy `Lớp 10A1`, GVCN `GV Nguyễn Thị Lan Test`, hạn mức `0 / 2.000 token`.
  - Thử truy cập thẳng `/teaching`, `/school`, `/staff/admin` → tất cả đều bị chặn/đẩy về `/classes`.
- [ ] **6.2. Học sinh gửi Ticket xin Token (`STC-011`, `MSG38`):**
  - Bấm `Xin thêm token hôm nay`: để trống lý do / nhập `50` token → hiện validate tiếng Việt.
  - Gửi yêu cầu `500` token với lý do `"Can them token on thi"` → HTTP `201`, nút bị khóa kèm `"Yêu cầu hôm nay đang chờ thầy cô duyệt."`. Đăng xuất.
- [ ] **6.3. Giáo viên 1 (`gv1.st2611@selftest26.edu.vn`) nhận lớp & chuyển tiếp Ticket (`STC-012`):**
  - Đăng nhập GV 1 → bắt buộc đổi mật khẩu lần đầu (`Teacher@2026`).
  - Vào `/teaching` → chỉ thấy `10A1` (`28` HS) & `12A1` (`2` HS), không thấy `10A2`, **không có nút nhập CSV**. Thử vào `/school` hoặc `/staff/admin` → bị chặn.
  - Vào `/teaching/token-requests` → tìm yêu cầu `500` token của `TS001` → bấm **`Chuyển lên`** (`PENDING_ADMIN`). Đăng xuất.
- [ ] **6.4. School Admin duyệt Ticket & Kiểm tra chênh lệch:**
  - Đăng nhập `admin.st2611@selftest26.edu.vn` / `SchoolAdmin@2026` → vào `/school/tokens`.
  - Bấm `Duyệt` ticket của `TS001`: thử sửa số lượng lên `600` → bị chặn `"Không được vượt quá số token đã xin (500)"`; duyệt đúng `500` → thành công `APPROVED`. Đăng xuất.
  - Đăng nhập lại `TS001` → `/classes` hiện hạn mức mới **`2.500` token** (`gồm 500 token thầy cô duyệt thêm`) và nút xin token tiếp tục bị khóa trong ngày (`MSG38`). Đăng xuất.

---

## Giai đoạn 7: Khóa Tài Khoản & Kết Thúc Năm Học (`Academic Year Rollover`, `ATC-011`)

- [ ] **7.1. Kết thúc năm học (`2026–2027`):**
  - Đăng nhập School Admin `admin.st2611@selftest26.edu.vn` → vào `/school/classes` → bấm **`Kết thúc năm học…`**.
  - Chọn **`Năm học 2026–2027`** (lưu ý đổi từ mặc định `2025–2026`), giữ tick `"Khóa tài khoản học sinh khối 12 (đã tốt nghiệp)"` → Xác nhận.
  - **Kỳ vọng:** Trả HTTP `200`, lưu trữ `3` lớp (`ARCHIVED`), khóa `2` HS khối 12 (`TS1201`, `TS1202`), quỹ token vẫn nguyên vẹn.
  - Vào `/school/students`: kiểm tra `TS1201`, `TS1202` mang trạng thái `Đã khóa`; `TS001`..`TS106` vẫn `Hoạt động`. Đăng xuất.
- [ ] **7.2. Xác nhận chặn đăng nhập Khối 12 sau tốt nghiệp:**
  - Thử đăng nhập `ts1201@selftest26.edu.vn` / `HistoryTalk@2026` → bị chặn HTTP `401` (với BE đã vá trả thông báo vô hiệu hóa `ACCOUNT_DEACTIVATED`).
  - Đăng nhập `ts001@selftest26.edu.vn` / `Student@2026` → vẫn đăng nhập bình thường, `/classes` hiển thị lớp `10A1` kèm nhãn `Đã lưu trữ`.
- [ ] **7.3. Kiểm tra lại `/staff/admin/payment/history` (sau khi đã tạo đơn PayOS top-up ở 5.3):**
  - Đăng nhập `systemadmin@historytalk.com` → vào `/staff/admin/payment/history` → xác nhận bảng tải HTTP `200`, hiển thị dòng `Nạp quỹ token trường — THPT SelfTest Demo ST2611` (`15.000 ₫`) không bị crash `500`.
