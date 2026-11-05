# 📑 FRONTEND API CONTRACT: CHẾ ĐỘ VẤN ĐÁP & THAY ĐỔI LUỒNG CHAT
> **Sprint:** Sprint 9  
> **Module:** AI Chat (`/chat/*`)  
> **Base URL:** `/Historical-tell/api/v1`  
> **Authentication:** Bearer Token (`Authorization: Bearer <jwt_access_token>`)  
> **Trạng thái BE:** Chế độ vấn đáp đã deploy production (commit `1422df3`). Mục 2.6 (lọc ngôn từ) và số trích dẫn theo loại câu hỏi đang chờ deploy. FE chưa tích hợp.

---

## 1. Tóm tắt cho Frontend

Trong chế độ vấn đáp, **nhân vật hỏi và chấm, người học trả lời**. FE không cần API mới:

* Thêm `mode: "EXAM"` khi tạo session.
* Gửi câu trả lời qua đúng API gửi tin nhắn hiện tại.
* Đọc thêm `exam` và `examVerdict` trong response.

### Luồng một buổi vấn đáp
1. `POST /chat/sessions` với `mode: "EXAM"`. BE trả session, và tin nhắn đầu tiên đã chứa câu hỏi số 1.
2. `GET /chat/sessions/{id}/messages` để hiển thị tin nhắn đó.
3. Người học trả lời qua `POST /chat/messages`.
4. Response gồm nhận xét, kết quả chấm và câu hỏi tiếp theo. Lặp lại bước 3.
5. Khi `exam.finished = true`: khóa ô nhập và hiện điểm. Gửi thêm sẽ nhận lỗi `400`.

**Khuyến nghị:** dùng `POST /chat/messages` (không stream) cho vấn đáp, vì câu trả lời vẫn về một lần. API stream vẫn hoạt động, xem mục 2.3.

---

## 2. API

### 2.1. Tạo session: `POST /chat/sessions`

Chat thường không đổi: không gửi `mode` thì vẫn là `CHAT`.

#### Request Body
```json
{
  "characterId": "55ae763f-2c8f-4918-bf31-b43566c3c355",
  "contextId": "d99b373f-dde4-49e8-8c93-1f31d6e8b5e7",
  "mode": "EXAM",
  "examQuestionCount": 5
}
```

| Trường | Bắt buộc | Mô tả |
|---|---|---|
| `mode` | Không | `CHAT` (mặc định) hoặc `EXAM` |
| `examQuestionCount` | Không | Từ 3 đến 10, mặc định 5. Chỉ dùng với `EXAM` |

#### Response `201`: các trường mới
```json
{
  "id": "5c72b6af-e296-4bd0-b8db-1f15c1a6f7fb",
  "title": "Vấn đáp với Ngô Quyền",
  "lastMessage": "Ta là Ngô Quyền. Hôm nay ta sẽ hỏi ngươi 5 câu. Hãy trả lời theo hiểu biết của ngươi.\n---\nTrận địa cọc ngầm có vai trò gì?",
  "messageCount": 1,
  "mode": "EXAM",
  "examQuestionCount": 5
}
```

`mode` có ở mọi session. `examQuestionCount` chỉ có với session vấn đáp.

#### Lỗi
| HTTP | Khi nào | Message |
|---|---|---|
| `400` | `examQuestionCount` ngoài khoảng 3–10 | `Số câu vấn đáp tối thiểu là 3` hoặc `Số câu vấn đáp tối đa là 10` |
| `400` | Nhân vật không có tài liệu lẫn tiểu sử | `Nhân vật này chưa có đủ tư liệu để vấn đáp.` |
| `5xx` | Dịch vụ AI lỗi | Session không được tạo, người dùng có thể thử lại |

---

### 2.2. Gửi câu trả lời: `POST /chat/messages`

Request giữ nguyên.

```json
{ "sessionId": "5c72b6af-...", "content": "Câu trả lời của người học", "messageType": "TEXT" }
```

#### Response `201`
```json
{
  "userMessage": { "id": "...", "role": "USER", "content": "Câu trả lời của người học" },
  "assistantMessage": {
    "id": "...",
    "role": "ASSISTANT",
    "content": "Ngươi đã nêu được một phần.\n---\nCâu hỏi tiếp theo?",
    "examVerdict": "PARTIAL",
    "quotes": ["Đoạn tư liệu của câu vừa chấm"]
  },
  "suggestedQuestions": [],
  "remainingTokens": 99994468,
  "promptTokens": 117,
  "completionTokens": 111,
  "exam": {
    "totalQuestions": 5,
    "answeredQuestions": 1,
    "correctCount": 0,
    "partialCount": 1,
    "incorrectCount": 0,
    "score": 1.0,
    "finished": false
  }
}
```

| Trường | Mô tả |
|---|---|
| `assistantMessage.content` | Nhận xét và câu tiếp theo, ngăn cách bởi `---`. Web và mobile đã tự tách thành 2 bong bóng |
| `assistantMessage.examVerdict` | `CORRECT`, `PARTIAL` hoặc `INCORRECT`: kết quả của **câu vừa trả lời**. Không có ở tin nhắn chat thường và tin mở đầu |
| `assistantMessage.quotes` | Tư liệu của **câu vừa chấm**, không phải câu tiếp theo |
| `exam` | Chỉ có với session vấn đáp |
| `exam.score` | Thang 10. Đúng tính 1 câu, đúng một phần tính nửa câu, làm tròn 1 chữ số |
| `exam.finished` | `true` khi đã trả lời đủ. Khi đó phần sau `---` là lời tổng kết, không phải câu hỏi |

#### Lỗi
| HTTP | Khi nào | FE cần làm |
|---|---|---|
| `400` | Buổi đã kết thúc: `Buổi vấn đáp này đã kết thúc.` | Khóa ô nhập |
| `5xx` | AI lỗi khi chấm hoặc sinh câu tiếp | Câu trả lời **không được lưu**. Giữ chữ trong ô nhập để gửi lại |

Các lỗi hết token và khóa gói vẫn giống chat thường.

---

### 2.3. Stream: `POST /chat/messages/stream`

Vẫn dùng được cho vấn đáp, cùng định dạng event. Nhưng toàn bộ nội dung nằm trong **một** event `text`, không có chữ chạy dần:

```text
data:{"type":"text","data":"Nhận xét\n---\nCâu tiếp theo?"}
data:{"type":"metadata","data":{"suggestedQuestions":[],"promptTokens":59,"completionTokens":56,"quotes_used":["..."],"examVerdict":"PARTIAL","exam":{"totalQuestions":5,"answeredQuestions":1,"correctCount":0,"partialCount":1,"incorrectCount":0,"score":1.0,"finished":false}}}
data:{"type":"done","remainingTokens":99993903}
```

Nếu dùng stream, phải đọc `examVerdict` và `exam` trong `metadata`.

---

### 2.4. Mở lại session: `GET /chat/sessions/{id}/messages`

```json
{
  "messages": [{ "id": "...", "role": "ASSISTANT", "content": "...", "examVerdict": "CORRECT", "quotes": [] }],
  "suggestedQuestions": [],
  "exam": { "totalQuestions": 5, "answeredQuestions": 2, "correctCount": 1, "partialCount": 0, "incorrectCount": 1, "score": 2.0, "finished": false }
}
```

Có `exam` là session vấn đáp. Dùng nó để khôi phục tiến độ và khóa ô nhập.

### 2.5. Danh sách và lịch sử

`GET /chat/sessions` và `GET /chat/history` có thêm `mode` cho mỗi session, dùng để gắn nhãn "Vấn đáp".

---

### 2.6. Kiểm tra ngôn từ trước khi hiển thị: `POST /chat/messages/check`

Gọi API này **ngay khi người dùng nhấn gửi, trước khi đưa tin nhắn vào khung chat**. API không lưu gì, không gọi AI và không trừ token.

#### Request Body
```json
{ "content": "Nội dung người dùng vừa nhập" }
```

#### Response `200`: được phép gửi
```json
{ "success": true, "message": "Message content is allowed", "data": null }
```

#### Response `400`: bị chặn
```json
{
  "success": false,
  "errorCode": 400,
  "message": "INAPPROPRIATE_CONTENT: Phát hiện ngôn từ không phù hợp. Vui lòng điều chỉnh nội dung rồi gửi lại.",
  "status": "BAD_REQUEST"
}
```

#### Luồng trên giao diện
1. Người dùng nhấn Enter hoặc nút gửi. Khóa nút gửi.
2. Gọi `POST /chat/messages/check`.
3. **Nếu `400` có tiền tố `INAPPROPRIATE_CONTENT:`:** hiện popup với phần sau dấu `:`, **không** đưa tin vào khung chat, giữ nguyên chữ trong ô nhập.
4. **Nếu `200`:** đưa tin vào khung chat và gửi như bình thường.
5. **Nếu lỗi mạng hoặc `5xx`:** vẫn gửi bình thường, vì API gửi tin nhắn cũng tự kiểm tra.

#### API gửi tin nhắn vẫn tự chặn
`POST /chat/messages` và `POST /chat/messages/stream`, ở cả chat thường và vấn đáp, vẫn kiểm tra lại và trả đúng lỗi `400` trên, không lưu tin, không gọi AI, không trừ token. Với API stream, lỗi này là HTTP `400` kèm JSON, không phải event `error`.

Session không bị khóa. Người dùng sửa nội dung là gửi tiếp được.

---

## 3. Việc Frontend cần làm

### Lọc ngôn từ (web và mobile)
- [ ] Gọi `POST /chat/messages/check` trước khi đưa tin vào khung chat, theo luồng ở mục 2.6. Áp dụng cho cả chat, gọi thoại và vấn đáp.
- [ ] Bị chặn thì hiện popup cảnh báo, không hiện tin trong khung chat, giữ chữ trong ô nhập.
- [ ] Xử lý cả lỗi `INAPPROPRIATE_CONTENT` từ API gửi tin nhắn, phòng khi bước kiểm tra bị bỏ qua.
- [ ] Đo độ trễ khi gửi, vì mỗi tin nhắn có thêm một lượt gọi API.

### Chế độ vấn đáp (web và mobile)
- [ ] Thêm lựa chọn "Trò chuyện / Vấn đáp" và số câu (3–10) khi tạo session.
- [ ] Truyền `mode`, `examQuestionCount` vào `chatService.createSession` (web) và `chatApi.createSession` (mobile).
- [ ] Hiển thị "Câu X/Y" và điểm từ `exam`. Số câu đang hỏi: `answeredQuestions + 1`.
- [ ] Hiện nhãn Đúng / Một phần / Sai từ `examVerdict`.
- [ ] Ẩn câu hỏi gợi ý.
- [ ] Hiện trạng thái chờ như "Nhân vật đang chấm bài...", vì mỗi lượt mất khoảng 7–8 giây.
- [ ] Khóa nút gửi trong lúc chờ. BE chưa chặn gửi trùng, nên gửi 2 lần có thể bị chấm 2 lần.
- [ ] Khi `finished = true`, khóa ô nhập và hiện màn hình điểm.
- [ ] Mở lại session phải đọc `exam` từ API messages.
- [ ] Gắn nhãn "Vấn đáp" trong lịch sử theo `mode`.
- [ ] Bỏ `---` trước khi gửi nội dung sang đọc giọng nói.
- [ ] Đặt timeout tạo session và gửi câu trả lời đủ dài, như API chat hiện tại (90 giây trở lên).

### Mobile
`chatApi.streamMessage` chỉ trả về chữ và bỏ qua `metadata`. Để lấy `exam`, dùng `chatApi.sendMessage` cho vấn đáp, hoặc gọi lại API messages sau mỗi lượt.

---

## 4. Thay đổi hành vi của chat thường

FE không bắt buộc phải sửa gì thêm ở đây, nhưng cần biết:

| Thay đổi | Ảnh hưởng tới FE |
|---|---|
| Bỏ việc nhân vật ngẫu nhiên hỏi ngược | Câu trả lời chat thường thường không còn phần sau `---` |
| Số trích dẫn tùy loại câu hỏi | Hỏi một chi tiết: tối đa 3. Hỏi tổng hợp như "tóm tắt", "diễn biến": tối đa 5, câu trả lời có thể dài hơn và chậm hơn |
| Bỏ độ trễ 50ms mỗi chunk trên server | Chữ về nhanh hơn. Web đang có hàng đợi hiển thị riêng |
| Câu trả lời lỗi không được lưu | Câu hỏi và phần trả lời dở bị xóa khỏi lịch sử. FE phải báo lỗi, không hiện phần dở dang |
| Không tra cứu được tài liệu thì báo lỗi | Stream trả event `error`, API thường trả lỗi 5xx. Trước đây vẫn trả lời nhưng không có tài liệu |
| Message lỗi của stream | Là tiếng Việt, có thể hiện trực tiếp cho người dùng |
| GPU server tắt | Báo lỗi sau vài giây thay vì chờ tới 3 phút |

Web đã xử lý event `error` và lỗi cắt dòng ở commit `5461357`. Mobile đã xử lý sẵn cả hai.

---

## 5. Giới hạn hiện tại

Số đo trên production ngày 05/11/2026, với GPU `qwen3.8:27b`, đo từ một máy client:

| Hạng mục | Thời gian |
|---|---|
| Chat có tài liệu: chữ đầu tiên | 3,7–4,1 giây |
| Chat không có tài liệu: chữ đầu tiên | khoảng 2,9 giây |
| Chat: tổng cả câu hỏi gợi ý | 7–9 giây |
| Tạo session vấn đáp | khoảng 4,4 giây |
| Một lượt vấn đáp, có câu tiếp theo | 7,6–8 giây |

* **Chưa đạt mục tiêu 3 giây** với chat có tài liệu và vấn đáp.
* **Chấm điểm còn khắt khe, giọng nhận xét còn nặng** (có câu "Ngươi thật vô dụng!"). Prompt sẽ được chỉnh ở BE, API không đổi.
* **Chưa kiểm chứng `CORRECT` trên production:** cả 2 buổi test đều chưa có câu được `CORRECT`, nên FE vẫn phải xử lý đủ 3 giá trị.
* **Mỗi câu chỉ được trả lời một lần.** Trả lời xong là chấm và sang câu mới.
* **Cần GPU server đang bật** khi test. Máy tắt thì các API này báo lỗi.
