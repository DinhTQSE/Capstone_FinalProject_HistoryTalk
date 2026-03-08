# HistoryTalk AI Service — Plan Summary

## Mục tiêu
Xây dựng một AI service độc lập (FastAPI + LangChain) đóng vai trò **bộ não LLM** cho tính năng chat nhân vật lịch sử.  
Service nhận context tin nhắn + dữ liệu nhân vật/bối cảnh lịch sử, rồi trả về câu trả lời in-character từ LLM.

---

## Kiến trúc tổng thể

> **FE không bao giờ gọi trực tiếp AI Service.** Chỉ BE-Java mới gọi.

```
┌─────────────────────────────────────────────────────────────────┐
│                      Frontend (Next.js)                         │
└───────────────────────────┬─────────────────────────────────────┘
                            │ POST /v1/chat/messages  (BE-Java API)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│              BE-Java  (Spring Boot :8080)                        │
│                                                                  │
│  • Xác thực JWT, lưu Message vào DB                             │
│  • Build characterData + contextData từ entity                  │
│  • Gọi AI Service với data pre-filled                           │
└──────────────────┬──────────────────────────────────────────────┘
                   │ POST /v1/ai/chat  (internal gọi lúc cần LLM)
                   ▼
┌─────────────────────────────────────────────────────────────────┐
│              AI Service  (FastAPI :8001)                         │
│                                                                  │
│  1. Nhận request (characterId, contextId, userMessage, history) │
│  2. Build system prompt tiếng Việt từ characterData/contextData  │
│     (nếu null → tự fetch từ Java BE qua java_client.py)         │
│  3. Gọi LLM qua LangChain (structured output)                   │
│  4. Trả về { message, suggestedQuestions[] }                    │
└──────────────────────────────────┬──────────────────────────────┘
                                   │ LLM API
                                   ▼
                        ┌────────────────────┐
                        │  OpenAI / Gemini   │
                        └────────────────────┘
```

**BE-Java gọi AI Service ở 3 thời điểm:**
1. `createSession` → `POST /v1/ai/chat` — greeting mở đầu
2. `sendMessage`   → `POST /v1/ai/chat` — mỗi tin nhắn
3. `sendMessage` (tin đầu tiên) → `POST /v1/ai/generate-title` (async)

---

## Cấu trúc file

```
history-talk-backend-AI/
├── main.py                   # Entry point — python main.py
├── requirements.txt          # Python dependencies
├── Dockerfile                # Container build
├── .env.example              # Template biến môi trường
├── .gitignore
└── app/
    ├── main.py               # FastAPI app, CORS, đăng ký router
    ├── config.py             # Settings (pydantic-settings, đọc .env)
    ├── models/
    │   ├── character.py      # CharacterData  ← mirror Java CharacterResponse
    │   ├── historical_context.py  # HistoricalContextData ← mirror Java HistoricalContextResponse
    │   └── chat.py           # ChatRequest / ChatResponse / GenerateTitleRequest / …
    ├── services/
    │   ├── java_client.py    # httpx async — gọi Java BE, unwrap ApiResponse.data
    │   ├── prompt_builder.py # Ghép system prompt tiếng Việt từ dữ liệu entity
    │   └── llm_service.py    # LangChain chain, structured output schema
    └── routers/
        └── chat.py           # Tất cả endpoints
```

---

## API Endpoints

| Method | Path | Mô tả |
|--------|------|-------|
| `POST` | `/v1/ai/chat` | **Chat chính** — nhận tin nhắn, trả lời in-character + 3 câu gợi ý |
| `POST` | `/v1/ai/generate-title` | Tạo tiêu đề session ngắn sau tin nhắn đầu tiên |
| `GET`  | `/v1/ai/character/{id}` | Diagnostic — proxy fetch character từ Java BE |
| `GET`  | `/v1/ai/context/{id}` | Diagnostic — proxy fetch context từ Java BE |
| `GET`  | `/health` | Kiểm tra service + LLM provider đang dùng |

---

## Request / Response chính

### `POST /v1/ai/chat`

**Request**
```json
{
  "characterId": "uuid-nhân-vật",
  "contextId":   "uuid-bối-cảnh",
  "userMessage": "Ngài đã làm gì tại trận Bạch Đằng?",
  "messageHistory": [
    { "role": "user",      "content": "Xin chào ngài!" },
    { "role": "assistant", "content": "Chào ngươi, ta là Ngô Quyền." }
  ]
}
```

> Caller có thể truyền thêm `characterData` / `contextData` (pre-fetched) để bỏ qua bước gọi Java BE.

**Response**
```json
{
  "success": true,
  "data": {
    "message": "Ta đã dùng cọc nhọn cắm xuống lòng sông...",
    "suggestedQuestions": [
      "Chiến thuật của ngài ảnh hưởng thế nào đến hậu thế?",
      "Sau trận chiến, ngài xây dựng đất nước ra sao?",
      "Người dân đón nhận chiến thắng thế nào?"
    ]
  }
}
```

### `POST /v1/ai/generate-title`

**Request**
```json
{
  "characterId": "uuid",
  "contextId":   "uuid",
  "firstUserMessage":      "Ngài chiến đấu như thế nào?",
  "firstAssistantMessage": "Ta dùng cọc nhọn..."
}
```

**Response**
```json
{
  "success": true,
  "data": { "title": "Trận Bạch Đằng và Ngô Quyền" }
}
```

---

## Luồng xử lý chi tiết (`POST /v1/ai/chat`)

```
1. Validate request (Pydantic)
          │
2. _resolve_character_and_context()
   ├── Nếu chưa có data → asyncio.gather() gọi Java BE song song
   └── Nếu đã có data   → dùng luôn (skip HTTP call)
          │
3. build_chat_system_prompt(character, context)
   └── Ghép prompt tiếng Việt gồm:
       - Thông tin nhân vật (tên, chức vị, tiểu sử, tính cách, phe phái)
       - Bối cảnh lịch sử (tên, mô tả, thời kỳ, địa điểm, phân loại)
       - 7 quy tắc nhập vai (không tiết lộ là AI, giới hạn kiến thức theo thời đại…)
          │
4. LangChain chain (structured output)
   ├── SystemMessage(system_prompt)
   ├── HumanMessage / AIMessage ... (messageHistory)
   └── HumanMessage(userMessage)
          │
5. LLM trả về _CharacterReply { message, suggestedQuestions[] }
          │
6. Return ChatResponse
```

---

## Data Flow — Dữ liệu từ Java BE

### Character (`GET /api/v1/characters/{id}`)
Các trường dùng để build prompt:

| Trường | Dùng cho |
|--------|----------|
| `name` | Tên nhân vật nhập vai |
| `title` | Chức vị / Danh hiệu |
| `background` | Tiểu sử chi tiết |
| `personality` | Tính cách |
| `lifespan` | Năm sinh – mất |
| `side` | Phe phái / Bên thuộc về |

### HistoricalContext (`GET /api/v1/historical-contexts/{id}`)
Các trường dùng để build prompt:

| Trường | Dùng cho |
|--------|----------|
| `name` | Tên sự kiện / giai đoạn |
| `description` | Mô tả bối cảnh |
| `era` | Thời kỳ (`ANCIENT/MEDIEVAL/MODERN/CONTEMPORARY`) |
| `category` | Phân loại (`WAR/POLITICS/CULTURE/…`) |
| `year` / `startYear` / `endYear` | Thời gian |
| `beforeTCN` | Trước Công Nguyên? |
| `location` | Địa điểm |

---

## Cấu hình môi trường (`.env`)

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `JAVA_BACKEND_URL` | `http://localhost:8080/Historical-tell` | URL Java BE |
| `LLM_PROVIDER` | `openai` | `openai` hoặc `google` |
| `OPENAI_API_KEY` | — | API key OpenAI |
| `GOOGLE_API_KEY` | — | API key Gemini |
| `LLM_MODEL` | `gpt-4o-mini` | Tên model |
| `LLM_TEMPERATURE` | `0.7` | Độ sáng tạo |
| `LLM_MAX_TOKENS` | `1024` | Giới hạn token output |
| `APP_PORT` | `8001` | Port service |

---

## Tích hợp vào Java Backend (việc cần làm phía Java)

Khi Frontend muốn chat, flow đề xuất:

1. Frontend `POST /v1/chat/sessions` → Java BE tạo `ChatSession` → trả `sessionId`
2. Frontend `POST /v1/ai/chat` → AI Service xử lý LLM → trả `message`
3. Frontend `POST /v1/chat/messages` (Java BE) → lưu `userMessage` + `assistantMessage` vào DB
4. Sau tin nhắn đầu tiên: Frontend `POST /v1/ai/generate-title` → lấy `title` → `PATCH /v1/chat/sessions/{id}` cập nhật title

> Các endpoint Java BE chưa có (cần bổ sung): `POST /v1/chat/messages`, `GET /v1/chat/sessions/{id}/messages`

---

## Quick Start

```bash
# 1. Copy và điền API key
cp .env.example .env

# 2. Cài dependencies
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt

# 3. Chạy
python main.py
# → http://localhost:8001/docs
```

---

## Mở rộng trong tương lai

| Tính năng | Hướng triển khai |
|-----------|-----------------|
| Streaming response | FastAPI `StreamingResponse` + LangChain `.astream()` |
| Memory dài hạn | LangChain `ConversationSummaryBufferMemory` hoặc vector store |
| RAG từ document | Embed `CharacterDocument` + `HistoricalContextDocument` vào vector DB, dùng `RetrievalQA` |
| Đa ngôn ngữ | Detect ngôn ngữ input, thêm instruction vào system prompt |
| Rate limiting | FastAPI middleware + Redis |
