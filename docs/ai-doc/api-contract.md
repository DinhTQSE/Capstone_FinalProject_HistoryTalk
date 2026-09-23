# API Contract - Document Embedding Integration

## Overview

This document defines the API contract for the PDF text embedding integration between Java backend and AI backend.

---

## 1. Java Backend APIs

### 1.1 Character Documents

#### Create Character Document

**Endpoint**: `POST /api/v1/character-documents`

**Controller**: `CharacterDocumentController`

**Service**: `CharacterDocumentServiceImpl.createDocument()`

**Request Schema** (`CreateCharacterDocumentRequest`):
```java
public class CreateCharacterDocumentRequest {
    private String characterId;    // UUID of the character
    private String title;
    private String content;         // Document content
    private String fileUrl;         // Optional PDF file URL
    // ... other fields
}
```

**Behavior**:
1. Creates new document for character
2. Saves to database
3. Triggers async embedding via `AiServiceClient.processDocumentAsync()`
4. Returns immediately (non-blocking)

**Response**: `CharacterDocumentResponse`

#### Update Character Document

**Endpoint**: `PUT /api/v1/character-documents/{docId}`

**Controller**: `CharacterDocumentController`

**Service**: `CharacterDocumentServiceImpl.updateDocument()`

**Request Schema** (`UpdateCharacterDocumentRequest`):
```java
public class UpdateCharacterDocumentRequest {
    private String title;
    private String content;         // Document content
    // ... other fields
}
```

**Behavior**:
1. Updates document metadata and content
2. If content changed → triggers async embedding via `AiServiceClient.processDocumentAsync()`
3. Returns immediately (non-blocking)

**Response**: `CharacterDocumentResponse`

---

### 1.2 Historical Context Documents

#### Create Historical Context Document

**Endpoint**: `POST /api/v1/historical-documents`

**Controller**: `HistoricalContextDocumentController`

**Service**: `HistoricalContextDocumentServiceImpl.createDocument()`

**Request Schema** (`CreateHistoricalContextDocumentRequest`):
```java
public class CreateHistoricalContextDocumentRequest {
    private String contextId;      // UUID of the historical context
    private String title;
    private String content;         // Document content
    private String fileUrl;         // Optional PDF file URL
    // ... other fields
}
```

**Behavior**:
1. Creates new document for historical context
2. Saves to database
3. Triggers async embedding via `AiServiceClient.processDocumentAsync()`
4. Returns immediately (non-blocking)

**Response**: `HistoricalContextDocumentResponse`

#### Update Historical Context Document

**Endpoint**: `PUT /api/v1/historical-documents/{docId}`

**Controller**: `HistoricalContextDocumentController`

**Service**: `HistoricalContextDocumentServiceImpl.updateDocument()`

**Request Schema** (`UpdateHistoricalContextDocumentRequest`):
```java
public class UpdateHistoricalContextDocumentRequest {
    private String title;
    private String content;         // Document content
    // ... other fields
}
```

**Behavior**:
1. Updates document metadata and content
2. If content changed → triggers async embedding via `AiServiceClient.processDocumentAsync()`
3. Returns immediately (non-blocking)

**Response**: `HistoricalContextDocumentResponse`

---

## 2. AI Backend API

### Process Document for Embedding

**Endpoint**: `POST /v1/ai/documents/process`

**Location**: `src/history_talk_ai/presentation/chat/router.py`

**Request Schema** (`ProcessDocumentRequest`):
```python
class ProcessDocumentRequest(BaseModel):
    doc_id: str        # UUID of the document
    entity_id: str     # UUID of the character or context
    content: str       # Full document content
```

**Response Schema**:
```json
{
  "success": true,
  "message": "Document {doc_id} processed successfully."
}
```

**Behavior**:
1. Chunks content (600 characters, 150 character overlap)
2. Generates embeddings via Ollama (bge-m3 model)
3. Stores vectors in Supabase `vector_chunk` table
4. Async (non-blocking FastAPI)

**Internal Implementation**:
- Location: `src/history_talk_ai/application/chat/service.py` (lines 536-642)
- Uses custom chunking logic with smart punctuation splitting
- Async embedding generation via Ollama with semaphore-based concurrency control
- Bulk insert to Supabase in batches of 100
- Delete-before-insert pattern for upserts

---

## 3. Integration Flow

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  Java Backend   │         │  HTTP REST API  │         │  AI Backend     │
│                 │         │  (FastAPI)      │         │                 │
│ - Create/Update │ ──────▶ │ POST /v1/ai/   │ ──────▶ │ - Chunking      │
│   Document      │  Async  │   documents/    │  Async  │ - Embeddings     │
│ - Async HTTP     │  Call   │   process       │  Call   │ - Vector DB      │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

**Sequence**:
1. Java backend receives `POST /api/v1/character-documents` or `POST /api/v1/historical-documents`
2. Service creates/updates document in database
3. Service calls `AiServiceClient.processDocumentAsync()`
4. `AiServiceClient` sends POST to `http://localhost:8001/v1/ai/documents/process`
5. AI backend processes chunking, embedding, and vector storage
6. Java backend returns immediately (non-blocking)

---

## 4. Java Backend Implementation

### Service: AiServiceClient

**Location**: `src/main/java/com/historytalk/service/chat/AiServiceClient.java`

**Method**: `processDocumentAsync(String docId, String entityId, String content)`

**Implementation** (lines 318-333):
```java
@Async
public void processDocumentAsync(String docId, String entityId, String content) {
    ProcessDocumentRequest request = new ProcessDocumentRequest(docId, entityId, content);
    try {
        log.info("Calling AI service to process document: {}", docId);
        restClient.post()
                .uri("/v1/ai/documents/process")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        log.info("Successfully sent document {} for AI processing", docId);
    } catch (RestClientException e) {
        log.error("AI service /documents/process call failed for doc {}: {}", docId, e.getMessage());
    }
}
```

**Configuration**:
- RestClient configured in constructor with `AI_SERVICE_URL` (default: `http://localhost:8001`)
- Timeout: 60s connect, 180s read
- Basic Auth support (optional)

### Service: CharacterDocumentServiceImpl

**Location**: `src/main/java/com/historytalk/service/character/CharacterDocumentServiceImpl.java`

**Integration**:
- **Create** (line 154): Calls `aiServiceClient.processDocumentAsync()` after document creation
- **Update** (line 203): Calls `aiServiceClient.processDocumentAsync()` if content changed

### Service: HistoricalContextDocumentServiceImpl

**Location**: `src/main/java/com/historytalk/service/historicalContext/HistoricalContextDocumentServiceImpl.java`

**Integration**:
- **Create** (line 153): Calls `aiServiceClient.processDocumentAsync()` after document creation
- **Update** (line 202): Calls `aiServiceClient.processDocumentAsync()` if content changed

---

## 5. Configuration

### Java Backend

**Environment Variables**:
- `AI_SERVICE_URL`: AI backend URL (default: `http://localhost:8001`)
- `AI_SERVICE_USERNAME`: Optional Basic Auth username
- `AI_SERVICE_PASSWORD`: Optional Basic Auth password

**Async Configuration**:
- `@EnableAsync` already enabled in `HistoryTalkApplication.java`
- Uses Spring's default thread pool configuration

### AI Backend

**Environment Variables**:
- `OLLAMA_BASE_URL`: Ollama service URL
- `SUPABASE_URL`: Supabase database URL
- `SUPABASE_KEY`: Supabase API key
- `SUPABASE_SCHEMA`: Database schema name

---

## 6. Vector Database Schema

**Table**: `vector_chunk` in Supabase

**Fields**:
- `chunk_id`: UUID (primary key)
- `doc_id`: UUID (foreign key to document)
- `entity_id`: UUID (foreign key to character/context)
- `content`: text (chunk content)
- `embedding`: vector (embedding vector from bge-m3)
- `sequence_number`: integer (chunk order)

**RPC Function**: `match_history_chunks`
- Used for similarity search with embeddings
- Parameters:
  - `query_embedding`: vector from user question
  - `match_limit`: number of results to return
  - `filter_entity_ids`: list of entity IDs to filter by

---

## 7. Error Handling

### Java Backend
- HTTP errors logged in `AiServiceClient.processDocumentAsync()`
- No retry logic (fire-and-forget pattern)
- Document save succeeds even if embedding fails

### AI Backend
- Errors logged in `process_document` function
- Bulk insert failures raised
- Delete-before-insert pattern may leave partial state on failure

---

## 8. Notes

- **Black-Box Integration**: AI Backend is treated as existing REST API - no modifications required
- **Asynchronous Architecture**: Spring `@Async` + RestClient prevents thread pool exhaustion
- **Contract-Based**: Java calls documented endpoint with defined request/response schema
- **Multilingual Support**: AI backend `bge-m3` model supports Vietnamese and other languages
- **Chunk Configuration**: AI backend uses 600 characters with 150 character overlap
- **Batch Processing**: AI backend handles concurrent embedding generation efficiently
- **Fire-and-Forget**: Java backend does not wait for embedding completion
- **Integration Points**: Only Character and Historical Context document services trigger embedding

