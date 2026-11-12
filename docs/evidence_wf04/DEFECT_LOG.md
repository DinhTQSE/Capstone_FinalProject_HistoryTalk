# 🐞 DEFECT LOG — WORKFLOW WF-04 E2E TESTING

> **Environment:** `https://historytalk.online`  
> **Date:** 2026-11-12  
> **Tester:** Antigravity Multi-Agent Live Browser E2E  
> **Accounts:** `gv.hong.d1@example.com` (Teacher), `hs.11a2.07.d1@example.com` (Student - Hồ Thu Ngân)

---

## DEF-WF04-01: Dịch vụ AI Upstream lỗi 500 khiến `AI_CHAT_ROLEPLAY` không thể chấm điểm theo số tin nhắn
- **Severity:** **Critical / High** (Environment / AI Service)
- **Test Case liên quan:** `E2E-WF04-08`, `E2E-WF04-09`
- **Các bước tái hiện (Steps to Reproduce):**
  1. Đăng nhập Học sinh `hs.11a2.07.d1@example.com`.
  2. Mở bài tập `[E2E-WF04] AI Chat Partial` (`c8ef466c-8dd4-416d-9f78-b00dc2bd0d1f`) hoặc `[E2E-WF04] AI Chat Full` (`690c0b25-7b4f-41e8-9da3-5f5cd7b3eae8`).
  3. Tạo phiên chat mới ("Phỏng vấn mới") với nhân vật `Võ Nguyên Giáp` (`8c2f1f53-b09b-4654-a90b-6a16c7cb03ea` / `848e8d75-801f-49a7-b937-e1b3cb9ffc44`).
  4. Gửi tin nhắn chat.
- **Kết quả thực tế (Actual):**
  - Endpoint stream trả về `500 INTERNAL_ERROR`.
  - UI hiển thị lỗi: *"Võ Nguyên Giáp chưa trả lời được vì dịch vụ AI đang gặp sự cố. Thử lại sau ít phút nhé."* / *"Không kết nối được máy chủ AI để tra cứu tư liệu. Vui lòng thử lại sau."*
  - Do không có phản hồi AI hoàn chỉnh, `messageCount` được server tính là `0`, dẫn đến khi nộp bài chỉ đạt `0.00 / 10`.
  - *(Lưu ý: Cơ chế chống trừ oan token `MSG14` hoạt động đúng, không làm crash app, nhưng dịch vụ AI trên production/staging đang gặp sự cố làm block luồng test chấm điểm `AI_CHAT_ROLEPLAY`).*
- **Kết quả mong đợi (Expected):**
  - AI stream phản hồi bình thường để server ghi nhận cặp tin nhắn và chấm điểm tỷ lệ `messageCount / minRequirement * 10`.

---

## DEF-WF04-02: Tab lọc `Đã hủy` (`CANCELLED`) của Giáo viên không hiển thị bài tập sau khi Hủy, và trả về HTTP 404
- **Severity:** **Medium** (Spec vs Implementation Mismatch)
- **Test Case liên quan:** `E2E-WF04-05`
- **Các bước tái hiện (Steps to Reproduce):**
  1. Đăng nhập Giáo viên `gv.hong.d1@example.com`.
  2. Vào chi tiết bài tập `[E2E-WF04] To Be Cancelled` (`fac2daa1-b654-4540-bf34-8d6c958d2565`).
  3. Bấm **Hủy bài** → Xác nhận **Hủy bài tập**.
  4. Chuyển sang tab lọc **Đã hủy** trên trang danh sách bài tập của lớp 11A2, hoặc truy cập trực tiếp `/teaching/assignments/fac2daa1-b654-4540-bf34-8d6c958d2565`.
- **Kết quả thực tế (Actual):**
  - Modal xác nhận ghi: *“‘[E2E-WF04] To Be Cancelled’ sẽ bị xóa khỏi lớp 11A2, kể cả 0 bài đã nộp và điểm — bạn sẽ không xem lại được. Chỉ muốn ngừng nhận bài thì dùng ‘Đóng bài’.”*
  - Tab **Đã hủy** hiển thị *"Không có bài tập phù hợp"*.
  - `GET /api/v1/teacher/assignments/fac2daa1-b654-4540-bf34-8d6c958d2565` trả về `HTTP 404`.
- **Kết quả mong đợi (Expected theo `assignment_end_to_end_test_flows.md` & `assignment_management_and_submission_api_contract.md`):**
  - `DELETE /api/v1/teacher/assignments/{id}` thực hiện Soft Delete chuyển `status = 'CANCELLED'`, bảo toàn dữ liệu bài nộp cũ trong DB và cho phép Giáo viên lọc xem lại trong tab `CANCELLED` (`Đã hủy`).

---

## DEF-WF04-03 (UX Observation - Low): UI hiển thị số tin nhắn của phiên chat cũ đã bị khóa khi mở bài `AI_CHAT_ROLEPLAY` mới
- **Severity:** **Low** (UX)
- **Test Case liên quan:** `E2E-WF04-09`
- **Mô tả:**
  - Khi học sinh mở bài `[E2E-WF04] AI Chat Partial`, nút nộp bài ban đầu hiển thị `"Nộp bài ( 1 tin nhắn)"` do lấy từ phiên chat cũ với `Võ Nguyên Giáp`, nhưng khi bấm vào phiên cũ để chat tiếp thì Backend chặn với `HTTP 400` (*"Phiên trò chuyện đã được sử dụng cho một bài tập đã nộp, không thể tiếp tục trò chuyện"*).
  - Backend chặn đúng (anti-cheat), nhưng Frontend nên kiểm tra phiên chưa gắn với bài tập đã nộp trước khi hiển thị gợi ý số tin nhắn trên nút nộp bài.
