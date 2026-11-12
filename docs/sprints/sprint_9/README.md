# 🏆 SPRINT 9 OVERVIEW & DOCUMENTATION
> **Mục tiêu chính:**  
> 1. Thêm chế độ vấn đáp: nhân vật hỏi, chấm và tính điểm người học.  
> 2. Chuyển LLM sang GPU server riêng và sửa lỗi RAG.  
> 3. Làm luồng chat ổn định hơn khi AI lỗi hoặc nhiều người dùng cùng lúc.

---

## 📂 TÀI LIỆU

* 📦 **[chat_exam_mode_api_contract.md](fe_contracts/chat_exam_mode_api_contract.md):** API, việc FE cần làm và các thay đổi hành vi của chat. **FE đọc file này để tích hợp.**
* 📝 **[exam_streaming_fe_tasks.md](fe_contracts/exam_streaming_fe_tasks.md):** Việc FE web và mobile cần sửa khi vấn đáp chuyển sang chạy chữ.
* 📑 **[b2b_custom_plan_and_quote_flow_api_contract.md](fe_contracts/b2b_custom_plan_and_quote_flow_api_contract.md):** API Contract cho Bảng giá B2B Customize, Live Quote Calculator, Form Đăng ký Báo giá (Humy.ai style) và Cổng System Admin duyệt báo giá khởi tạo trường học.
* 🏫 **[b2b_subscription_pricing_and_quote_specification.md](business_flows/b2b_subscription_pricing_and_quote_specification.md):** Đặc tả chi tiết Unit Economics, bảng giá toàn bộ gói B2C & B2B (đã nới lỏng quota Học sinh/Giáo viên), công thức tính giá Customize B2B Plan, luồng đăng ký `Request a Quote` (tham khảo Humy.ai) và kế hoạch Unit/Integration Test cho Report 5.

---

## ✅ THAY ĐỔI BACKEND ĐÃ DEPLOY

### 1. Chế độ vấn đáp (commit `f311b94`, `1422df3`)
* Session có thêm `mode` (`CHAT` hoặc `EXAM`) và `examQuestionCount`.
* AI service có thêm `POST /v1/ai/exam/question` và `POST /v1/ai/exam/grade` (sau đó `exam/grade` được thay bằng stream, xem mục 9). Các API này chỉ Java gọi, FE không gọi trực tiếp.
* Đáp án và tư liệu của câu đang hỏi không bao giờ trả về client.
* Bỏ việc nhân vật ngẫu nhiên hỏi ngược trong chat thường.
* Migration: `V20261105100000__add_exam_mode_to_chat.sql`.

### 2. GPU server và RAG
* LLM chuyển sang VM Google Cloud (`g4-standard-12`), model chat `qwen3.8:27b`, embedding `bge-m3` (1024 chiều), reranker `bge-reranker-v2-m3`.
* AI service truyền thông qua HTTPS có mật khẩu, tắt thinking của model và dùng role `system`.
* Sửa lỗi RAG không tìm được tài liệu: cột `embedding` đổi sang `vector(1024)`, tạo hàm `historical_schema.match_history_chunks`.
* Migration: `V20261105110000__vector_chunk_search.sql`.
* Câu hỏi một chi tiết dùng 3 đoạn (`RAG_TOP_K`). Câu hỏi tổng hợp như "tóm tắt", "diễn biến" dùng 5 đoạn (`RAG_TOP_K_BROAD`), và nhận diện bằng từ khóa.
* Tìm kiếm chính xác, không dùng chỉ mục HNSW, để lọc theo nhân vật không làm sót đoạn đúng.

### 3. Độ ổn định của chat (commit `1422df3`)
* GPU server tắt thì báo lỗi sau vài giây.
* Không tra cứu được tài liệu thì báo lỗi thay vì trả lời không có căn cứ.
* Không có tài liệu liên quan thì model được yêu cầu nói "Sử sách không ghi chép rõ".
* Lỗi sinh câu hỏi gợi ý không làm hỏng câu trả lời.
* Câu trả lời lỗi hoặc rỗng không được lưu.
* Không giữ kết nối DB trong lúc chờ LLM. Open-in-view được tắt riêng cho `/api/v1/chat/**`.
* Tự gọi lại một lần nếu câu trả lời lẫn chữ Trung (trừ stream).
* RAG bỏ qua tài liệu đã xóa, `INACTIVE` và `PENDING_REVIEW`. Migration: `V20261105120000__rag_skips_unavailable_documents.sql`.

### 4. Lọc ngôn từ không phù hợp
* Thêm `POST /chat/messages/check` để FE kiểm tra ngay khi nhấn gửi. Bị chặn thì popup cảnh báo, tin không vào khung chat.
* API gửi tin nhắn cũng kiểm tra lại: không lưu, không gọi AI, không trừ token, trả `400` với tiền tố `INAPPROPRIATE_CONTENT:`.
* Danh sách từ nằm trong `ChatContentPolicy`. Giữ nguyên dấu tiếng Việt để không chặn nhầm "buổi", "các", "lớn". Từ lịch sử như "giết", "thảm sát" được phép.
* Chỉ từ chối tin nhắn đó, không khóa session.
* Đây là lọc theo danh sách từ, nên chưa bắt được từ viết né kiểu lạ, câu xúc phạm không dùng từ tục, hay nội dung nhạy cảm và lạc đề.

### 5. Giọng đọc nhân vật tự host
* VieNeu-TTS v3 Turbo (Apache-2.0) chạy bằng CPU trên server GPU, sau Caddy ở `/v1/audio/*`.
* Java có `POST /api/v1/chat/tts`, AI service có `POST /v1/ai/tts` để chuyển tiếp sang server giọng đọc.
* Mỗi nhân vật có cột `voice_id`. Migration: `V20261106100000__add_character_voice.sql`.
* Web đã bỏ Azure và giọng của trình duyệt, đọc từng câu và tạo trước âm thanh câu sau.
* Thêm `POST /chat/tts/stream` (PCM 24 kHz phát dần), `GET /voices` và `POST /voices/preview` (chỉ content admin và teacher), và `voiceId` cho nhân vật địa phương của teacher.
* AI service hạ âm thanh từ 48 kHz xuống 24 kHz, Caddy trên EC2 không còn gom dữ liệu trước khi gửi (`flush_interval -1`).

### 6. Quyền nội dung
* Chỉ `CONTENT_ADMIN` và `TEACHER` được tạo, sửa nhân vật, trận đánh, tài liệu, media, PDF, ghim bản đồ chính thức và chọn giọng. Chỉ `CONTENT_ADMIN` được xóa nội dung hệ thống.
* `SYSTEM_ADMIN` và `SCHOOL_ADMIN` chỉ còn quyền xem. School admin vẫn duyệt nội dung địa phương. Chi tiết ở mục 7 của contract.

### 7. Câu hỏi gợi ý bám tư liệu
* Prompt chỉ cho gợi ý câu có câu trả lời trong tư liệu, sau đó từng câu được kiểm tra lại bằng tìm kiếm và reranker. Câu không có đoạn tư liệu nào đủ điểm bị bỏ, nên có thể ít hơn 3 câu.

### 8. Prompt và cách xưng hô
* Nhân vật mặc định xưng "ta" và gọi "ngươi". Content admin và teacher đặt cách xưng riêng qua `selfPronoun`, `addressPronoun`, ví dụ `tôi` / `bạn` cho nhân vật hiện đại. Migration: `V20261106143000__add_character_pronouns.sql`.
* Luật thời đại dựa theo năm sinh và năm mất, nên nhân vật hiện đại không còn bị bắt từ chối điện thoại hay xe hơi.
* Chấm vấn đáp khích lệ hơn, không trừ điểm khi nói thêm ý đúng. Câu hỏi vấn đáp không hỏi ý kiến, luôn ở ngôi thứ nhất.
* Gợi ý câu hỏi chỉ gửi tư liệu và câu trả lời vừa rồi, không gửi lại cả cuộc hội thoại.
* Luật RAG được rút gọn. Tiểu sử chỉ giữ 200 ký tự đầu của mỗi bối cảnh. Bỏ `_esc` làm nhân đôi dấu ngoặc nhọn.
* Token đầu vào vẫn được chia 10 khi trừ token của người dùng.

### 9. Vấn đáp chạy chữ và hỏi nối tiếp câu trả lời
* AI service thay `POST /v1/ai/exam/grade` bằng `POST /v1/ai/exam/turn/stream`: stream lời nhận xét, rồi `---` và câu hỏi tiếp theo. Chỉ Java gọi API này.
* Câu tiếp theo không sinh sẵn. AI tìm tư liệu bằng câu vừa hỏi và câu trả lời của người học, rồi đặt câu hỏi nối tiếp. Trả lời sai thì hỏi một ý liên quan dễ hơn.
* Model ghi `KẾT QUẢ: ...` ở dòng đầu khi chấm, và `ĐÁP ÁN: ...` sau câu hỏi. AI service giữ hai phần này lại, nên kết quả chấm và đáp án không lọt vào stream.
* Java chuyển tiếp chữ cho client, lưu lượt vấn đáp khi stream xong rồi gửi `metadata` (`examVerdict`, `exam`). `POST /chat/messages` dùng chung luồng này nhưng chờ xong mới trả về.
* Câu hỏi đầu tiên vẫn được tạo lúc tạo session, vì lúc đó chưa có câu trả lời nào.
* Web đã chuyển câu trả lời vấn đáp sang API stream. Mobile vẫn cần sửa, xem mục 3 của contract.

### 10. Dùng LLM qua Amazon Bedrock
* `LLM_PROVIDER=external` gọi Chat Completions kiểu OpenAI, dùng cho GPT trên Bedrock. Cấu hình: `EXTERNAL_API_BASE_URL`, `EXTERNAL_API_KEY`, `LLM_MODEL`, `EXTERNAL_REASONING_EFFORT` (mặc định `low`, để trống thì dùng mặc định của model).
* Token đầu vào vẫn chia 10 như Ollama. Stream gửi `stream_options.include_usage` để lấy số token.
* Model từ chối tham số nào (`temperature`, `reasoning_effort`, `response_format`, `max_completion_tokens`) thì AI service bỏ hoặc đổi tham số đó và gọi lại, các lần sau không gửi nữa.
* Embedding `bge-m3`, reranker và giọng đọc vẫn chạy trên VM GPU, nên VM vẫn phải bật.
* Quay lại Ollama: đặt `LLM_PROVIDER=ollama` rồi restart AI service.

### 11. Vấn đáp về cuộc trò chuyện
* Tạo session vấn đáp với `sourceSessionId` của một session chat: câu hỏi lấy từ các đoạn tư liệu nhân vật đã dùng trong cuộc chat đó, câu nối tiếp ưu tiên đoạn gần với câu trả lời của người học.
* Session chat giữ nguyên, vấn đáp xong web có nút "Quay lại phỏng vấn". Chi tiết ở mục 9 của contract.
* Migration: `V20261112140000__add_source_session_to_chat_session.sql`.
* Bỏ được nhãn "Lời nhận xét:" khi model ghi khác "NHẬN XÉT:".

---

## 🧪 KẾT QUẢ KIỂM THỬ TRÊN PRODUCTION (05/11/2026)

| Hạng mục | Kết quả |
|---|---|
| Chat có RAG | Đạt: trả về 3 trích dẫn đúng chủ đề |
| Câu hỏi ngoài tài liệu | Đạt: không bịa số liệu |
| Lẫn tiếng Trung | Không phát hiện trong các câu đã test |
| Luồng vấn đáp | Đạt: chấm, tính điểm, khóa sau câu cuối, mở lại đúng tiến độ |
| Lộ đáp án qua API | Không phát hiện |
| Tấn công prompt khi chấm | Đạt: lần test trước đó vẫn chấm `INCORRECT` |
| 6 tin nhắn cùng lúc | Đạt: API khác vẫn phản hồi, chậm nhất 0,8 giây, không có tin rỗng |
| Chữ đầu tiên dưới 3 giây | **Chưa đạt** với chat có tài liệu (3,7–4,1 giây) |

**Chưa kiểm thử trên production:** GPU tắt giữa chừng, giao diện web. 14 unit test Java ngoài module chat vẫn đang lỗi (document, email, user) và chưa được đối chứng trên bản trước thay đổi.

---

## ⚠️ VIỆC CÒN LẠI

* [ ] FE web và mobile tích hợp vấn đáp và xử lý lỗi `INAPPROPRIATE_CONTENT`.
* [ ] Mobile chuyển giọng đọc sang `POST /chat/tts`, trang quản lý nhân vật thêm chọn giọng, và đặt giọng nữ cho nhân vật nữ.
* [ ] Giảm thời gian tới chữ đầu tiên: rút ngắn prompt, đo log `Chat stream timing`.
* [ ] Đo lại thời gian tới chữ đầu tiên của vấn đáp sau khi chuyển sang stream.
* [ ] Chỉnh prompt chấm: bớt khắt khe, giọng khích lệ, không hỏi kiểu "Theo ngươi".
* [ ] Lời chào cố định, tránh sai sử khi tự sinh.
* [ ] Lọc nội dung nhạy cảm, lạc đề và prompt injection bằng LLM (hiện chỉ có lọc từ tục).
* [ ] Muốn câu tổng hợp dùng trên 5 đoạn thì phải tăng giới hạn của reranker trên VM GPU và số đoạn lấy từ Supabase.
* [ ] So sánh `qwen3.8:27b` với `gemma4:26b` và `gpt-oss:20b`.
* [ ] Tài liệu chưa có bước duyệt: mọi tài liệu mặc định `DRAFT` và vẫn được RAG sử dụng. Cần thêm luồng duyệt nếu muốn chặn.
* [ ] Cập nhật báo cáo: vẫn còn ghi HNSW và schema `public`.
* [ ] Kiểm tra reboot VM GPU: các dịch vụ và warmup chưa được test khi khởi động thật.
