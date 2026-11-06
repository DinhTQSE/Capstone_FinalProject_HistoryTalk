# 📑 FRONTEND API CONTRACT: CHẾ ĐỘ VẤN ĐÁP & THAY ĐỔI LUỒNG CHAT
> **Sprint:** Sprint 9  
> **Module:** AI Chat (`/chat/*`)  
> **Base URL:** `/Historical-tell/api/v1`  
> **Authentication:** Bearer Token (`Authorization: Bearer <jwt_access_token>`)  
> **Trạng thái BE:** Mọi API trong tài liệu này đã deploy production (cập nhật 06/11/2026). FE chưa tích hợp.  
> **Mục lục cho FE:** Vấn đáp ở mục 1–3 · Lọc ngôn từ ở mục 2.6 · Giọng đọc và chọn giọng nhân vật ở mục 6 · Thay đổi quyền ở mục 7

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
| Câu hỏi gợi ý chỉ còn câu có tư liệu trả lời | `suggestedQuestions` có thể ít hơn 3 câu, kể cả rỗng. FE ẩn khu vực gợi ý khi rỗng |
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

---

## 6. Giọng đọc nhân vật

Giọng đọc do BE tạo bằng VieNeu-TTS tự host, nên **mọi thiết bị nghe cùng một giọng**, và mỗi nhân vật có giọng riêng. Không còn dùng Azure hay giọng của trình duyệt. **Web đã dùng các API này. Mobile vẫn đang gọi Azure trực tiếp**, cần chuyển.

### 6.1. Đọc dạng stream (nên dùng): `POST /chat/tts/stream`

#### Request Body
```json
{ "characterId": "55ae763f-2c8f-4918-bf31-b43566c3c355", "text": "Một câu hoặc một đoạn ngắn." }
```
`text` tối đa 1000 ký tự. Mọi người dùng đã đăng nhập đều gọi được.

#### Response `200`
* `Content-Type: application/octet-stream`: **PCM 16-bit little-endian, mono, 24 kHz**, gửi dần trong lúc BE đang tạo.
* Header `X-Audio-Format: pcm_s16le;rate=24000;channels=1`. Trình duyệt có thể không đọc được header này do CORS, nên mặc định 24000.
* Phát được ngay khi nhận những byte đầu, không phải chờ cả câu.

Web đã làm sẵn, xem `src/lib/character-speech.ts`:
* `openCharacterSpeechStream` mở luồng.
* `createStreamingSpeechPlayer` nhận từng phần, chuyển sang `Float32`, lên lịch nối liền trên `AudioContext`. Câu đầu gom khoảng 0,3 giây trước khi phát để không bị ngắt.
* `splitSpeechSegments(text, { earlyFirst: true })` tách câu, câu đầu ngắt sớm ở dấu phẩy.
* Lúc stream chat, cụm đầu được đọc ngay khi có khoảng 35 ký tự và gặp dấu phẩy, không chờ dấu chấm.

### 6.2. Đọc cả câu dạng WAV: `POST /chat/tts`
Request giống 6.1, trả `audio/wav` 24 kHz mono của cả câu. Dùng khi không phát được dạng stream.

#### Lỗi của 6.1 và 6.2
| HTTP | Khi nào |
|---|---|
| `400` | Thiếu `characterId` hoặc `text`, hoặc `text` quá dài |
| `404` | Nhân vật không tồn tại hoặc đã xóa |
| `5xx` | Server giọng đọc tắt hoặc lỗi: `Dịch vụ giọng đọc đang không khả dụng. Vui lòng thử lại sau.` |

### 6.3. Danh sách giọng: `GET /voices`
**Role:** `CONTENT_ADMIN`, `TEACHER`. Role khác nhận `403`.

```json
{
  "success": true,
  "data": [
    { "id": "Thiện Minh", "gender": "MALE", "region": "NORTH", "style": "Kể chuyện", "recommended": true, "isDefault": true },
    { "id": "Ngọc Linh", "gender": "FEMALE", "region": "NORTH", "style": "Kể chuyện", "recommended": false, "isDefault": false }
  ]
}
```
* 25 giọng: 14 nam, 11 nữ.
* `gender`: `MALE` hoặc `FEMALE`. `region`: `NORTH`, `CENTRAL` hoặc `SOUTH`.
* `id` chính là giá trị gửi vào `voiceId`.

### 6.4. Nghe thử giọng: `POST /voices/preview`
**Role:** `CONTENT_ADMIN`, `TEACHER`.

```json
{ "voiceId": "Ngọc Linh", "text": "Ta là Trưng Trắc." }
```
* `text` không bắt buộc, tối đa 300 ký tự. Để trống thì đọc câu mẫu, câu mẫu được lưu sẵn nên lần sau phát ngay.
* Trả `audio/wav` 24 kHz mono.
* **Lỗi:**
  * `400` khi `voiceId` không có trong danh sách.
  * `400` với tiền tố `INAPPROPRIATE_CONTENT:` khi câu tự nhập có từ tục.
  * `5xx` khi server giọng đọc tắt.

### 6.5. Chọn giọng khi tạo và sửa nhân vật
Thêm trường `voiceId` vào request, và response trả lại `voiceId`:

| API | Ai dùng |
|---|---|
| Tạo, sửa nhân vật hệ thống (`/characters`) | Content admin |
| Tạo, sửa nhân vật địa phương (local content) | Teacher |

* `voiceId` phải là một `id` từ `GET /voices`, sai thì `400`.
* Gửi chuỗi rỗng `""` để về giọng mặc định. Không gửi trường này khi sửa thì giữ nguyên giọng cũ.
* `null` trong response nghĩa là giọng mặc định **Thiện Minh** (nam). **Nhân vật nữ phải chọn giọng nữ.**

#### Gợi ý màn hình
* Ô chọn giọng có lọc theo giới tính và vùng miền, hiện nhãn "Đề xuất" cho `recommended`.
* Nút nghe thử cạnh mỗi giọng, có ô nhập câu tự chọn.
* Ô chọn giọng để trống nghĩa là giọng mặc định.
* Khi server giọng đọc tắt, nút nghe thử hiện thông báo lỗi, nhưng vẫn cho lưu nhân vật.

### 6.6. Việc Frontend còn lại
- [ ] Màn hình tạo và sửa nhân vật cho content admin và teacher: chọn giọng, nghe thử (6.3–6.5).
- [ ] Mobile: thay `speakWithAzure` bằng `POST /chat/tts/stream` hoặc `/chat/tts`, và xóa `EXPO_PUBLIC_AZURE_SPEECH_KEY`.

### 6.7. Giới hạn
* Chỉ phục vụ tốt **một người dùng một lúc**. Nhiều người cùng đọc sẽ chậm.
* Server GPU tắt thì không có giọng đọc, và web không dự phòng bằng giọng của trình duyệt.

---

## 7. Thay đổi quyền: chỉ content admin và teacher được tạo, sửa nhân vật và trận đánh

Từ sprint 9, `SYSTEM_ADMIN` và `SCHOOL_ADMIN` **không còn** tạo hoặc sửa nhân vật, trận đánh và nội dung đi kèm. Gọi các API dưới đây sẽ nhận `403`.

| API | Trước | Bây giờ |
|---|---|---|
| `POST`, `PUT /characters`, gắn và gỡ bối cảnh của nhân vật | 4 role | `CONTENT_ADMIN`, `TEACHER` |
| `DELETE`, `PATCH .../soft-delete` nhân vật | `CONTENT_ADMIN`, `SYSTEM_ADMIN` | `CONTENT_ADMIN` |
| `POST`, `PUT /historical-contexts` | 4 role | `CONTENT_ADMIN`, `TEACHER` |
| `DELETE`, `PATCH .../soft-delete` trận đánh | `CONTENT_ADMIN`, `SYSTEM_ADMIN` | `CONTENT_ADMIN` |
| Tài liệu nhân vật và trận đánh: `POST`, `PUT`, `DELETE` | 4 role | `CONTENT_ADMIN`, `TEACHER` |
| Media nhân vật và trận đánh: upload, xóa | 4 role | `CONTENT_ADMIN`, `TEACHER` |
| PDF: `/documents/{docId}/upload-pdf`, `/documents/pdf/*` | 4 role | `CONTENT_ADMIN`, `TEACHER` |
| Ghim bản đồ chính thức của trận đánh | content admin, system admin, teacher, school admin | `CONTENT_ADMIN`, teacher của trường đó |
| `GET /voices`, `POST /voices/preview` | mới | `CONTENT_ADMIN`, `TEACHER` |

**Không đổi:**
* Mọi API **xem** nhân vật, trận đánh, tài liệu.
* `GET /documents/{docId}/pdf-url`.
* **School admin vẫn duyệt** nội dung địa phương của teacher (`/school-admin/local-contents/.../status`).
* Ghim bản đồ cá nhân của người học.
* Các API `/teacher/local-contents/*` vẫn chỉ dành cho teacher.

**FE cần làm:** ẩn các nút tạo, sửa, xóa, upload và chọn giọng với `SYSTEM_ADMIN` và `SCHOOL_ADMIN`, và xử lý `403` nếu vẫn gọi.
