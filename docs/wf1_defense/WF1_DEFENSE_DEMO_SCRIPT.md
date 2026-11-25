# Kịch Bản Demo WF-01 Trước Hội Đồng — Trần Quốc Dinh (SE196495)

> **Chủ đề Workflow 1:** *B2B School Onboarding, Classroom Setup & Bulk Student Enrollment* (`F-01`, `F-02`, `F-03` | Demo Scenarios `DS-01a`, `DS-01b`, `ATC-018`)  
> **Thời lượng khuyến nghị:** `8–10 phút` (trôi chảy, không vấp dữ liệu)  
> **Bộ CSV sạch dành riêng cho Hội đồng:** [`docs/wf1_defense/csv_demo/`](./csv_demo/) (đuôi `@hoidong26.edu.vn`, chưa từng xuất hiện trên production)

---

## 0. Checklist Chuẩn Bị 15 Phút Trước Giờ G (Bắt buộc)

- [ ] **1. Kiểm tra bản deploy BE & FE:**
  - Chắc chắn đã **push bản BE vừa sửa** lên `main` (để EC2 tự deploy: fix NPE `500` ở `/staff/admin/payment/history`, fix `granularity=year`, fix `ACCOUNT_DEACTIVATED`, fix BOM & giờ UTC+7).
  - Nếu FE đã sửa [`FE_BUG_FIX_HANDOFF_WF1.md`](./FE_BUG_FIX_HANDOFF_WF1.md), kiểm tra lại nhanh các màn hình đó; nếu FE **chưa kịp deploy**, xem mục **Mục 5 (Vùng cần tránh khi trình chiếu)** bên dưới!
- [ ] **2. Dọn dữ liệu test cũ (nếu cần):** Nếu trên production vẫn còn đơn PayOS top-up cũ gây crash trên bản BE cũ, phải chắc chắn BE mới đã lên trước khi mở `/staff/admin/payment/history`.
- [ ] **3. Chuẩn bị trình duyệt sạch:**
  - Dùng **1 cửa sổ Chrome ẩn danh mới** (`100% zoom`, độ phân giải `1440×900` hoặc Full HD).
  - Không mở 2 tab đăng nhập 2 role khác nhau trong cùng một cửa sổ trình duyệt vì dùng chung cookie/storage (nếu muốn mở song song 2 role trước hội đồng, mở **1 cửa sổ thường + 1 cửa sổ Incognito** hoặc **2 Chrome Profile khác nhau**).
- [ ] **4. Chuẩn bị sẵn folder chứa 4 file CSV Demo:** Mở sẵn File Explorer trỏ tới [`docs/wf1_defense/csv_demo/`](./csv_demo/):
  1. [`DS01a_Demo_30_HocSinh_10A1.csv`](./csv_demo/DS01a_Demo_30_HocSinh_10A1.csv) — *30 học sinh hợp lệ (`HD001..HD030`)*
  2. [`DS01b_Demo_LoiDong_Va_TrungMa.csv`](./csv_demo/DS01b_Demo_LoiDong_Va_TrungMa.csv) — *7 dòng: 2 thiếu tên (`HDB02`, `HDB04`), 1 trùng mã (`HDB01`)*
  3. [`DS01b_Demo_ThieuCot_MSG27.csv`](./csv_demo/DS01b_Demo_ThieuCot_MSG27.csv) — *Thiếu cột bắt buộc (`MSG27`)*
  4. [`DS01_Demo_Khoi12_Rollover.csv`](./csv_demo/DS01_Demo_Khoi12_Rollover.csv) — *2 HS khối 12 dự phòng nếu hội đồng hỏi Kết thúc năm học*
- [ ] **5. Chuẩn bị file Note/Notepad ghi nhanh thông tin tài khoản phát sinh live** (để copy-paste mật khẩu tạm do hệ thống sinh ra trong 1 giây, không gõ tay nhầm).

### Bảng thông số nhập nhanh khi Demo (Copy sẵn)

| Đối tượng | Giá trị nhập khi Demo |
|---|---|
| **System Admin** | `systemadmin@historytalk.com` / `123456789` |
| **Tên trường tạo live** | `THPT Chuyên Hội Đồng 2026` *(nếu lỡ chạy lại lần 2 thì đổi sang `THPT Thực Hành Hội Đồng 26`)* |
| **Email trường / Liên hệ** | `contact.hd26@hoidong26.edu.vn` / SĐT `0940000100` / Địa chỉ `280 An Dương Vương, TP. Hồ Chí Minh` |
| **Gói B2B chọn** | `Enterprise Medium` (`1.016.750.000` token / năm học · `5.000` token/ngày) |
| **School Admin tạo live** | Họ tên: `Quản Trị Hội Đồng` · Email: `admin.hd26@hoidong26.edu.vn` · SĐT: `0940000101` |
| **Mật khẩu mới khi ép đổi** | `Demo@2026` *(dùng thống nhất cho School Admin, Teacher, Student trong buổi demo để không quên)* |
| **Giáo viên 1** | `GV Nguyễn Thị Lan` · `gv.lan.hd26@hoidong26.edu.vn` · `0940000201` |
| **Lớp học tạo live** | Lớp `10A1` (`Khối 10`, Năm học `2026–2027`, GVCN: `GV Nguyễn Thị Lan`) · Lớp `10A2` (`Khối 10`) |

---

## 1. Phần Mở Đầu & Bài Toán Kinh Doanh B2B (0:00 – 1:00)

- **Thao tác màn hình:** Đứng tại `https://historytalk.online/for-schools`.
- **Thực hiện nhanh:**
  1. Kéo nhẹ qua 3 gói B2B chuẩn (`Small 600 HS`, `Medium 1.400 HS`, `Large 2.000 HS`).
  2. Xuống phần **Tính giá theo quy mô trường**, gõ nhanh `800` học sinh / `20` giáo viên → cho hội đồng thấy công thức tính minh bạch (`581.000.000 token`, chiết khấu bậc thang `10%`, `84.240.000đ/năm học`) *(hoặc mở sẵn báo giá nếu muốn đi qua luồng duyệt Quote)*.
- **Lời thoại gợi ý:**
  > *"Kính thưa Hội đồng, em là Trần Quốc Dinh, phụ trách **Workflow 1 — Khởi tạo trường học B2B, thiết lập lớp học và import hàng loạt học sinh**. Trong mô hình SaaS của HistoryTalk, mỗi trường là một tenant độc lập hoạt động theo năm học 01/09 – 31/05 với **Quỹ Token dùng chung (Pure Shared Pool Pay-As-You-Go)**, không trừ trước token khi tạo tài khoản mà chỉ trừ theo tiêu thụ thực tế trong hạn mức ngày."*

---

## 2. Màn 1 — System Admin Khởi Tạo Trường & Cấp Quyền School Admin (1:00 – 3:00)

- **Thao tác màn hình (`F-02`, `DS-01a`):**
  1. Đăng nhập tại `/login` bằng `systemadmin@historytalk.com` / `123456789` → vào `/staff/admin`.
  2. Lướt nhanh 3 giây **System Admin Dashboard**: nhấn mạnh bộ lọc tách biệt **B2C (Khách lẻ)** vs **B2B (Trường học)** và tổng quỹ token B2B.
  3. Vào **Trường học** (`/staff/admin/schools`) → bấm **Thêm trường** (`/staff/admin/schools/new`):
     - **Bước 1:** Điền `THPT Chuyên Hội Đồng 2026`, gói `Enterprise Medium`, email `contact.hd26@hoidong26.edu.vn`, SĐT `0940000100`, địa chỉ `280 An Dương Vương, TP. Hồ Chí Minh`, **tick cam kết chính sách nội dung lịch sử địa phương** → Bấm Lưu.
     - Chỉ cho hội đồng xem **mã trường tự sinh theo tên + tỉnh/thành** (`CHD-HCM` hoặc tương ứng) và quỹ `1.016.750.000` token.
     - **Bước 2:** Nhập `Quản Trị Hội Đồng` / `admin.hd26@hoidong26.edu.vn` / `0940000101` → Tạo.
     - Hộp thoại hiện **username** và **mật khẩu tạm ngẫu nhiên cấp 1 lần** (`Ht@...`) → bấm **Sao chép** dán ngay vào Notepad.
     - *(Nếu muốn nhấn mạnh bảo mật)*: chỉ rõ hệ thống **không tạo phiếu in hàng loạt cho tài khoản quản trị cấp trường**, chỉ cấp mật khẩu tạm một lần và bắt buộc đổi khi đăng nhập.
  4. **Đăng xuất** khỏi System Admin.
- **Lời thoại gợi ý:**
  > *"System Admin khởi tạo tenant trường mới chỉ trong hai bước: hệ thống tự chuẩn hóa mã trường theo tên và địa danh, khởi tạo quỹ token 1,016 tỷ cho gói Medium, ràng buộc trường xác nhận chính sách lịch sử địa phương, rồi tạo tài khoản Quản trị trường với mật khẩu ngẫu nhiên cấp một lần."*

---

## 3. Màn 2 — School Admin Đổi Mật Khẩu Lần Đầu, Tạo Giáo Viên & Lớp Học (3:00 – 5:00)

- **Thao tác màn hình (`F-01`, `F-03`):**
  1. Đăng nhập bằng `admin.hd26@hoidong26.edu.vn` + mật khẩu tạm vừa copy.
  2. **Điểm nhấn bảo mật:** Modal **`Đổi mật khẩu lần đầu`** lập tức chặn toàn bộ giao diện (bấm `Esc` hay click ra ngoài đều không đóng được). Nhập mật khẩu mới `Demo@2026` → Xác nhận → hệ thống tự re-login vào `/school`.
  3. Vào **Giáo viên** (`/school/teachers`) → bấm **Thêm giáo viên**:
     - Tạo `GV Nguyễn Thị Lan` / `gv.lan.hd26@hoidong26.edu.vn` / `0940000201` → copy nhanh username & mật khẩu tạm vào Notepad.
  4. Vào **Lớp học** (`/school/classes`) → bấm **Tạo lớp mới**:
     - Tạo lớp **`10A1`** (Khối `10`, Năm học `2026–2027`, chọn GVCN `GV Nguyễn Thị Lan`).
     - Tạo thêm lớp **`10A2`** (Khối `10`, chưa cần HS) để lát minh họa chuyển lớp hoặc ngoại lệ `DS-01b`.
     - Chỉ rõ **Smart Class Code** tự sinh không dấu tiếng Việt và danh sách trạng thái lớp chỉ còn `Đang hoạt động` / `Đã lưu trữ`.
- **Lời thoại gợi ý:**
  > *"Khi School Admin đăng nhập lần đầu, hệ thống cưỡng chế đổi mật khẩu trước khi truy cập mọi API nghiệp vụ. Tiếp theo, School Admin tạo tài khoản giáo viên với chuẩn username theo mã trường và tạo lớp học tự sinh mã lớp thông minh."*

---

## 4. Màn 3 — Bulk Import CSV `DS-01a` & `DS-01b` (5:00 – 7:30)

### 4.1. `DS-01a` — Import thành công 30 học sinh vào Lớp `10A1`
1. Mở chi tiết lớp **`10A1`** → bấm **Nhập CSV**.
2. Chọn file [`DS01a_Demo_30_HocSinh_10A1.csv`](./csv_demo/DS01a_Demo_30_HocSinh_10A1.csv).
3. Cho hội đồng xem **Preview Grid (`Hợp lệ: 30 / Có lỗi: 0`)** → bấm **Nhập 30 học sinh vào lớp**.
4. **Chốt số liệu ngay trước hội đồng:**
   - Tạo xong 30 tài khoản (`{school_code}_hs_hd001` .. `hd030`) chỉ trong vài giây, có bảng tài khoản & nút xuất CSV cho lớp.
   - Quay nhanh sang **Tổng quan trường (`/school`)** hoặc chỉ ngay trên dòng kết quả: **Quỹ token chung vẫn giữ nguyên `1.016.750.000` (trừ trước = `0`)**, chứng minh đúng kiến trúc Pure Shared Pool Pay-As-You-Go.

### 4.2. `DS-01b` — Xử lý lỗi từng dòng (Inline Edit) & Sai định dạng cột (`MSG27`)
1. Mở chi tiết lớp **`10A2`** → bấm **Nhập CSV** → chọn [`DS01b_Demo_LoiDong_Va_TrungMa.csv`](./csv_demo/DS01b_Demo_LoiDong_Va_TrungMa.csv).
2. Chỉ ngay trên **Preview Grid (`Hợp lệ: 4 / Có lỗi: 3`)**:
   - Dòng `3` (`HDB02`) & dòng `5` (`HDB04`) báo đỏ **thiếu họ và tên**.
   - Dòng `7` (`HDB01`) báo đỏ **trùng mã với dòng 2**.
3. **Demo Inline Edit:** Click sửa trực tiếp tên dòng `3` (`HDB02`) thành `Trần Minh Tuấn` → số hợp lệ nhảy từ `4` lên **`5`**, lỗi còn **`2`**.
4. Bấm **Nhập 5 học sinh vào lớp** → lớp `10A2` nhận đúng 5 học sinh hợp lệ, 2 dòng lỗi bị bỏ qua an toàn. Đóng modal bằng dấu **`X`** góc trên phải.
5. Vào **Học sinh (`/school/students`)** → bấm **Import danh sách (CSV)** → chọn [`DS01b_Demo_ThieuCot_MSG27.csv`](./csv_demo/DS01b_Demo_ThieuCot_MSG27.csv).
6. Chỉ rõ **banner đỏ từ chối file (`MSG27`)**: *"File không đúng định dạng danh sách học sinh — Thiếu cột bắt buộc: Họ và tên, Ngày sinh"*, nút nhập bị khóa hoàn toàn, không tạo bất kỳ bản ghi rác nào. Đóng modal.

- **Lời thoại gợi ý:**
  > *"Với kịch bản ngoại lệ DS-01b, bộ lọc trước khi gửi kiểm tra toàn bộ từng ô: phát hiện cả thiếu trường bắt buộc lẫn trùng mã học sinh trong file, cho phép sửa trực tiếp ngay trên bảng xem trước hoặc bỏ qua dòng lỗi. Nếu người dùng chọn nhầm file thiếu cột bắt buộc, hệ thống chặn ngay từ cửa với thông báo chuẩn MSG27."*

---

## 5. Màn 4 — Xác Nhận Học Sinh & Quản Trị Vòng Đời (7:30 – 9:30)

1. *(Nếu muốn nhấn mạnh chuyển lớp)*: Vào lại `10A1`, tích `HD030` → **Chuyển lớp** sang `10A2` (`10A1: 29`, `10A2: 6`) → minh họa luồng `ACTIVE -> TRANSFERRED` (mỗi HS chỉ ở 1 lớp đang học).
2. **Đăng xuất** khỏi School Admin.
3. Đăng nhập bằng học sinh đầu tiên **`hd001@hoidong26.edu.vn` / `HistoryTalk@2026`**:
   - Bị ép **đổi mật khẩu lần đầu** → đổi sang `Demo@2026`.
   - Vào **`/classes`**: học sinh nhìn thấy ngay **`Lớp 10A1`**, giáo viên chủ nhiệm **`GV Nguyễn Thị Lan`**, danh sách bạn cùng lớp và hạn mức **`0 / 5.000 token`** ở thanh bên.
   - *(Mở rộng nếu còn thời gian)*: Gõ thử URL `/school` hoặc `/staff/admin` trên thanh địa chỉ → bị chặn và điều hướng ngược về `/classes` (chứng minh RBAC).
4. **Dự phòng nếu Hội đồng hỏi thêm:**
   - **Kết thúc năm học (Rollover):** Vào lớp `12A1` import [`DS01_Demo_Khoi12_Rollover.csv`](./csv_demo/DS01_Demo_Khoi12_Rollover.csv) -> tại `/school/classes` bấm `Kết thúc năm học…`, chọn năm `2026–2027`, tick khóa HS khối 12 → toàn bộ lớp chuyển `ARCHIVED` và chỉ các HS khối 12 bị `DEACTIVATED`.
   - **Lịch sử PayOS / Boost Token:** Chỉ thực hiện nếu BE mới đã deploy xong (tránh lỗi cũ ở `/staff/admin/payment/history`).

---

## 6. Vùng Cần Tránh Khi Trình Chiếu (Nếu FE Chưa Kịp Deploy Bản Vá)

| Hành động dễ lộ lỗi UI | Cách đi đường vòng an toàn khi Demo |
|---|---|
| Bấm nút **`Năm`** trên Dashboard (nếu BE mới chưa lên EC2) | Chỉ dùng `Ngày`, `Tuần`, `Tháng`. |
| Mở `/staff/admin/payment/history` nếu DB còn đơn `SCHOOL_TOKEN_TOPUP` cũ & BE mới chưa deploy | Chỉ mở sau khi đã verify BE mới lên server (`200 OK`). |
| Vào `/school/tokens`, đổi hạn mức rồi bấm `F5` ở chính trang đó | Đổi xong bấm sang `/school` (Tổng quan trường) để show số hạn mức mới đã lưu đúng. |
| Bấm **`Xong`** ở modal kết quả CSV trong lớp rồi bấm `Nhập CSV` lại | Đóng modal kết quả bằng dấu **`X`** ở góc trên phải. |
| Re-upload trùng file CSV vào cùng 1 lớp đã import | Chỉ upload mỗi file CSV 1 lần vào 1 lớp khi demo. |
| Bấm nút **`Back`** của trình duyệt ngay sau khi `Đăng xuất` khỏi Admin | Luôn dùng menu điều hướng / URL trực tiếp thay vì nút Back trình duyệt. |
| Demo `MSG27` (thiếu cột) bên trong modal của **Lớp học** | Demo file `DS01b_Demo_ThieuCot_MSG27.csv` tại màn hình **`/school/students`** để hiện banner đỏ `MSG27`. |

---

## 7. Bộ Câu Hỏi Phản Biện Hội Đồng Hay Hỏi Về WF-01 & Cách Trả Lời

1. **"Tại sao import 30–500 học sinh xong quỹ token của trường vẫn không bị trừ?"**
   - *Trả lời:* Hệ thống áp dụng kiến trúc **Pure Shared Pool Pay-As-You-Go**. Khi onboarding học sinh, trường không phải chia cứng token trước (tránh tình trạng học sinh ít chat làm kẹt/lãng phí token). Quỹ `unallocatedTokenQuota` của trường chỉ trừ khi học sinh thực sự gửi prompt chat AI (và bị chặn trần theo hạn mức ngày `dailyTokenQuota` của lớp hoặc trường).
2. **"Làm sao đảm bảo một học sinh không đứng tên ở nhiều lớp cùng lúc hoặc trùng dữ liệu giữa các trường?"**
   - *Trả lời:* Trong cùng một trường, `ClassroomImportProcessor` và `ClassroomStudentServiceImpl` kiểm tra `ClassStudent` trạng thái `ACTIVE`; nếu học sinh đang học lớp khác thì chặn và hướng dẫn dùng chức năng **Chuyển lớp** (`ACTIVE -> TRANSFERRED`). Username sinh theo chuẩn `{school_code}_hs_{student_code}` để tách biệt tenant giữa các trường, và mật khẩu khởi tạo gắn cờ `mustChangePassword = true` được chặn bởi `JwtAuthenticationFilter` trên toàn bộ API nghiệp vụ cho tới khi người dùng tự đổi mật khẩu.
3. **"File CSV tải từ Excel Việt Nam hay bị lỗi font UTF-8 BOM hoặc thiếu cột thì xử lý sao?"**
   - *Trả lời:* Phía FE đọc và chuẩn hóa bảng dữ liệu kèm xem trước cho phép sửa từng ô inline trước khi gửi, hỗ trợ template chuẩn UTF-8 có BOM; phía BE cũng đã xử lý strip UTF-8 BOM ở đầu stream khi đọc bằng Apache Commons CSV để đảm bảo cột đầu tiên `student_code` luôn được nhận diện đúng kể cả khi gọi trực tiếp API.
4. **"Cuối năm học muốn nâng cấp gói hay chuyển khóa học sinh lớp 12 thì sao?"**
   - *Trả lời:* Trường có 2 luồng tách biệt: (1) **Kết thúc năm học (`Rollover`)** để lưu trữ toàn bộ lớp niên khóa cũ (`ARCHIVED`) và tự động khóa tài khoản học sinh khối 12 đã tốt nghiệp nhưng vẫn giữ toàn bộ lịch sử log và quỹ token trường; (2) **Gia hạn & Kích hoạt năm học mới** hỗ trợ chuyển tiếp **20% quỹ token chưa sử dụng** sang gói năm học mới (`01/09 – 31/05`).
