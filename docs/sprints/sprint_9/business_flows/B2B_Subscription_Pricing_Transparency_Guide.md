# 🏛️ TÀI LIỆU MINH BẠCH TÀI CHÍNH & HƯỚNG DẪN CƠ CHẾ ĐỊNH GIÁ GÓI B2B TRƯỜNG HỌC

> **Hệ thống:** HistoryTalk — Nền tảng Giáo dục Lịch sử Thông minh bằng Trí tuệ Nhân tạo  
> **Phiên bản:** 2.0 (Cập nhật cơ chế Deep Customization theo Chu kỳ Năm học & Hạn mức linh hoạt)  
> **Đối tượng áp dụng:** Ban Giám hiệu, Phòng Kế toán & Quản trị viên Trường học (`SCHOOL_ADMIN`)  
> **Cam kết:** Minh bạch 100% — Tính đúng, tính đủ theo nhu cầu đào tạo thực tế, không chi phí ẩn.

---

## 1. Lời Mở Đầu & Cam Kết Minh Bạch Tài Chính

Nhằm đồng hành cùng các trường phổ thông (THCS, THPT, Trường Liên cấp) trên toàn quốc trong việc đẩy mạnh chuyển đổi số dạy và học môn Lịch sử theo chương trình GDPT 2018, **HistoryTalk** áp dụng mô hình định giá dựa trên **định mức kỹ thuật thực tế** (Activity-Based Pricing).

Nhà trường được toàn quyền tùy biến quy mô học sinh, số lượng giáo viên, chu kỳ tuần học, số ngày học/tuần và hạn mức token hàng ngày. Mọi chi phí đều được bóc tách rõ ràng theo 2 thành phần: **Chi phí Trí tuệ Nhân tạo (AI Token Pool)** và **Chi phí Hạ tầng Nền tảng (Platform & LMS Fee)**.

---

## 2. Cơ Cấu 2 Thành Phần Chi Phí Cấu Thành Gói B2B

Mỗi gói tài khoản trường học tại HistoryTalk được cấu thành từ đúng 2 thành phần chi phí độc lập:

### 2.1. Thành phần 1: Chi phí Xử lý AI Trí tuệ Nhân tạo (`AI Token Pool`)
* **Đơn giá niêm yết cố định:** **`0,15 đ / 1 token`** (áp dụng đồng nhất cho cả B2C và B2B).
* **Quỹ Token Chung của Toàn Trường (`Shared Token Pool`):** Toàn bộ token của học sinh và giáo viên được quy đổi và gộp chung vào một quỹ của nhà trường.
  * *Lợi ích:* Tránh tình trạng lãng phí token nếu có lớp nghỉ học, học sinh nghỉ ốm hoặc tuần lễ thi cử; các lớp khác có nhu cầu học tăng cường có thể tận dụng mà không phát sinh thêm chi phí.

### 2.2. Thành phần 2: Chi phí Hạ tầng Nền tảng, Lưu trữ & Tính năng Giáo dục (`Platform Fee`)
* **Dành cho Học sinh (`STUDENT`):** Chuẩn 35 tuần học là **`7.000 đ / HS / năm học`**.
  * Bao gồm: Phân chia cơ sở dữ liệu theo lớp học, lưu trữ toàn bộ lịch sử vấn đáp với nhân vật lịch sử, quyền mở khóa không giới hạn **Chế độ Thi Vấn Đáp (`Exam Mode`)**, làm bài trắc nghiệm nhanh (`Quiz`), bảng xếp hạng học tập.
  * Khi chu kỳ năm học $W$ thay đổi, chi phí này được tính theo tỷ lệ: $\text{round}\left(7.000 \times \frac{W}{35}\right)$.
* **Dành cho Giáo viên (`TEACHER`):** Chuẩn 35 tuần học là **`42.500 đ / GV / năm học`**.
  * Bao gồm: Cổng quản trị sư phạm (LMS), tạo đề thi và ma trận câu hỏi tự động bằng AI, phân lớp, **Kho nội dung Lịch sử Địa phương riêng của Trường** (upload tài liệu PDF, video bài giảng, nhân vật danh nhân địa phương lên Cloud & Vector Database).
  * Khi chu kỳ năm học $W$ thay đổi, chi phí này được tính theo tỷ lệ: $\text{round}\left(42.500 \times \frac{W}{35}\right)$.

---

## 3. Bảng Định Mức Kỹ Thuật Tiêu Chuẩn (Chu kỳ 35 tuần Bộ GD&ĐT)

| Hạng mục | Học sinh (`STUDENT`) | Giáo viên (`TEACHER`) | Ghi chú & Ý nghĩa sư phạm |
| :--- | :---: | :---: | :--- |
| **Hạn mức Token mỗi ngày** | **`5.000 tokens / ngày`** | **`5.000 tokens / ngày`** | Đủ cho 12–15 lượt hỏi đáp chuyên sâu hoặc 1 bài thi vấn đáp hoàn chỉnh |
| **Tần suất hoạt động chuẩn** | **`4 ngày / tuần`** | **`6 ngày / tuần`** | Phù hợp thời khóa biểu học tập trên lớp và tự học tại nhà |
| **Số tuần năm học chuẩn** | **`35 tuần`** | **`35 tuần`** | Theo khung kế hoạch thời gian năm học của Bộ GD&ĐT |
| **Tổng Token / Tài khoản / Năm** | **`700.000 tokens`** | **`1.050.000 tokens`** | $\text{Hạn mức ngày} \times \text{Ngày/tuần} \times 35\text{ tuần}$ |
| **(1) Tiền Token AI (`× 0,15 đ`)** | **`105.000 đ / năm`** | **`157.500 đ / năm`** | Chi phí điện toán đám mây AI thực tế |
| **(2) Phí Nền tảng, Lưu trữ, LMS** | **`7.000 đ / năm`** | **`42.500 đ / năm`** | Hạ tầng Database, Cloud Storage & Phần mềm giáo dục |
| **Tổng Chi phí Gốc Chưa Giảm** | **`112.000 đ / HS / năm`** | **`200.000 đ / GV / năm`** | Tổng chi phí tiêu chuẩn trước khi áp dụng chiết khấu quy mô |

---

## 4. Công Thức Tính Toán Chi Phí Tùy Biến Chuyên Sâu (`Deep Customization`)

Khi nhà trường đăng ký gói Tùy chỉnh (`CUSTOM`) với các tham số riêng:
* $S$: Tổng số lượng học sinh
* $T$: Tổng số lượng giáo viên
* $W$: Chu kỳ năm học (Số tuần, từ 1 đến 60 tuần; mặc định 35)
* $D_S, N_S$: Hạn mức ngày và số ngày học/tuần của Học sinh (mặc định 5.000 token, 4 ngày/tuần)
* $D_T, N_T$: Hạn mức ngày và số ngày dùng/tuần của Giáo viên (mặc định 5.000 token, 6 ngày/tuần)

### Quy trình tính toán 6 bước:

$$\begin{aligned}
\text{Token}_{\text{student/year}} &= W \times D_S \times N_S \\
\text{Token}_{\text{teacher/year}} &= W \times D_T \times N_T \\
\text{TotalTokenPool} &= (S \times \text{Token}_{\text{student/year}}) + (T \times \text{Token}_{\text{teacher/year}})
\end{aligned}$$

$$\begin{aligned}
\text{Chi phí Token AI} &= \text{TotalTokenPool} \times 0,15\text{ đ}
\end{aligned}$$

$$\begin{aligned}
\text{Phí Nền tảng HS (quy đổi)} &= \text{round}\left(7.000 \times \frac{W}{35}\right) \\
\text{Phí Nền tảng GV (quy đổi)} &= \text{round}\left(42.500 \times \frac{W}{35}\right) \\
\text{Tổng Phí Nền tảng} &= (S \times \text{Phí Nền tảng HS}) + (T \times \text{Phí Nền tảng GV})
\end{aligned}$$

$$\begin{aligned}
\text{Tạm tính (Subtotal)} &= \text{Chi phí Token AI} + \text{Tổng Phí Nền tảng}
\end{aligned}$$

$$\begin{aligned}
\text{Tỷ lệ Chiết khấu} &= 
\begin{cases} 
0\%, & S < 600 \\
10\%, & 600 \le S < 1.200 \quad (\text{Gói Small}) \\
20\%, & 1.200 \le S < 1.800 \quad (\text{Gói Medium}) \\
30\%, & S \ge 1.800 \quad (\text{Gói Large})
\end{cases}
+ 5\%\text{ (nếu áp dụng mã đối tác)}
\end{aligned}$$

$$\begin{aligned}
\text{Giá Hợp Đồng Cuối Cùng} &= \text{Tạm tính} \times (100\% - \text{Tỷ lệ Chiết khấu})
\end{aligned}$$

---

## 5. Bảng Tính Minh Họa Các Kịch Bản Thực Tế

### 5.1. Ba Gói Tiêu Chuẩn (Chu kỳ chuẩn 35 tuần)

| Chỉ số | Gói Small (Trường nhỏ / THCS) | Gói Medium (Trường tiêu chuẩn) | Gói Large (Trường lớn / Đô thị) |
| :--- | :---: | :---: | :---: |
| **Quy mô** | **600 Học sinh & 15 Giáo viên** | **1.400 Học sinh & 35 Giáo viên** | **2.000 Học sinh & 50 Giáo viên** |
| **Chu kỳ năm học** | 35 tuần | 35 tuần | 35 tuần |
| **Tổng Quỹ Token Toàn Trường** | **435.750.000 tokens** | **1.016.750.000 tokens** | **1.452.500.000 tokens** |
| **(1) Tiền Token AI (0,15 đ)** | 65.362.500 đ | 152.512.500 đ | 217.875.000 đ |
| **(2) Phí Nền tảng & LMS** | 4.837.500 đ | 11.287.500 đ | 16.125.000 đ |
| **Tạm tính (Subtotal)** | **70.200.000 đ** | **163.800.000 đ** | **234.000.000 đ** |
| **Mức Chiết khấu Giáo dục** | **Giảm 10%** (`-7.020.000 đ`) | **Giảm 20%** (`-32.760.000 đ`) | **Giảm 30%** (`-70.200.000 đ`) |
| **GIÁ TRỌN GÓI HỢP ĐỒNG** | **63.180.000 đ / năm học** | **131.040.000 đ / năm học** | **163.800.000 đ / năm học** |
| **Chi phí bình quân 1 HS / tháng** | **~11.700 đ / HS / tháng** | **~10.400 đ / HS / tháng** | **~9.100 đ / HS / tháng** |

---

### 5.2. Các Kịch Bản Tùy Biến Chuyên Sâu

#### Kịch bản 1: Trường THPT Chuyên Mở Rộng Năm Học (40 Tuần)
* **Quy mô:** 1.200 Học sinh, 30 Giáo viên.
* **Chu kỳ:** 40 tuần (kéo dài thêm 5 tuần ôn tập & bồi dưỡng học sinh giỏi).
* **Định mức:** HS 5.000 token/ngày (4 ngày/tuần), GV 6.000 token/ngày (6 ngày/tuần).
* **Tính toán:**
  * Token HS/năm: $40 \times 5.000 \times 4 = 800.000$ tokens $\rightarrow$ $1.200 \times 800.000 = 960.000.000$ tokens.
  * Token GV/năm: $40 \times 6.000 \times 6 = 1.440.000$ tokens $\rightarrow$ $30 \times 1.440.000 = 43.200.000$ tokens.
  * Quỹ Token: $1.003.200.000$ tokens $\rightarrow$ Tiền Token AI: $1.003.200.000 \times 0,15 = 150.480.000$ đ.
  * Phí HS (40 tuần): $7.000 \times 40 / 35 = 8.000$ đ $\rightarrow 1.200 \times 8.000 = 9.600.000$ đ.
  * Phí GV (40 tuần): $42.500 \times 40 / 35 = 48.571$ đ $\rightarrow 30 \times 48.571 = 1.457.130$ đ.
  * Tạm tính: $150.480.000 + 11.057.130 = 161.537.130$ đ.
  * Chiết khấu quy mô (1.200 HS): Giảm 20% ($-32.307.426$ đ).
  * **Giá hợp đồng trọn gói:** **`129.229.704 đ`** (Bình quân chỉ ~10.700 đ / HS / tháng).

#### Kịch bản 2: Trường THCS Tiết Kiệm Ngân Sách (Hạn mức 3.000 token/ngày)
* **Quy mô:** 500 Học sinh, 15 Giáo viên.
* **Chu kỳ:** 35 tuần chuẩn.
* **Định mức:** HS 3.000 token/ngày (3 ngày/tuần), GV 4.000 token/ngày (5 ngày/tuần).
* **Tính toán:**
  * Token HS/năm: $35 \times 3.000 \times 3 = 315.000$ tokens $\rightarrow 500 \times 315.000 = 157.500.000$ tokens.
  * Token GV/năm: $35 \times 4.000 \times 5 = 700.000$ tokens $\rightarrow 15 \times 700.000 = 10.500.000$ tokens.
  * Quỹ Token: $168.000.000$ tokens $\rightarrow$ Tiền Token AI: $168.000.000 \times 0,15 = 25.200.000$ đ.
  * Phí Nền tảng: $(500 \times 7.000) + (15 \times 42.500) = 3.500.000 + 637.500 = 4.137.500$ đ.
  * Tạm tính: $29.337.500$ đ.
  * Chiết khấu ($<600$ HS): 0%.
  * **Giá hợp đồng trọn gói:** **`29.337.500 đ`** (Bình quân chỉ ~6.500 đ / HS / tháng — rất tiết kiệm cho trường công lập).

---

## 6. Chính Sách Quản Trị & Bảo Vệ Ngân Sách Nhà Trường

1. **Bảo toàn Quỹ Token (No Token Waste):**
   - Quỹ token trường học không bị mất đi mỗi tháng mà được duy trì trong suốt toàn bộ chu kỳ hợp đồng năm học.
2. **Kiểm soát lạm dụng (Abuse Prevention):**
   - Hạn mức hàng ngày ngăn học sinh sử dụng quá đà, đảm bảo tài nguyên AI phục vụ đúng mục đích nghiên cứu và học tập lịch sử.
3. **Cổng Giám sát Thời gian Thực (`Analytics Dashboard`):**
   - School Admin và Ban Giám hiệu có thể theo dõi biểu đồ tiêu thụ token theo ngày, theo khối, theo lớp và theo từng học sinh.
4. **Hồ sơ Pháp lý & Hóa đơn Hợp lệ:**
   - Cung cấp đầy đủ Hợp đồng cung cấp dịch vụ phần mềm giáo dục, Biên bản nghiệm thu và Hóa đơn điện tử GTGT (VAT) hợp lệ phục vụ công tác thanh quyết toán kho bạc nhà nước.
5. **Cơ chế Nạp Thêm Token Tự Động Trực Tuyến Giữa Năm Học (Self-Service Top-up qua PayOS):**
   - Khi nhà trường có nhu cầu đột xuất (mở đợt ôn thi học kỳ tập trung, thi học sinh giỏi) hoặc dùng hết quỹ token trước khi hết năm học:
   - Quản trị viên Trường (`SCHOOL_ADMIN`) chỉ cần vào mục **Mua thêm Token**, chọn số lượng mong muốn và thanh toán trực tiếp qua cổng **PayOS (VietQR)** theo đúng đơn giá gốc **`0,15 đ / token`**.
   - **Tự động 100%, không cần chờ duyệt hay làm lại hợp đồng:** Ngay khi quét mã QR chuyển tiền thành công, hệ thống Webhook tự động cộng ngay lập tức token vào kho chung của trường, mở khóa cho giáo viên và học sinh tiếp tục sử dụng tức thì.

---

## 7. Tài Liệu Đính Kèm

- **File Word Báo giá & Thuyết minh tài chính:** `docs/sprints/sprint_9/business_flows/B2B_Subscription_Pricing_Transparency_Guide.docx`
- **Tài liệu API Contract dành cho Kỹ thuật & Tích hợp:** `docs/services/history-talk-backend/b2b-custom-plan-and-quote-flow-api-contract.md`
