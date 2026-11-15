# 📋 TEST PLAN END-TO-END: WORKFLOW WF-04 (CLASSROOM ASSIGNMENT & GRADING)

> **Owner:** Võ Đồng Đức Khải (`KhaiVDD`)  
> **Target Environment:** `https://historytalk.online`  
> **Accounts:**  
> - Teacher: `gv.hong.d1@example.com` / `12345678`  
> - Student: `hs.11a2.07.d1@example.com` / `1234567890`  
> **Execution Method:** Live Browser UI E2E (Multi-Agent, No Workaround)

---

## Danh sách Kịch bản & Test Cases

| ID | Nhóm | Tên Kịch bản | Actor | Điều kiện / Dữ liệu | Kết quả mong đợi |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **E2E-WF04-01** | Teacher | Tạo bài tập đủ 3 loại (`QUIZ_TEST`, `AI_CHAT_ROLEPLAY`, `CONTEXT_READING`) & Eager Init | Teacher | Lớp phụ trách có HS `hs.11a2.07.d1@example.com` đang `ACTIVE` | Tạo thành công 201; Gradebook ban đầu hiển thị `totalStudents >= 1`, `notStartedCount = totalStudents`, tất cả HS có trạng thái `NOT_STARTED`. |
| **E2E-WF04-02** | Teacher | Validation tạo bài tập với Deadline quá khứ / thiếu trường | Teacher | Nhập deadline < hiện tại hoặc bỏ trống title | UI/API chặn tạo, hiển thị thông báo lỗi (`MSG33` / validation error). |
| **E2E-WF04-03** | Teacher | Xem & theo dõi Gradebook chi tiết | Teacher | Có bài tập đã tạo & bài tập đã có HS nộp | Hiển thị đúng `submittedCount`, `lateSubmittedCount`, `inProgressCount`, `notStartedCount`, `averageScore`, danh sách từng HS. |
| **E2E-WF04-04** | Teacher | Cập nhật & Gia hạn Deadline + Auto-Revert `LATE_SUBMITTED` → `SUBMITTED` | Teacher | Có HS đã nộp `LATE_SUBMITTED` | Dời deadline ra sau `submittedAt` → trạng thái HS tự động revert về `SUBMITTED`. |
| **E2E-WF04-05** | Teacher | Hủy bài tập (Soft Delete `CANCELLED`) | Teacher | Bài tập đang `ACTIVE` | Hủy thành công (`CANCELLED`); HS không còn làm/nộp được bài này; dữ liệu cũ vẫn bảo toàn. |
| **E2E-WF04-06** | Student Quiz | Main Path `DS-04a`: Làm & nộp `QUIZ_TEST` đúng hạn | Student | Bài tập `QUIZ_TEST` `ACTIVE`, deadline tương lai | Bắt đầu (`IN_PROGRESS`), không lộ đáp án trước khi nộp, làm quiz, nộp quiz, nộp bài tập → `SUBMITTED`, điểm thang 10 khớp tỷ lệ đúng, Gradebook GV cập nhật. |
| **E2E-WF04-07** | Student Quiz | Chặn nộp đè (`MSG28`) & Chặn nộp Quiz chưa hoàn thành | Student | Bài đã `SUBMITTED` & bài Quiz mới bấm Start chưa nộp | Nộp lại lần 2 bị chặn (`MSG28`); nộp assignment khi quiz chưa submit bị chặn. |
| **E2E-WF04-08** | Student AI Chat | Làm & nộp `AI_CHAT_ROLEPLAY` đủ số tin nhắn (`>= minRequirement`) | Student | Bài tập `AI_CHAT_ROLEPLAY` (`minRequirement = 3`) | Bắt đầu, chat đúng nhân vật đủ >= 3 câu, AI stream bình thường, nộp bài → `score = 10.00`, `messageCount >= 3`, `status = SUBMITTED`. |
| **E2E-WF04-09** | Student AI Chat | Làm & nộp `AI_CHAT_ROLEPLAY` chưa đủ số câu & Chưa có phiên chat | Student | Bài tập `AI_CHAT_ROLEPLAY` (`minRequirement = 3`) | Chưa chat bấm nộp → bị chặn; chat 1 câu rồi nộp → điểm tỷ lệ (`3.33` / `10`), `status = SUBMITTED`. |
| **E2E-WF04-10** | Student Reading | Làm & nộp `CONTEXT_READING` đủ thời gian + Battle Map (F-12) | Student | Bài tập `CONTEXT_READING` (`minRequirement` ngắn, ưu tiên context có Battle Map) | Bấm Start, đọc đủ thời gian (kiểm tra Battle Map pins/focuses/practice nếu có), bấm Hoàn thành → `score = 10.00`, `status = SUBMITTED`. |
| **E2E-WF04-11** | Student Reading | Làm & nộp `CONTEXT_READING` chưa đủ thời gian & Chưa Start | Student | Bài tập `CONTEXT_READING` (`minRequirement = 60s`) | Chưa bấm Start không cho nộp; bấm Start rồi nộp ngay sau vài giây → điểm tỷ lệ $\text{duration}/\text{minReq} \times 10$. |
| **E2E-WF04-12** | Student Late | Nộp trễ `LATE_SUBMITTED` khi quá deadline (`DS-04b`) | Student | Bài tập có deadline đã qua | UI hiển thị trạng thái quá hạn, HS nộp bài → `status = LATE_SUBMITTED`. |
| **E2E-WF04-13** | Cross-check | Chặn nộp bài đã hủy (`CANCELLED`) & Đồng bộ Dashboard | Student / Teacher | Bài tập đã bị hủy ở E2E-WF04-05 | HS không thấy hoặc không nộp được bài đã hủy; Dashboard GV/HS cập nhật đúng tiến độ & điểm. |
