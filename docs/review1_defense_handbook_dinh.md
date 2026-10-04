# 🎯 CẨM NĂNG BẢO VỆ REVIEW 1 — REPORT 2 (PMP)
> **Dành riêng cho:** Trần Quốc Dinh (DinhTQ) — Backend Developer & DevOps Engineer  
> **Dự án:** HistoryTalk (SaaS Historical Education Platform)  
> **Ngày bảo vệ:** Review 1 (Tuần 4)

---

## 📌 CHƯƠNG 1: KỊCH BẢN THUYẾT TRÌNH PHẦN REPORT 2 (PMP)

### 🎙️ Lời mở đầu (15-20 giây):
> *"Kính thưa Thầy/Cô trong Hội đồng, em tên là Trần Quốc Dinh. Sau đây em xin đại diện nhóm trình bày **Report 2 — Kế hoạch Quản lý Dự án (Project Management Plan)** của HistoryTalk. Báo cáo này thiết lập toàn bộ quy trình phân bổ nguồn lực, quản lý rủi ro, phân công trách nhiệm RACI và hạ tầng CI/CD tự động trong suốt 15 tuần phát triển."*

---

### 📋 6 Trụ cột nội dung cần nêu bật trong Slide:

#### 1️⃣ Scope & WBS Estimation (Phạm vi & Ước tính Ngày công — Bảng 2)
* **Tổng ngân sách ngày công:** **150 man-days** (cho 15 tuần, 5 thành viên, trung bình **30 man-days/người**, quy đổi 2 ngày công/tuần/người).
* **Phân bổ 4 Module chính:**
  1. **Module Student (Learner): 35 man-days** — Auth/OAuth2 (4d), Onboarding (5d), AI Roleplay Chat SSE (10d), Bản đồ Lịch sử Spatio-Temporal (5d), Timed Quiz Engine (6d), Portal Bài tập (5d).
  2. **Module Educator & Classroom Management: 40 man-days** — Tạo lớp (5d), Quản lý danh sách học sinh (6d), Giao bài tập đa nhiệm Context+Chat+Quiz (10d), Tạo Bối cảnh/Quiz riêng (8d), Sổ điểm & Engagement Analytics (11d).
  3. **Module System & Content Admin: 40 man-days** — Quản lý Bối cảnh/Nhân vật/Quiz chung (10d), Document OCR Pipeline PDFBox & Tess4J (11d), Tích hợp Thanh toán PayOS & Nạp Token (10d), Dashboard hệ thống JVM & Analytics (9d).
  4. **Testing & DevOps Infrastructure: 35 man-days** — Test Case Specification & System Integration (15d), CI/CD Pipeline Automation, Docker Compose & AWS EC2 Deploy (20d).

#### 2️⃣ Project Objectives & Quality Metrics (Mục tiêu & Chất lượng — Bảng 3)
* **Quy trình Agile/Scrum:** Áp dụng mô hình phát triển **Agile/Scrum** với chu kỳ **Sprint 2 tuần** kéo dài suốt 15 tuần phát triển. Các chu kỳ Sprint ngắn cho phép nhóm liên tục phản hồi, tinh chỉnh yêu cầu và bàn giao các tính năng cuốn chiếu qua từng mốc Review của bộ môn (Review 1: Khảo sát, DB, Auth; Review 2: Core AI Chat, RAG, Class APIs, Quiz; Final Defense: PayOS, Analytics, OCR, AWS Deploy).
* **Tỷ lệ bao phủ kiểm thử (Test Coverage):** Peer Review 100% PRs, Unit Test $\ge 80\%$, Integration Test $\ge 85\%$, System Test 100% luồng.
* **Chiến lược phân bổ lỗi (Shift Left % of Defect):** Tập trung bắt **60% số lỗi ngay ở giai đoạn đầu** (30% Reviewing + 30% Unit Test) để tiết kiệm chi phí sửa lỗi, 40% lỗi còn lại thuộc Integration Test (20%) và System Test (20%).
* **Chỉ số UAT Defect Rate:** Đạt **0 lỗi Critical/Blocker** và dưới **5 lỗi Minor** trong đợt nghiệm thu cuối.

#### 3️⃣ Risk Management (Quản lý Rủi ro — 4 Rủi ro cốt lõi — Bảng 4)
* **Rủi ro 1 (AI Hallucination - High/Medium):** AI bịa đặt sự kiện lịch sử $\rightarrow$ *Giải pháp:* Hai lớp RAG (Dense retrieval + Kaggle Reranker) & System Prompt ngặt nghèo.
* **Rủi ro 2 (Student Chat Spamming - Medium/High):** Học sinh spam tin nhắn rác để lấy thành tích $\rightarrow$ *Giải pháp:* Lọc độ dài tối thiểu ($\ge 10$ chars), **trừ Token người dùng theo tin nhắn/độ dài**, và chốt điểm số bằng bài kiểm tra Quiz bấm giờ (*anchor final grades to timed Quiz*).
* **Rủi ro 3 (PayOS Webhook Failure - High/Low):** Mất kết nối webhook khi thanh toán $\rightarrow$ *Giải pháp:* Chạy Scheduler đối soát ngầm tự động (`PaymentFulfillmentReconciliationScheduler`).
* **Rủi ro 4 (OCR Processing Failures - Medium/Low):** PDF scan sách cũ mờ không trích xuất được chữ $\rightarrow$ *Giải pháp:* Dùng **Apache PDFBox** trích xuất text trực tiếp cho PDF điện tử; dùng **Tess4J** tiền xử lý tăng độ nét lên **300 DPI** cho PDF scan.

#### 4️⃣ Responsibility Assignments (Ma trận RACI — Bảng 7)
* Phân công ngặt nghèo cho 5 thành viên theo nguyên tắc mỗi việc chỉ có **1 người chịu trách nhiệm chính (A - Accountable)**:
  * **Trí (TriNM):** Leader, Phụ trách cốt lõi AI RAG Pipeline, Streaming SSE Chat & Prompting (D, R).
  * **Định (DinhTQ):** Phụ trách SaaS B2B Class & User APIs, Progress & System Analytics Dashboards, và DevOps CI/CD Automation trên AWS EC2 (D, R).
  * **Khải (KhaiVDD):** Phụ trách Auth, Google OAuth2, Multi-tenant DB, Global Content CRUD & PDF/OCR Pipeline (D, R).
  * **Thành (ThanhNC):** Phụ trách toàn bộ Frontend Web/App UI (NextJS/Tailwind) và Trưởng nhóm Kiểm thử/Test Cases (D, R).
  * **Bảo (BaoDQ):** Phụ trách Quiz Engine API, Cổng thanh toán PayOS và Bản đồ tương tác Interactive Map (D, R).

#### 5️⃣ Project Communications (Quy trình Giao tiếp — Bảng 8)
* **Họp GVHD:** Thứ 6 hàng tuần (Weekly) qua Google Meet / Offline.
* **Họp nhóm nội bộ:** Thứ 7 hàng tuần (Weekly) qua Google Meet.
* **Trao đổi hàng ngày:** Nhóm chat Zalo & GitHub PR review.

#### 6️⃣ Configuration Management & DevOps CI/CD (Quản lý Cấu hình — Mục 6 & Bảng 9)
* **Source Code Control:** Monorepo quy trình **Git Flow** (`main` cho Production, `deployment_v5` cho Staging, `feature/*` cho tính năng mới). Commit theo chuẩn *Conventional Commits*.
* **Tự động hóa CI/CD Automation:**
  * Push code lên branch `main` $\rightarrow$ **GitHub Actions** tự động chạy pipeline build Java Backend & Python AI Backend.
  * Đóng gói Docker Images đẩy lên **GitHub Container Registry (`ghcr.io`)**.
  * Tự động SSH vào máy chủ **AWS EC2 (`c6a.large`)** $\rightarrow$ `git pull` $\rightarrow$ Re-deploy tự động bằng **Docker Compose** và cấp SSL/TLS tự động qua **Caddy Reverse Proxy**.

---

## 👤 CHƯƠNG 2: CHI TIẾT NHIỆM VỤ & CÔNG NGHỆ CỦA TRẦN QUỐC ĐỊNH (DINHTQ)

### 💻 1. Các phân hệ Backend APIs trực tiếp phát triển:
1. **Phân hệ SaaS B2B Lớp học & Người dùng (B2B Class & User Management APIs - WBS 2.2 / 2.3):**
   * Tham gia cùng Khải xây dựng REST API Quản lý Lớp học (`Classroom Management`) và Mã gia nhập lớp (*Class Code*).
   * Quản lý danh sách học sinh (*Class Roster*) trong lớp.
2. **Hệ thống Theo dõi Tiến độ Học tập (Progress Tracking & Engagement Analytics - WBS 2.6):**
   * *Chịu trách nhiệm chính (D, R)*: Xây dựng API & Service tính toán trạng thái bài tập (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `LATE_SUBMITTED`).
   * Tổng hợp chỉ số tương tác bài học (*Engagement Analytics*) xuất báo cáo cho Giáo viên.
3. **Hệ thống Dashboard Quản trị & Giám sát Hệ thống (System Tracking & Analytics - WBS 3.4):**
   * *Chịu trách nhiệm chính (D, R)*: Xây dựng API Dashboard cho Admin hệ thống:
     * **JVM Health & Server Tracking:** Giám sát CPU, RAM, Thread Dump Java Spring Boot.
     * **Token Flow Analytics:** Báo cáo tổng lượng Token AI tiêu thụ theo ngày/tháng.
     * **Top Wrong Quiz Analytics:** Thống kê các câu hỏi Quiz học sinh làm sai nhiều nhất.

### 🚀 2. Hạ tầng & Tự động hóa DevOps CI/CD:
1. **Máy chủ AWS EC2:**
   * AWS EC2 `c6a.large` (2 vCPU / 4 GB RAM / 30 GB SSD) tại Singapore (`ap-southeast-1`).
   * Cấu hình **4 GB Swap File** tại `/swapfile` (`vm.swappiness=10`) giúp chống lỗi tràn bộ nhớ OOM.
   * Firewall Security Group: Mở Port 22 (SSH), Port 80 (HTTP), Port 443 (HTTPS); **Khóa tuyệt đối** port nội bộ `8080` (Java) và `8001` (AI).
2. **GitHub Actions CI/CD Pipeline (`.github/workflows/deploy.yml`):**
   * Merge code `main` $\rightarrow$ GitHub Actions tự động build Java 21 & Python AI Docker Images.
   * Push images lên **GitHub Container Registry (`ghcr.io`)**.
   * SSH tự động vào EC2 qua `appleboy/ssh-action` $\rightarrow$ `git pull origin main` $\rightarrow$ `docker compose pull` $\rightarrow$ `docker compose up -d --force-recreate` $\rightarrow$ `docker image prune -f`.
3. **Caddy Reverse Proxy & SSL HTTPS:**
   * Container `historytalk-caddy` (`caddy:2-alpine`) tự động cấp & gia hạn SSL/TLS Let's Encrypt cho `historytalk.app`.
   * Điều hướng: `/Historical-tell/*` $\rightarrow$ `historytalk-java:8080`, `/v1/*` & `/health` $\rightarrow$ `historytalk-ai:8001`.

---

## ❓ CHƯƠNG 3: BỘ CÂU HỎI Q&A "HỎI XOÁY ĐÁP XOAY" TỪ HỎI ĐỒNG

### ❓ Câu 1: *"Con số 150 man-days tính từ đâu ra? Tại sao 15 tuần 5 người lại ra 150?"*
* 💡 **Trả lời:**  
  *"Dạ thưa Thầy/Cô, dự án kéo dài 15 tuần với 5 thành viên. Mỗi thành viên dành trung bình 2 ngày làm việc tập trung mỗi tuần cho Capstone (tương đương 30 man-days/người trong cả kỳ). Tổng công sức 5 người $\times 30$ man-days = **150 man-days**. Con số này được chia đều và cân bằng (~30 man-days/người) cho 4 Module công việc trong bảng WBS ạ."*

### ❓ Câu 2: *"Tại sao lại có cột '% of Defect' (30%, 30%, 20%, 20%) trong Bảng 3?"*
* 💡 **Trả lời:**  
  *"Dạ thưa Thầy/Cô, đây là chỉ số phân bổ tỷ lệ phần trăm lỗi dự kiến phát hiện ở từng giai đoạn. Nhóm áp dụng nguyên tắc **Shift Left (Phát hiện lỗi sớm)**: đặt mục tiêu **bắt và xử lý 60% số lỗi ngay ở 2 bước đầu (Reviewing tài liệu/PR 30% + Unit Test 30%)** để tiết kiệm tối đa chi phí sửa lỗi, 40% lỗi còn lại thuộc về Integration Test (20%) và System Test (20%) trước khi ra bản chính thức ạ."*

### ❓ Câu 3: *"Học sinh spam tin nhắn rác khi chat với AI thì nhóm xử lý thế nào?"*
* 💡 **Trả lời:**  
  *"Dạ thưa Thầy/Cô, nhóm áp dụng 3 lớp bảo vệ:  
  1. Bộ lọc độ dài tin nhắn tối thiểu ($\ge 10$ ký tự).  
  2. **Trừ Token tài khoản người dùng** theo từng tin nhắn/độ dài câu thoại để tránh spam vô hạn.  
  3. Chat AI chỉ là điều kiện trải nghiệm học tập, **điểm số chính thức của bài tập được chốt (anchor) dựa trên kết quả bài Quiz bấm giờ** ạ."*

### ❓ Câu 4: *"Định ơi, em làm những phần nào trong dự án này?"*
* 💡 **Trả lời:**  
  *"Dạ thưa Thầy/Cô, trong dự án HistoryTalk em đảm nhận 2 vai trò chính là **Backend Developer & DevOps Engineer**:
  1. **Về mảng Backend:** Em phụ trách phát triển các REST API về Quản lý Lớp học (Classroom Management), hệ thống Báo cáo tiến độ học tập của học sinh (Progress Tracking Analytics), và Dashboard Quản trị hệ thống (System Tracking về JVM Health, Token Flow và Top câu hỏi Quiz sai nhiều nhất).
  2. **Về mảng DevOps:** Em chịu trách nhiệm dựng máy chủ AWS EC2, tối ưu bộ nhớ Swap, cấu hình Caddy SSL HTTPS tự động và xây dựng quy trình CI/CD tự động hoàn toàn qua GitHub Actions và Docker Compose ạ."*

### ❓ Câu 5: *"Em giải thích luồng CI/CD tự động khi push code hoạt động như thế nào?"*
* 💡 **Trả lời:**  
  *"Dạ thưa Thầy/Cô, quy trình CI/CD của nhóm em được tự động hóa qua GitHub Actions với file workflow `deploy.yml`:  
  * Khi code được merge vào branch `main`, GitHub Actions sẽ tự động kích hoạt Job Build để đóng gói Java 21 và Python AI thành Docker Images và push lên GitHub Container Registry (`ghcr.io`).  
  * Tiếp theo, Job Deploy sẽ dùng SSH Key kết nối an toàn vào máy chủ AWS EC2, thực hiện kéo code mới, pull Docker Images mới về và chạy lệnh `docker compose up -d --force-recreate` để cập nhật ứng dụng mà không cần thao tác thủ công trên server ạ."*

---

## 🌟 CHƯƠNG 4: BÍ QUYẾT BẢO VỆ THÀNH CÔNG RỰC RỠ

1. **Thần thái:** Bình tĩnh, tự tin, mắt nhìn thẳng vào Hội đồng, phát âm rõ ràng.
2. **Xưng hô chuẩn:** *"Dạ thưa Thầy/Cô..."* ở đầu mỗi câu trả lời.
3. **Làm chủ phần việc:** Nhấn mạnh vào chuyên môn Backend APIs (Progress Analytics, System Dashboard) và DevOps CI/CD (GitHub Actions, Docker Compose, Caddy SSL) mà bạn đã làm thành công.

*Chúc Định và toàn nhóm HistoryTalk bảo vệ Review 1 đạt điểm số cao nhất ngày mai! 🚀*
