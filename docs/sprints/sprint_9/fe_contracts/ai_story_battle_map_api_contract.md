# 📑 FE API CONTRACT: AI SINH KỊCH BẢN KỂ CHUYỆN VÀ LƯỢC ĐỒ TRẬN ĐÁNH

BE đã làm theo đặc tả `ai-story-battle-map-generation.md`. File này chỉ ghi **những gì FE cần biết để nối API thật** và các chỗ khác với đặc tả. Định dạng `steps`, kế hoạch lược đồ, ý nghĩa từng trường và luật kiểm tra giữ đúng như đặc tả.

---

## 1. Khác với đặc tả

| Chỗ | Đặc tả | Thực tế |
|---|---|---|
| Đường dẫn | `/v1/ai/...` | **`/api/v1/ai-authoring/...`** trên Java BE. AI service không mở ra ngoài |
| Quyền | `CONTENT_ADMIN`, `SYSTEM_ADMIN` | **`CONTENT_ADMIN`, `TEACHER`**. Teacher chỉ dùng được cho bối cảnh chung hoặc bối cảnh địa phương của trường mình |
| `tokenUsage` | Ngoài `data` | **Trong `data`**, giống `/exam/question` |
| Lỗi | `{ "detail": "..." }` | Giống mọi API khác của Java: `{ "success": false, "errorCode": 422, "message": "..." }` |
| `contextData`, `characterData` | FE có thể gửi | **Không cần gửi**: BE tự lấy từ DB, kể cả bối cảnh và nhân vật chưa xuất bản. Gửi lên cũng bị ghi đè |
| Id tài liệu trong `sources` | `chunk_12` | `chunk_` + 8 ký tự đầu của id đoạn, ví dụ `chunk_1a2b3c4d`. Cùng một đoạn luôn ra cùng một id |
| Model | Ollama | Kịch bản và lược đồ dùng model chat đang chạy (hiện là `qwen3-next-80b` trên Bedrock). `locate-anchors` dùng `qwen3.8:27b` trên server GPU |

---

## 2. Danh sách API

Tất cả cần `Authorization: Bearer <token>`.

| Method | Path | Đồng bộ | Mô tả |
|---|---|---|---|
| POST | `/api/v1/ai-authoring/story/generate` | Tuỳ `async` | Sinh kịch bản (`mode`: `full`, `continue`, `rewrite`, `quiz`) |
| POST | `/api/v1/ai-authoring/battle-map/plan` | Tuỳ `async` | Lập kế hoạch lược đồ |
| POST | `/api/v1/ai-authoring/battle-map/locate-anchors` | Luôn đồng bộ | Đoán vị trí điểm mốc trên ảnh |
| GET | `/api/v1/ai-authoring/jobs/{jobId}` | | Tiến độ và kết quả của job |
| DELETE | `/api/v1/ai-authoring/jobs/{jobId}` | | Huỷ job, trả `204` |

---

## 3. Sinh kịch bản: `POST /story/generate`

Request đúng như mục A.2 của đặc tả, bỏ `contextData` và `characterData`.

```json
{
  "mode": "full",
  "async": true,
  "contextId": "d99b373f-...",
  "narratorId": "55ae763f-...",
  "characterIds": [],
  "options": { "gradeLevel": "THCS", "targetMinutes": 3, "choiceCount": 1, "quizCount": 1, "tone": null, "notes": "Nhấn mạnh thuỷ triều" },
  "backgrounds": [{ "id": "river-dawn", "name": "Sông lúc bình minh" }]
}
```

* `characterIds` rỗng: mọi nhân vật gắn với bối cảnh đều được xuất hiện. Nhân vật kể (`narratorId`) luôn được thêm vào.
* `backgrounds` bắt buộc, ít nhất 1 cảnh.
* `continue` cần `previousSteps` và `count` (3–5). `quiz` cần `previousSteps`. `rewrite` cần `step` và `instruction`.
* Nhân vật kể dùng đúng cách xưng hô đã cài cho nhân vật (`selfPronoun`, `addressPronoun`, mặc định "ta" / "ngươi").

### Response đồng bộ `200`
```json
{
  "success": true,
  "data": {
    "title": "Ngô Quyền kể chuyện Bạch Đằng",
    "startId": "s1",
    "steps": [ { "id": "s1", "type": "line", "speaker": "narrator", "text": "...", "expression": "neutral", "background": "river-night", "sources": ["chunk_1a2b3c4d"], "next": "s2" } ],
    "warnings": [],
    "tokenUsage": { "promptTokens": 521, "completionTokens": 1830, "totalTokens": 2351 }
  }
}
```

* `warnings` không rỗng: bản nháp vẫn dùng được nhưng còn lỗi sau 1 lần AI tự sửa (ví dụ `"Bước s5: next trỏ tới 's9' không tồn tại"`). FE mở bản nháp và chỉ ra chỗ lỗi.
* Nếu AI trả JSON sai kiểu đến mức không đọc được theo mẫu, `data` là JSON thô model trả về và `warnings` ghi lỗi. FE nên chạy `validateStory` trước khi dựng đồ thị.
* `sources` chỉ còn id có thật, id model tự bịa đã bị bỏ. `sources` rỗng thì gắn nhãn "Cần kiểm chứng".

### Response khi `async: true`: `202`
```json
{ "success": true, "data": { "jobId": "3f2a9c0e8b7d4e1f9a6b5c4d3e2f1a0b" } }
```

---

## 4. Theo dõi job: `GET /jobs/{jobId}`

```json
{
  "success": true,
  "data": {
    "jobId": "3f2a...",
    "status": "running",
    "stage": "generating",
    "progress": 30
  }
}
```

* `status`: `queued`, `running`, `succeeded`, `failed`, `cancelled`.
* `stage`: `retrieving` (10%) → `generating` (30%) → `validating` (70%) → `repairing` (80%, chỉ khi phải sửa) → `done` (100%).
* `succeeded`: có thêm `result`, giống hệt `data` của response đồng bộ.
* `failed`: có thêm `error`, là câu tiếng Việt hiện được cho người dùng, ví dụ `"Bối cảnh chưa có tài liệu tham khảo."`.
* Nên hỏi lại mỗi 2–3 giây.
* Job giữ trong bộ nhớ 30 phút. **AI service khởi động lại thì job mất** và API trả `404`: FE báo "Phiên tạo đã hết hạn" và cho bấm tạo lại.

---

## 5. Lập kế hoạch lược đồ: `POST /battle-map/plan`

Request như mục B.3 của đặc tả. Response như mục B.4, thêm `warnings` và `tokenUsage` trong `data`. Hỗ trợ `async` giống mục 3.

* Ký hiệu hướng hành động có trường `"from"` (đúng tên như đặc tả).
* Trường vị trí không dùng tới trả `null`, ví dụ ký hiệu `stakes` có `"from": null, "to": null, "via": null`.
* Điểm mốc không ký hiệu nào dùng tới bị bỏ, kèm một dòng trong `warnings`.
* Trận trước năm 1900 không có `armor`, `airforce`; trước năm 1400 không có `artillery`. Năm lấy từ `year` hoặc `startYear` của bối cảnh.

---

## 6. Đoán vị trí điểm mốc: `POST /battle-map/locate-anchors`

Request và response như mục B.7 của đặc tả.

```json
{ "success": true, "data": { "anchors": [
  { "id": "cua-song", "found": true, "x": 61.5, "y": 48.2, "confidence": 0.7 },
  { "id": "bien", "found": false }
] } }
```

* `x`, `y` là **% theo chiều rộng, chiều cao ảnh** (0–100), cùng hệ toạ độ với `BattleSymbol.x/y`.
* Gửi `imageUrl` (ảnh đã lưu) hoặc `imageBase64` (ảnh mới upload, có hay không có tiền tố `data:image/...;base64,` đều được), tối đa 10 điểm mốc mỗi lần.
* Chạy trên server GPU, mất vài giây tới khoảng 1 phút. Nên có nút huỷ phía FE.
* Model không chắc thì trả `found: false`, FE yêu cầu staff tự đặt. Kết quả chỉ là gợi ý, luôn cho kéo chỉnh.

---

## 7. Lỗi

| HTTP | Khi nào | `message` |
|---|---|---|
| `400` | `contextId`, `characterIds`, `narratorId` hoặc `jobId` sai định dạng | `contextId không hợp lệ.` ... |
| `403` | Không phải content admin hoặc teacher | `Access Denied` |
| `404` | Bối cảnh, nhân vật không có, đã xoá, hoặc thuộc trường khác | `Không tìm thấy bối cảnh.` |
| `404` | Job không có hoặc đã hết hạn | `Không tìm thấy job hoặc job đã hết hạn.` |
| `422` | Bối cảnh chưa có tài liệu | `Bối cảnh chưa có tài liệu tham khảo.` |
| `422` | Thiếu trường theo `mode`, hoặc giá trị ngoài giới hạn | `Mode continue cần previousSteps.` hoặc `Dữ liệu không hợp lệ: ...` |
| `422` | Ảnh không tải được, không phải ảnh, base64 sai | `Không tải được ảnh bản đồ từ đường dẫn.` ... |
| `503` | Không tra cứu được tài liệu, hoặc server GPU tắt khi đoán vị trí | Message tiếng Việt, hiện được cho người dùng |
| `500` | Model không trả JSON | `Dịch vụ AI đang gặp sự cố. Vui lòng thử lại sau.` |

Gọi đồng bộ có thể mất tới 4 phút với `mode=full`, nên **luôn dùng `async: true` cho `full` và cho lược đồ**.

---

## 8. Việc FE cần làm
- [ ] Đổi dữ liệu giả trong `src/features/story/mock-data.ts` sang các API trên.
- [ ] Nút "Tạo bằng AI": gọi với `async: true`, hỏi job tới khi xong, hiện `stage` và `progress`, có nút huỷ.
- [ ] Hiện `warnings` trong editor và gắn nhãn "Cần kiểm chứng" cho bước có `sources` rỗng.
- [ ] Xử lý `404` của job (hết hạn) và `422` "chưa có tài liệu".
- [ ] Ẩn các nút AI với `SYSTEM_ADMIN` và `SCHOOL_ADMIN`.
- [ ] Lưu kịch bản và lược đồ: phần lưu xuống Java (mục A.9 của đặc tả) **chưa làm**, sẽ có contract riêng.
