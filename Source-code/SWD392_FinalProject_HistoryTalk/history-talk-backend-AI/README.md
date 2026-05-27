# HistoryTalk — AI Service

FastAPI + LangChain service that powers the character roleplay chat feature and Retrieval-Augmented Generation (RAG).  
**Frontend không bao giờ gọi trực tiếp service này** — chỉ BE-Java hoặc BE-Express mới gọi vào khi cần LLM hoặc Indexing tài liệu.

---

## Architecture

```text
Frontend  ──→  BE-Java / BE-Express  ──→  AI Service (FastAPI :8001)
                         │                                     │
                         │ (pre-fills characterData         ▼
                         │  + contextData)               Ollama Server
                         │                               (qwen2.5:14b)
                         │                                     │
                         │                                     ▼
                         ◄────────────────────────────── Supabase (pgvector)
```

**BE gọi AI Service ở các thời điểm chính:**
1. `sendMessage` → `POST /v1/ai/chat` (Tạo câu trả lời từ AI kèm RAG context)
2. `sendMessage` (tin đầu tiên) → `POST /v1/ai/generate-title` (Tự động tạo title cho session)
3. `createDocument` / `updateDocument` → `POST /v1/ai/documents/process` (Băm văn bản và nhúng vector vào Supabase)
4. `deleteDocument` → `DELETE /v1/ai/documents/{doc_id}` (Xóa vector chunks khỏi DB)

---

## Quick Start

### 1. Create `.env`

```bash
cp .env.example .env
# Cấu hình OLLAMA_BASE_URL, SUPABASE_URL, và SUPABASE_KEY
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
# hoặc
uvicorn history_talk_ai.main:app --reload --port 8001 --app-dir src
```

Swagger UI → [http://localhost:8001/docs](http://localhost:8001/docs)

---

## API Reference

### 1. Chat & Inference

#### `POST /v1/ai/chat`

Gửi tin nhắn của người dùng và nhận câu trả lời AI (đã tự động tìm kiếm RAG Context).

**Request body**
```jsonc
{
  "characterId": "uuid",          // required
  "contextId":   "uuid",          // required
  "userMessage": "Lương khô đặc biệt là gì?",  // required
  "messageHistory": [             // optional — Lịch sử chat (ưu tiên tối đa 5-10 tin gần nhất)
    { "role": "user",      "content": "Xin chào ngài!" },
    { "role": "assistant", "content": "Chào ngươi." }
  ],
  "characterData": null,          // optional — prefilled from Java
  "contextData":   null           // optional — prefilled from Java
}
```

**Response `200 OK`**
```jsonc
{
  "success": true,
  "data": {
    "message": "Đội hậu cần đã phát minh ra gạo nếp rang chín...",
    "suggestedQuestions": [
      "Chiến thuật của ngài có ảnh hưởng gì đến hậu thế?",
      "Người dân Đại Việt đón nhận chiến thắng ra sao?"
    ],
    "tokenUsage": {
      "promptTokens": 750,
      "completionTokens": 100,
      "totalTokens": 850
    }
  }
}
```

#### `POST /v1/ai/generate-title`

Tạo tự động tiêu đề ngắn gọn (≤ 8 từ) cho Chat Session.

---

### 2. RAG & Document Processing

#### `POST /v1/ai/documents/process`

Chuyển đổi một đoạn Text/Tài liệu thành các Vector Chunks và lưu vào bảng `vector_chunk` trên Supabase.

**Request body**
```jsonc
{
  "doc_id": "uuid của document",
  "entity_id": "uuid của character hoặc context",
  "content": "Nội dung lịch sử dài cần lưu trữ..."
}
```

**Response `200 OK`**
```jsonc
{
  "success": true,
  "message": "Document 852ad0d7... processed successfully."
}
```

#### `DELETE /v1/ai/documents/{doc_id}`

Xóa toàn bộ các Vector Chunks của một Document cụ thể khỏi Supabase.

---

## Configuration (`.env`)

Tất cả cấu hình được load qua `.env` (sử dụng `pydantic-settings`).

| Variable | Ví dụ | Mô tả |
|---|---|---|
| `JAVA_BACKEND_URL` | `http://localhost:8080/Historical-tell` | Java backend base URL |
| `OLLAMA_BASE_URL` | `http://109.237.69.169/api/chat` | URL của Ollama Server |
| `OLLAMA_USERNAME` | `mtn_ai` | (Nếu có Basic Auth) |
| `OLLAMA_PASSWORD` | `***` | (Nếu có Basic Auth) |
| `LLM_MODEL` | `qwen2.5:14b` | Model name sử dụng trên Ollama |
| `SUPABASE_URL` | `https://vwy...supabase.co` | Supabase Project URL |
| `SUPABASE_KEY` | `eyJhbG...` | Supabase Service Role Key |
| `APP_PORT` | `8001` | Port chạy AI Service |

---

## Docker Deployment

```bash
docker build -t historytalk-ai .
docker run --env-file .env -p 8001:8001 historytalk-ai
```
