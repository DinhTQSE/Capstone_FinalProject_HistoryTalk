# HistoryTalk — AI Service

FastAPI + LangChain service that powers the character roleplay chat feature.  
**Frontend không bao giờ gọi trực tiếp service này** — chỉ BE-Java mới gọi vào khi cần LLM.

---

## Architecture

```
Frontend  ──→  BE-Java (Spring Boot :8080)  ──→  AI Service (FastAPI :8001)
                        │                                    │
                        │  (pre-fills characterData         ▼
                        │   + contextData để tránh       LLM API
                        │   callback ngược lại)     (OpenAI / Gemini)
                        │                           via LangChain
                        ◄────────────────────────────────────
```

**BE-Java gọi AI Service ở 3 thời điểm:**
1. `createSession` → `POST /v1/ai/chat` (greeting message đầu tiên)
2. `sendMessage`   → `POST /v1/ai/chat` (mỗi tin nhắn người dùng gửi)
3. `sendMessage` (tin đầu tiên) → `POST /v1/ai/generate-title` (async, tạo title session)

**Để tránh callback vòng lặp**, BE-Java tự build `characterData` và `contextData` từ entity rồi truyền
thẳng vào request body. AI Service chỉ gọi ngược về Java BE (qua `java_client.py`) khi hai field
này là `null` — tức là khi dùng API trực tiếp để debug/test.

---

## Quick Start

### 1. Create `.env`

```bash
cp .env.example .env
# Fill in OPENAI_API_KEY and JAVA_BACKEND_URL
```

### 2. Install dependencies

```bash
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS / Linux
source .venv/bin/activate

pip install -r requirements.txt
```

### 3. Run

```bash
python main.py
# or
uvicorn app.main:app --reload --port 8001
```

Swagger UI → [http://localhost:8001/docs](http://localhost:8001/docs)

---

## API Reference

### `POST /v1/ai/chat`

Send a user message and receive an in-character AI response.

**Request body**

```jsonc
{
  "characterId": "uuid",          // required
  "contextId":   "uuid",          // required
  "userMessage": "Ngài đã làm gì tại trận Bạch Đằng?",  // required, max 4000 chars
  "messageHistory": [             // optional — previous turns, oldest first
    { "role": "user",      "content": "Xin chào ngài!" },
    { "role": "assistant", "content": "Chào ngươi, ta là Ngô Quyền." }
  ],
  // Optional — skip Java backend calls if caller already has the data
  "characterData": null,
  "contextData":   null
}
```

**Response `200 OK`**

```jsonc
{
  "success": true,
  "data": {
    "message": "Ta đã dùng cọc nhọn cắm xuống lòng sông...",
    "suggestedQuestions": [
      "Chiến thuật của ngài có ảnh hưởng gì đến hậu thế?",
      "Sau trận chiến, ngài đã xây dựng đất nước như thế nào?",
      "Người dân Đại Việt đón nhận chiến thắng ra sao?"
    ]
  }
}
```

---

### `POST /v1/ai/generate-title`

Auto-generates a short session title (≤ 8 Vietnamese words) from the first message exchange.
Call this once after the first assistant reply to set a human-readable session name.

**Request body**

```jsonc
{
  "characterId":            "uuid",
  "contextId":              "uuid",
  "firstUserMessage":       "Ngài đã làm gì tại trận Bạch Đằng?",
  "firstAssistantMessage":  "Ta đã dùng cọc..."
}
```

**Response `200 OK`**

```jsonc
{ "success": true, "data": { "title": "Trận Bạch Đằng và Ngô Quyền" } }
```

---

### `GET /v1/ai/character/{characterId}`  
### `GET /v1/ai/context/{contextId}`

Diagnostic endpoints — proxy a single fetch from the Java backend.  
Use these to verify the AI service can reach the Java backend.

---

### `GET /health`

```jsonc
{ "status": "ok", "llm_provider": "google", "llm_model": "gemini-2.5-flash-lite" }
```

---

## Configuration

All settings are loaded from `.env` (via `pydantic-settings`).

| Variable | Default | Description |
|---|---|---|
| `JAVA_BACKEND_URL` | `http://localhost:8080/Historical-tell` | Java backend base URL |
| `CHARACTER_API_PATH` | `/api/v1/characters` | Character endpoint path |
| `CONTEXT_API_PATH` | `/api/v1/historical-contexts` | Context endpoint path |
| `JAVA_CLIENT_TIMEOUT` | `10.0` | HTTP timeout (seconds) |
| `LLM_PROVIDER` | `openai` | `openai` or `google` |
| `OPENAI_API_KEY` | — | OpenAI secret key |
| `GOOGLE_API_KEY` | — | Google API key (Gemini) |
| `LLM_MODEL` | `gpt-4o-mini` | Model name |
| `LLM_TEMPERATURE` | `0.7` | Sampling temperature |
| `LLM_MAX_TOKENS` | `1024` | Max output tokens |
| `APP_PORT` | `8001` | Port to listen on |
| `DEBUG` | `false` | Enable uvicorn auto-reload |

---

## Switching LLM Provider

### Google Gemini

1. Install the extra package:
   ```bash
   pip install langchain-google-genai
   ```
2. Set in `.env`:
   ```
   LLM_PROVIDER=google
   GOOGLE_API_KEY=AIza...
  LLM_MODEL=gemini-2.5-flash-lite
   ```

---

## Docker

```bash
docker build -t historytalk-ai .
docker run --env-file .env -p 8001:8001 historytalk-ai
```
