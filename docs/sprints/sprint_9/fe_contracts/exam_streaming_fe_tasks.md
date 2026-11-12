# Vấn đáp chạy chữ: việc FE cần sửa

> Chi tiết API đầy đủ ở [chat_exam_mode_api_contract.md](chat_exam_mode_api_contract.md), mục 2.3.

## Thay đổi ở BE (đã deploy)

* Câu trả lời vấn đáp giờ **chạy chữ** qua `POST /chat/messages/stream`, giống chat thường.
* Thứ tự chữ: lời nhận xét → `\n---\n` → câu hỏi tiếp theo. Ở câu cuối, sau `---` là lời tổng kết điểm.
* Câu hỏi tiếp theo được nhân vật đặt **dựa trên câu trả lời vừa rồi**, không sinh sẵn lúc tạo session.
* `examVerdict` và `exam` nằm trong event `metadata`, tới **sau** khi chữ chạy xong.
* `POST /chat/messages` (không stream) vẫn dùng được, response không đổi. Chỉ là không có chữ chạy dần.
* Request, URL và cách tạo session không đổi.

## Các event của một lượt

```text
data:{"type":"text","data":"Ngươi đã nêu được "}
data:{"type":"text","data":"một phần."}
data:{"type":"text","data":"\n---\n"}
data:{"type":"text","data":"Vậy sau trận ấy, ta đóng đô ở đâu?"}
data:{"type":"metadata","data":{"suggestedQuestions":[],"promptTokens":59,"completionTokens":56,"quotes_used":["..."],"examVerdict":"PARTIAL","exam":{"totalQuestions":5,"answeredQuestions":1,"correctCount":0,"partialCount":1,"incorrectCount":0,"score":1.0,"finished":false}}}
data:{"type":"done","remainingTokens":99993903}
```

Khi lỗi:

| Tình huống | BE trả về | FE cần làm |
|---|---|---|
| AI lỗi giữa chừng | `{"type":"error","message":"..."}` rồi `{"type":"done"}`, có thể sau vài event `text` | Xóa phần chữ dở và tin của người học khỏi khung chat, báo lỗi, cho gửi lại. BE không lưu gì, không trừ token |
| Buổi đã kết thúc | HTTP `400` JSON `Buổi vấn đáp này đã kết thúc.`, không mở stream | Khóa ô nhập, tải lại messages |
| Ngôn từ không phù hợp, hết token, khóa gói | Giống chat thường | Giữ cách xử lý hiện tại |

## Web: đã sửa sẵn (commit trên `master`)

* `chatService.sendMessageStream`: `onDone` có thêm `exam` và `examVerdict`.
* `chat-main.tsx` → `sendExamAnswer`: dùng `sendMessageStream`, hiện chữ chạy dần, đọc giọng từng câu, không đọc `---`, cập nhật `exam` vào cache messages.

Việc web còn lại:
- [ ] Test trên production: trả lời đúng, sai, câu cuối, và tắt mạng giữa chừng.
- [ ] Kiểm tra nhãn Đúng / Một phần / Sai hiện ở tin vừa nhận sau khi stream xong.
- [ ] Kiểm tra câu hỏi tiếp theo hiện thành bong bóng riêng ngay khi `---` tới.

## Mobile: cần sửa

- [ ] Gửi câu trả lời vấn đáp qua `chatApi.streamMessage` thay cho `chatApi.sendMessage`.
- [ ] `chatApi.streamMessage` phải đọc event `metadata` và trả ra `examVerdict`, `exam` (hiện đang bỏ qua `metadata`).
- [ ] Hiện chữ chạy dần. Tách bong bóng tại `---` cả khi đang stream.
- [ ] Hiện "Nhân vật đang chấm bài..." tới khi có chữ đầu tiên.
- [ ] Khi stream xong: gắn `examVerdict` vào tin của nhân vật, cập nhật "Câu X/Y" và điểm từ `exam`. Khi `exam.finished = true` thì khóa ô nhập và hiện điểm.
- [ ] Event `error`: xóa phần chữ dở và tin của người học, báo lỗi, cho gửi lại.
- [ ] Bỏ `---` trước khi đọc giọng.
- [ ] Khóa nút gửi tới khi nhận `done`, tránh gửi 2 lần bị chấm 2 lần.

Nếu chưa sửa kịp, mobile vẫn có thể dùng tạm `chatApi.sendMessage` như cũ, chỉ là không có chữ chạy.

## Lưu ý

* Đáp án của câu tiếp theo không bao giờ có trong stream hay response.
* Tài khoản `CUSTOMER` chưa mua gói luôn bị giới hạn 2 câu, dù gửi `examQuestionCount` bao nhiêu. Đọc số câu thật từ `examQuestionCount` trong response tạo session hoặc `exam.totalQuestions`.
* Thời gian tới chữ đầu tiên của vấn đáp chưa được đo lại sau khi chuyển sang stream.
