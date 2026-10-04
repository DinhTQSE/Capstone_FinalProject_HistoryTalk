# 🎙️ KỊCH BẢN THUYẾT TRÌNH CHI TIẾT — REPORT 2: PROJECT MANAGEMENT PLAN (PMP)
> **Người trình bày:** Trần Quốc Dinh (DinhTQ) — Representative, Backend Developer & DevOps Engineer  
> **Dự án:** HistoryTalk — Nền tảng Giáo dục Lịch sử Tương tác AI (SaaS)  
> **Thời lượng dự kiến:** 5 - 7 phút  

---

## 🕒 PHẦN 1: LỜI MỞ ĐẦU & TỔNG QUAN REPORT 2 (30 giây)

### 🖥️ Slide 1: Cover Report 2 — Project Management Plan (PMP)
* **Kịch bản nói (Lời Định):**
> *"Kính thưa Thầy/Cô trong Hội đồng bảo vệ và toàn thể các bạn,*
>
> *Em tên là **Trần Quốc Dinh**, đại diện nhóm phát triển dự án **HistoryTalk**. Sau đây, em xin phép được trình bày báo cáo **Report 2 — Kế hoạch Quản lý Dự án (Project Management Plan - PMP)**.*
>
> *Báo cáo này đóng vai trò là kim chỉ nam cho toàn bộ hoạt động của dự án trong suốt 15 tuần phát triển, bao gồm các phương pháp ước tính công sức WBS, chỉ số chất lượng phần mềm, chiến lược quản lý rủi ro, phân công trách nhiệm RACI và hạ tầng tự động hóa CI/CD trên Cloud."*

---

## 🕒 PHẦN 2: PHẠM VI DỰ ÁN & ƯỚC TÍNH NGUỒN LỰC WBS (1.5 phút)

### 🖥️ Slide 2: Scope & Effort Estimation (Bảng 2 trong Report 2)
* **Kịch bản nói (Lời Định):**
> *"Đầu tiên, về mặt **Phạm vi và Ước tính Ngày công (WBS Effort Estimation)**:*
>
> *Dự án HistoryTalk kéo dài trong **15 tuần** với **5 thành viên**. Mỗi thành viên dành trung bình **2 ngày công tập trung mỗi tuần** cho Capstone (tương đương 30 man-days/người trong cả kỳ). Như vậy, tổng ngân sách công sức của toàn nhóm được hoạch định chính xác là **150 man-days**.*
>
> *Con số 150 man-days này được nhóm phân bổ cân bằng và hợp lý vào **4 Module công việc chính**:*
> 1. **Module 1 - Student (Learner): 35 man-days** — Phục vụ trải nghiệm học sinh bao gồm Auth/Google OAuth2, AI Roleplay Chat SSE Streaming, Bản đồ Lịch sử tương tác và Quiz Engine bấm giờ.
> 2. **Module 2 - Educator & Classroom Management: 40 man-days** — Phục vụ Giáo viên quản lý lớp B2B, tạo bài tập đa nhiệm (Context + Chat + Quiz), sổ điểm và báo cáo mức độ tương tác (Engagement Analytics).
> 3. **Module 3 - System & Content Admin: 40 man-days** — Dành cho Admin quản trị Bối cảnh/Nhân vật lịch sử, đường ống xử lý tài liệu OCR (PDFBox & Tess4J), cổng thanh toán PayOS và Dashboard giám sát JVM/Token.
> 4. **Module 4 - Testing & DevOps Infrastructure: 35 man-days** — Dành cho công tác viết Test Cases, kiểm thử tích hợp hệ thống và xây dựng hạ tầng CI/CD tự động trên máy chủ AWS EC2."*

---

## 🕒 PHẦN 3: QUY TRÌNH SCRUM & CHỈ SỐ CHẤT LƯỢNG SOFTWARE METRICS (1.5 phút)

### 🖥️ Slide 3: Project Objectives & Quality Metrics (Bảng 3 trong Report 2)
* **Kịch bản nói (Lời Định):**
> *"Tiếp theo, em xin trình bày về **Quy trình Phát triển và Các Chỉ số Chất lượng (Quality Metrics)**:*
>
> *Về quy trình, nhóm áp dụng mô hình **Agile/Scrum** với chu kỳ **Sprint 2 tuần** kéo dài suốt 15 tuần. Các chu kỳ Sprint ngắn cho phép nhóm liên tục phản hồi, tinh chỉnh yêu cầu và bàn giao tính năng theo từng mốc Review của bộ môn:*
> * *Giai đoạn 1 (T1-T4):* Khảo sát nghiệp vụ, DB Schema, Auth (mốc Review 1).
> * *Giai đoạn 2 (T5-T9):* Core AI Chat SSE, RAG Search, B2B Class APIs, Quiz Engine (mốc Review 2).
> * *Giai đoạn 3 (T10-T15):* Thanh toán PayOS, System Analytics, OCR Document, System Testing & Deploy AWS EC2 (mốc Final Defense).
>
> *Về các chỉ số đo lường chất lượng phần mềm:*
> * **Tỷ lệ kiểm thử (Test Coverage):** Nhóm quy định **100% Pull Request** đều phải qua Peer Review, độ bao phủ **Unit Test ≥ 80%**, **Integration Test ≥ 85%** và **System Test đạt 100%** các luồng nghiệp vụ.
> * **Chiến lược Phân bổ Lỗi (Shift Left Defect %):** Nhóm triệt để áp dụng tư duy *Shift Left* — đưa việc phát hiện lỗi về sớm nhất có thể. Nhóm đặt mục tiêu bắt **60% số lỗi ngay ở giai đoạn đầu** (gồm **30% ở bước Review tài liệu/Code PR** và **30% ở bước Unit Test**) để giảm tối đa chi phí sửa lỗi muộn. 40% lỗi còn lại thuộc về Integration Test (20%) và System Test (20%).
> * **Chỉ số nghiệm thu UAT:** Đạt **0 lỗi Critical/Blocker** và dưới **5 lỗi Minor** khi nghiệm thu cuối kỳ."*

---

## 🕒 PHẦN 4: QUẢN LÝ RỦI RO CỐT LÕI (RISK MANAGEMENT) (1.5 phút)

### 🖥️ Slide 4: Risk Management & Mitigation Strategies (Bảng 4 trong Report 2)
* **Kịch bản nói (Lời Định):**
> *"Kính thưa Hội đồng, trong quá trình phát triển một sản phẩm EdTech tích hợp AI như HistoryTalk, nhóm đã nhận diện **4 Rủi ro cốt lõi** và xây dựng giải pháp kỹ thuật triệt để:*
>
> * 🔴 **Rủi ro 1: AI Hallucination (AI bịa đặt sự kiện lịch sử)** *(Độ ảnh hưởng: Cao)*  
>   👉 *Giải pháp:* Nhóm xây dựng kiến trúc **RAG 2 lớp** (Dense Retrieval kết hợp Kaggle Reranker) cùng bộ System Prompt ngặt nghèo, bắt buộc AI chỉ được trả lời dựa trên kho tri thức lịch sử đã kiểm duyệt.
>
> * 🟡 **Rủi ro 2: Học sinh Spam tin nhắn Chat AI để lấy điểm thành tích** *(Độ ảnh hưởng: Trung bình/Cao)*  
>   👉 *Giải pháp:* Nhóm áp dụng 3 lớp bảo vệ: Bộ lọc độ dài tối thiểu (≥ 10 ký tự), **trừ Token người dùng theo từng tin nhắn**, và đặc biệt là **chốt điểm số chính thức dựa trên kết quả bài Quiz bấm giờ (Timed Quiz)** chứ không phụ thuộc vào số lượng tin nhắn chat.
>
> * 🟢 **Rủi ro 3: Mất kết nối Webhook khi thanh toán PayOS** *(Độ ảnh hưởng: Thấp)*  
>   👉 *Giải pháp:* Thiết lập dịch vụ ngầm Scheduler đối soát tự động (`PaymentFulfillmentReconciliationScheduler`) quét và quét lại các giao dịch treo mỗi 5 phút.
>
> * 🟡 **Rủi ro 4: File PDF sách lịch sử cũ scan bị mờ, OCR thất bại** *(Độ ảnh hưởng: Trung bình)*  
>   👉 *Giải pháp:* Kết hợp **Apache PDFBox** để trích xuất text trực tiếp với các file PDF chuẩn, và tích hợp thư viện **Tess4J** tiền xử lý tăng độ nét lên **300 DPI** đối với các file PDF tài liệu scan cũ."*

---

## 🕒 PHẦN 5: MA TRẬN PHÂN CÔNG RACI & GIAO TIẾP (1 phút)

### 🖥️ Slide 5: RACI Matrix & Communications (Bảng 7 & Bảng 8 trong Report 2)
* **Kịch bản nói (Lời Định):**
> *"Về mặt phân công nhân sự, nhóm tuân thủ **Ma trận RACI** với nguyên tắc tối thượng: **Mỗi hạng mục công việc chỉ có duy nhất 1 người chịu trách nhiệm chính (A - Accountable)**:*
>
> * **Bạn Nguyễn Minh Trí (Leader & AI Engineer):** Phụ trách cốt lõi AI RAG Pipeline, Streaming SSE Chat & Prompting.
> * **Em — Trần Quốc Dinh (Backend & DevOps):** Phụ trách phân hệ B2B Class APIs, Progress Analytics, System Tracking Dashboard và hạ tầng CI/CD trên AWS EC2.
> * **Bạn Võ Đồng Đức Khải (Backend Developer):** Phụ trách Auth/Google OAuth2, Multi-tenant DB, Content CRUD và OCR Document Pipeline.
> * **Bạn Nguyễn Công Thành (Frontend & Lead Tester):** Phụ trách toàn bộ giao diện Web/App UI và xây dựng bộ Test Case Specification.
> * **Bạn Đặng Quốc Bảo (Backend Developer):** Phụ trách Quiz Engine API, Cổng thanh toán PayOS và Bản đồ tương tác Spatio-Temporal Map.
>
> *Về **Quy trình Giao tiếp (Communications)**: Nhóm họp cố định với Thầy hướng dẫn vào Thứ 6 hàng tuần, họp nhóm nội bộ kiểm điểm Sprint vào Thứ 7 hàng tuần, và trao đổi code review hàng ngày qua Zalo & GitHub PR."*

---

## 🕒 PHẦN 6: QUẢN LÝ CẤU HÌNH & HẠ TẦNG CI/CD TỰ ĐỘNG (1 phút)

### 🖥️ Slide 6: Configuration Management & DevOps CI/CD Automation (Mục 6 & Bảng 9)
* **Kịch bản nói (Lời Định):**
> *"Cuối cùng, em xin giới thiệu về **Quản lý Cấu hình và Hạ tầng CI/CD Tự động hóa** của HistoryTalk:*
>
> *Nhóm quản lý nguồn mã tập trung trên GitHub Monorepo theo quy trình **Git Flow** (`main` cho Production, `deployment_v5` cho Staging, `feature/*` cho từng tính năng) và tuân thủ chuẩn commit *Conventional Commits*.
>
> *Toàn bộ quy trình triển khai lên Cloud được **tự động hóa 100% qua GitHub Actions (`deploy.yml`)**:*
> 1. Khi developer merge code vào branch `main`, GitHub Actions tự động kích hoạt workflow build Java 21 và Python AI thành các Docker Images.
> 2. Các images này được đẩy lên **GitHub Container Registry (`ghcr.io`)**.
> 3. GitHub Actions SSH an toàn vào máy chủ **AWS EC2 `c6a.large`** (đã cấu hình 4GB Swap RAM), thực hiện kéo code mới, pull Docker Images và lệnh `docker compose up -d --force-recreate`.
> 4. Container **Caddy Reverse Proxy** tự động điều hướng SSL HTTPS cho domain `historytalk.app` đến Java Spring Boot (port 8080) và Python AI (port 8001)."*

---

## 🕒 PHẦN 7: LỜI KẾT (15 giây)

### 🖥️ Slide 7: Conclusion & Q&A
* **Kịch bản nói (Lời Định):**
> *"Tóm lại, Report 2 đã xây dựng một kế hoạch quản lý toàn diện từ nhân sự, chất lượng, rủi ro đến hạ tầng kỹ thuật, đảm bảo dự án HistoryTalk về đích đúng tiến độ với chất lượng cao nhất.
>
> Em xin chân thành cảm ơn Thầy/Cô trong Hội đồng đã chú ý lắng nghe. Em xin phép được lắng nghe các câu hỏi và nhận xét từ Hội đồng ạ!"*
