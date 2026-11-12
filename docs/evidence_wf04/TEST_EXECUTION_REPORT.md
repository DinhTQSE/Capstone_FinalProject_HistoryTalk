# 📊 TEST EXECUTION REPORT — WORKFLOW WF-04 (E2E LIVE BROWSER)

> **Environment:** `https://historytalk.online`  
> **Date:** 2026-11-12  
> **Class Tested:** Lớp `11A2` (`25` học sinh)  
> **Accounts:**  
> - Teacher: `gv.hong.d1@example.com` (Võ Thị Hồng)  
> - Student: `hs.11a2.07.d1@example.com` (Hồ Thu Ngân - `HS11A207`)

---

## Tổng hợp Kết quả

| ID | Kịch bản | Trạng thái | Kết quả thực tế quan sát được |
| :--- | :--- | :---: | :--- |
| **E2E-WF04-01** | Tạo bài tập đủ 3 loại & Eager Init | **PASS** | Tạo thành công 7 bài tập test (`QUIZ_TEST`, `AI_CHAT_ROLEPLAY`, `CONTEXT_READING`). Gradebook khởi tạo ngay `25/25` học sinh ở trạng thái `Chưa bắt đầu` (`NOT_STARTED`), `averageScore = —`. |
| **E2E-WF04-02** | Validation Deadline quá khứ | **PASS** | Nhập deadline `2026-11-11T23:59` bị form chặn ngay với thông báo `"Hạn nộp phải ở tương lai"`. |
| **E2E-WF04-03** | Xem Gradebook chi tiết | **PASS** | Hiển thị đúng điểm và trạng thái của Hồ Thu Ngân: Quiz `10.00` (`Đã nộp`), Reading Partial `4.17` (`25s/60s`, `Đã nộp`), Late & Extend `10.00` (`Nộp muộn`). |
| **E2E-WF04-04** | Gia hạn Deadline & Auto-Revert | **PASS** | Dời hạn `[E2E-WF04] Late & Extend` từ `19:08 12/11` → `23:59 13/11`: trạng thái Hồ Thu Ngân tự động revert từ `Nộp muộn` (`LATE_SUBMITTED`) → `Đã nộp` (`SUBMITTED`). |
| **E2E-WF04-05** | Hủy bài tập (Soft Delete `CANCELLED`) | **FAIL (Partial)** | Phía HS bị chặn nộp (`HTTP 400`), nhưng phía GV tab `Đã hủy` không hiển thị bài đã hủy và `GET /api/v1/teacher/assignments/{id}` trả `HTTP 404` (xem `DEF-WF04-02`). |
| **E2E-WF04-06** | Main Path `DS-04a`: `QUIZ_TEST` đúng hạn | **PASS** | Start (`IN_PROGRESS`), payload câu hỏi không lộ đáp án trước khi nộp, làm đúng 10/10 câu (`Chiến dịch Điện Biên Phủ (1954)`), nộp Quiz + nộp Assignment → `10.00 / 10`, `Đã nộp`. |
| **E2E-WF04-07** | Chống nộp đè (`MSG28`) | **PASS** | UI ẩn nút nộp sau khi đã nộp (`"Bài đã nộp không thể nộp lại."`); gọi trực tiếp `POST .../submit` bị chặn `HTTP 400`. |
| **E2E-WF04-08** | `AI_CHAT_ROLEPLAY` đủ tin nhắn (`>= 3`) | **BLOCKED (Fail by AI 500)** | Tạo phiên mới (`848e8d75-...`) và gửi tin nhắn nhưng AI stream lỗi `500` (*"Không kết nối được máy chủ AI..."*) → `messageCount = 0`, nộp bài ra `0.00 / 10` (xem `DEF-WF04-01`). |
| **E2E-WF04-09** | `AI_CHAT_ROLEPLAY` một phần (`< 3`) | **BLOCKED (Fail by AI 500)** | Phiên cũ bị chặn tái sử dụng (`HTTP 400`), phiên mới (`8c2f1f53-...`) gửi tin nhắn gặp AI stream `500` → `messageCount = 0`, nộp bài ra `0.00 / 10` (xem `DEF-WF04-01`, `DEF-WF04-03`). |
| **E2E-WF04-10** | `CONTEXT_READING` đủ thời gian + Battle Map (F-12) | **PASS** | Mở `Trận Bạch Đằng (1288)`, kiểm tra `/map` có đủ 5 stages/focuses, admin pins, personal pins, chế độ Tự vẽ & Thử thách trí nhớ; đọc `>15s` rồi nộp → `10.00 / 10`, `Đã nộp`. |
| **E2E-WF04-11** | `CONTEXT_READING` chưa đủ thời gian & Chưa Start | **PASS** | Trước khi bấm Start không có nút Nộp bài; bấm Start, đọc `25s / 60s` rồi nộp → chấm tỷ lệ chính xác `4.17 / 10`, `Đã nộp`. |
| **E2E-WF04-12** | Nộp trễ `LATE_SUBMITTED` (`DS-04b`) | **PASS** | Đợi quá hạn `19:08 12/11`, UI hiện `Quá hạn`, HS đọc `31s` và nộp lúc `19:19:30` → `LATE_SUBMITTED` (`Nộp muộn`), điểm `10.00`. |
| **E2E-WF04-13** | Chặn bài đã hủy & Đồng bộ Dashboard | **PASS** | HS vào link trực tiếp bài đã hủy và bấm Bắt đầu đọc bị chặn `HTTP 400` (*"Bài tập này hiện không còn khả dụng"*); Dashboard GV (`/classes/...?tab=stats`) và HS (`/home`, `/grades`) đồng bộ đúng số liệu. |

---

## Thống kê
- **Tổng số Test Cases:** `13`
- **PASS:** `10`
- **FAIL / BLOCKED:** `3` (`E2E-WF04-05` sai lệch soft-delete/tab Đã hủy; `E2E-WF04-08` & `E2E-WF04-09` bị block do AI Service `500` trên môi trường).

## Screenshots Evidence
Lưu tại `docs/evidence_wf04/screenshots/`:
- `1f18904e_final_step_80.png` — Gradebook Eager Initialization (25/25 `Chưa bắt đầu`)
- `1f18904e_final_step_137.png` — Teacher Dashboard & Analytics Lớp 11A2 sau khi nộp bài
- `8b8264dc_final_step_88.png` — Student Reading & Battle Map / Quiz flow
- `8b8264dc_final_step_129.png` — Student Grades & Progress Dashboard (`/grades`)
- `f2e32cf3_final_step_119.png` — Student AI Chat & Late Submission flow
