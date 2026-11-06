# 🏆 SPRINT 9 OVERVIEW & DOCUMENTATION
> **Mục tiêu chính:**  
> 1. Thêm chế độ vấn đáp: nhân vật hỏi, chấm và tính điểm người học.  
> 2. Chuyển LLM sang GPU server riêng và sửa lỗi RAG.  
> 3. Làm luồng chat ổn định hơn khi AI lỗi hoặc nhiều người dùng cùng lúc.

---

## 📂 TÀI LIỆU

* 📦 **[chat_exam_mode_api_contract.md](fe_contracts/chat_exam_mode_api_contract.md):** API, việc FE cần làm và các thay đổi hành vi của chat. **FE đọc file này để tích hợp.**

---

## ✅ THAY ĐỔI BACKEND ĐÃ DEPLOY

### 1. Chế độ vấn đáp (commit `f311b94`, `1422df3`)
* Session có thêm `mode` (`CHAT` hoặc `EXAM`) và `examQuestionCount`.
* AI service có thêm `POST /v1/ai/exam/question` và `POST /v1/ai/exam/grade`. Hai API này chỉ Java gọi, FE không gọi trực tiếp.
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

### 4. Lọc ngôn từ không phù hợp (chờ deploy)
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
* [ ] Giảm thời gian mỗi lượt vấn đáp: sinh sẵn câu hỏi khi tạo session.
* [ ] Chỉnh prompt chấm: bớt khắt khe, giọng khích lệ, không hỏi kiểu "Theo ngươi".
* [ ] Lời chào cố định, tránh sai sử khi tự sinh.
* [ ] Lọc nội dung nhạy cảm, lạc đề và prompt injection bằng LLM (hiện chỉ có lọc từ tục).
* [ ] Muốn câu tổng hợp dùng trên 5 đoạn thì phải tăng giới hạn của reranker trên VM GPU và số đoạn lấy từ Supabase.
* [ ] So sánh `qwen3.8:27b` với `gemma4:26b` và `gpt-oss:20b`.
* [ ] Tài liệu chưa có bước duyệt: mọi tài liệu mặc định `DRAFT` và vẫn được RAG sử dụng. Cần thêm luồng duyệt nếu muốn chặn.
* [ ] Cập nhật báo cáo: vẫn còn ghi HNSW và schema `public`.
* [ ] Kiểm tra reboot VM GPU: các dịch vụ và warmup chưa được test khi khởi động thật.
