# Chat Messages & History — Implementation Plan

## Tổng quan luồng FE ↔ BE-Java ↔ BE-Python

```
FE  ──→  BE-Java  ──→  BE-Python (chỉ khi cần LLM)
                  ←──
         ←──
←──

FE không bao giờ gọi trực tiếp BE-Python.
BE-Python được gọi bởi BE-Java ở 3 thời điểm:
  1. createSession   → POST /v1/ai/chat (greeting)
  2. sendMessage     → POST /v1/ai/chat (user message)
  3. sendMessage (tin đầu tiên) → POST /v1/ai/generate-title (async)
```

---

## Hiện trạng (đã có)

**BE-Java — đã implement:**
- `GET  /v1/chat/sessions?contextId=&characterId=`
- `POST /v1/chat/sessions` — chưa có greeting AI
- `DELETE /v1/chat/sessions/{id}`
- Entity: `ChatSession`, `Message` (không có `suggestedQuestions`)
- Repository: `ChatSessionRepository` (thiếu `findAllByUserUid`)

**BE-Python — đã implement đầy đủ, không cần thêm:**
- `POST /v1/ai/chat` → `{message, suggestedQuestions[]}`
- `POST /v1/ai/generate-title` → `{title}`
- `java_client.py` — tự gọi BE-Java để lấy Character + Context nếu không được pre-fill

---

## BE-Python Plan

### Không cần thêm code mới.

### Contract hiện tại

```
POST /v1/ai/chat
Request:
{
  "characterId": "uuid",
  "contextId":   "uuid",
  "userMessage": "string",
  "messageHistory": [
    { "role": "user" | "assistant", "content": "string" }
  ],
  "characterData": { ... } | null,   // optional — BE-Java pre-fill để tránh callback
  "contextData":   { ... } | null    // optional — BE-Java pre-fill để tránh callback
}
Response:
{
  "success": true,
  "data": {
    "message": "string",
    "suggestedQuestions": ["string", "string", "string"]
  }
}

POST /v1/ai/generate-title
Request:
{
  "characterId":           "uuid",
  "contextId":             "uuid",
  "firstUserMessage":      "string",
  "firstAssistantMessage": "string",
  "characterData": { ... } | null,
  "contextData":   { ... } | null
}
Response:
{
  "success": true,
  "data": { "title": "string" }
}
```

### Cách BE-Python tương tác với BE-Java
- `java_client.py` gọi `GET /Historical-tell/api/v1/characters/{id}` và `GET /Historical-tell/api/v1/historical-contexts/{id}`
- Chỉ gọi nếu `characterData` / `contextData` là `null` trong request
- Nếu BE-Java pre-fill sẵn → BE-Python bỏ qua, không callback

### `characterData` schema (fields BE-Python dùng để build prompt)
```json
{
  "characterId": "uuid",
  "name":        "string",
  "title":       "string | null",
  "background":  "string",
  "personality": "string | null",
  "lifespan":    "string | null",
  "side":        "string | null"
}
```

### `contextData` schema
```json
{
  "contextId":   "uuid",
  "name":        "string",
  "description": "string",
  "era":         "string | null",
  "year":        "int | null",
  "location":    "string | null"
}
```

---

## BE-Java Plan

### Các file cần tạo / sửa (theo thứ tự)

---

### Bước 1 — Flyway Migration

**File:** `src/main/resources/db/migration/V4__add_message_suggested_questions.sql`

```sql
ALTER TABLE historical_schema.message
    ADD COLUMN IF NOT EXISTS suggested_questions TEXT;
```

---

### Bước 2 — Sửa `Message.java`

**File:** `entity/chat/Message.java`

Thêm field sau `timestamp`:
```java
@Column(name = "suggested_questions", columnDefinition = "TEXT")
private String suggestedQuestions;
// Chỉ có giá trị với message role=ASSISTANT
// Lưu dạng JSON array string: ["câu 1", "câu 2", "câu 3"]
// Dùng Jackson ObjectMapper để serialize/deserialize khi cần
```

---

### Bước 3 — Tạo `MessageRepository.java`

**File:** `repository/MessageRepository.java`

```java
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByChatSessionSessionIdOrderByTimestampAsc(UUID sessionId);
}
```

---

### Bước 4 — Sửa `ChatSessionRepository.java`

**File:** `repository/ChatSessionRepository.java`

Thêm method:
```java
// Dùng cho GET /chat/history — tất cả session của user, group về phía service
@Query("""
    SELECT cs FROM ChatSession cs
    JOIN FETCH cs.character c
    JOIN FETCH c.historicalContext hc
    WHERE cs.user.uid = :userId
    ORDER BY cs.lastMessageAt DESC NULLS LAST, cs.createDate DESC
    """)
List<ChatSession> findAllByUserUid(@Param("userId") UUID userId);
```

---

### Bước 5 — Tạo `AiServiceClient.java`

**File:** `service/chat/AiServiceClient.java`

Spring `@Service` dùng `RestClient`. Thêm `AI_SERVICE_URL=http://localhost:8001` vào `secretKey.properties`.

**Cách BE-Java tương tác với BE-Python:**
- Truyền `characterData` và `contextData` được build từ `Character` entity và `HistoricalContext` entity
- Mục đích: BE-Python không cần callback lại BE-Java để lấy data → giảm 1 round-trip
- Nếu BE-Python nhận được data này, `java_client.py` sẽ bỏ qua bước HTTP GET sang BE-Java

**Methods:**
```java
// Gọi POST /v1/ai/chat
// Trả về {message, suggestedQuestions[]}
AiChatResult chat(
    String characterId,
    String contextId,
    String userMessage,
    List<MessageHistoryItem> messageHistory,
    CharacterPayload characterData,   // pre-built từ Character entity
    ContextPayload contextData        // pre-built từ HistoricalContext entity
)

// Gọi POST /v1/ai/generate-title (dùng @Async)
String generateTitle(
    String characterId,
    String contextId,
    String firstUserMessage,
    String firstAssistantMessage,
    CharacterPayload characterData,
    ContextPayload contextData
)
```

**Inner data classes / DTOs gửi sang BE-Python:**
```java
// CharacterPayload — map từ Character entity
{
  "characterId": session.character.characterId,
  "name":        session.character.name,
  "title":       session.character.title,
  "background":  session.character.background,
  "personality": session.character.personality,
  "lifespan":    session.character.lifespan,
  "side":        session.character.side
}

// ContextPayload — map từ HistoricalContext entity
{
  "contextId":   context.contextId,
  "name":        context.name,
  "description": context.description,
  "era":         context.era,
  "year":        context.year,
  "location":    context.location
}
```

---

### Bước 6 — Tạo DTOs

**Package:** `dto/chat/`

#### `MessageResponse.java`
```java
String id
String sessionId
String role         // "USER" | "ASSISTANT"
String content
LocalDateTime createdAt
```

#### `GetMessagesResponse.java`
```java
List<MessageResponse> messages
List<String> suggestedQuestions   // từ message ASSISTANT cuối cùng, [] nếu không có
```

#### `SendMessageRequest.java`
```java
@NotBlank String sessionId
@NotBlank @Size(max = 4000) String content
```
> `characterId` và `contextId` được lấy từ session trong DB — không cần FE gửi lên.

#### `SendMessageResponse.java`
```java
MessageResponse userMessage
MessageResponse assistantMessage
List<String> suggestedQuestions
```

#### `ChatHistorySessionItem.java`
```java
String id
String characterId
String characterName
String characterTitle
String characterImage
String contextId
String contextName
String sessionTitle
String lastMessage       // content của message cuối
LocalDateTime lastMessageAt
int messageCount
```

#### `ChatHistoryGroupResponse.java`
```java
String contextId
String contextName
List<ChatHistorySessionItem> sessions
```

---

### Bước 7 — Tạo `MessageService.java`

**File:** `service/chat/MessageService.java`

#### `getMessages(String sessionId, String userId)`
```
1. chatSessionRepository.findBySessionIdAndUserUid(sessionId, userId)
      → 404 nếu không tìm thấy hoặc session không thuộc user này
2. messageRepository.findByChatSessionSessionIdOrderByTimestampAsc(sessionId)
3. Tìm message ASSISTANT cuối cùng → parse suggestedQuestions từ JSON string
4. Return GetMessagesResponse { messages[], suggestedQuestions[] }
   - suggestedQuestions = [] nếu không có ASSISTANT message
```

#### `sendMessage(String userId, SendMessageRequest request)`
```
1. chatSessionRepository.findBySessionIdAndUserUid(sessionId, userId)
      → 404 nếu không tìm thấy / không sở hữu
2. Đếm số user message hiện có → isFirstMessage = (count == 0)
3. Lưu userMessage: Message { role=USER, content, chatSession, isFromAi=false }
4. Load messageHistory = tất cả message trước đó (trước khi lưu user message mới)
      → map thành List<MessageHistoryItem> { role, content }
5. Build characterData từ session.character
   Build contextData  từ session.character.historicalContext
6. Gọi aiServiceClient.chat(characterId, contextId, content, messageHistory, characterData, contextData)
      → AiChatResult { message, suggestedQuestions[] }
7. Serialize suggestedQuestions → JSON string
8. Lưu assistantMessage: Message { role=ASSISTANT, content=ai.message,
                                    isFromAi=true, suggestedQuestions=jsonString }
9. session.setLastMessageAt(now()) → save
10. Nếu isFirstMessage:
      @Async: aiServiceClient.generateTitle(...)
           → session.setTitle(title) → save
11. Return SendMessageResponse { userMessage, assistantMessage, suggestedQuestions[] }
```

---

### Bước 8 — Sửa `ChatSessionService.createSession` — thêm greeting

Sau khi `chatSessionRepository.save(session)`:
```
1. Build characterData từ character entity
   Build contextData  từ character.historicalContext entity
2. Gọi aiServiceClient.chat(
       characterId,
       contextId,
       "Hãy chào và giới thiệu ngắn gọn về bản thân.",
       [],          // messageHistory rỗng
       characterData,
       contextData
   )
3. Lưu greeting message:
   Message { role=ASSISTANT, isFromAi=true, content=ai.message,
             suggestedQuestions=serialize(ai.suggestedQuestions), chatSession=saved }
4. saved.setLastMessageAt(now()) → save
5. Return mapToResponse(saved)
```

---

### Bước 9 — Tạo `ChatHistoryService.java`

**File:** `service/chat/ChatHistoryService.java`

```
1. chatSessionRepository.findAllByUserUid(userId)
2. Group by contextId (dùng Collectors.groupingBy)
3. Mỗi group:
     - Sort sessions theo lastMessageAt DESC (null last)
     - Build ChatHistoryGroupResponse {
         contextId,
         contextName = sessions[0].character.historicalContext.name,
         sessions = List<ChatHistorySessionItem>
       }
4. Sort groups theo max(lastMessageAt) của group, DESC
5. Return List<ChatHistoryGroupResponse>
```

---

### Bước 10 — Cập nhật `ChatController.java`

Thêm 3 endpoint mới:

| Method | URL | Request | Response |
|--------|-----|---------|----------|
| `GET` | `/v1/chat/sessions/{id}/messages` | — | `ApiResponse<GetMessagesResponse>` 200 |
| `POST` | `/v1/chat/messages` | `SendMessageRequest` `@Valid` | `ApiResponse<SendMessageResponse>` 201 |
| `GET` | `/v1/chat/history` | — | `ApiResponse<List<ChatHistoryGroupResponse>>` 200 |

```java
// GET /v1/chat/sessions/{id}/messages
String userId = SecurityUtils.getUserId();
GetMessagesResponse result = messageService.getMessages(id, userId);
return ResponseEntity.ok(ApiResponse.success(result, "Messages retrieved successfully"));

// POST /v1/chat/messages
String userId = SecurityUtils.getUserId();
SendMessageResponse result = messageService.sendMessage(userId, request);
return ResponseEntity.status(201).body(ApiResponse.success(result, "Message sent successfully"));

// GET /v1/chat/history
String userId = SecurityUtils.getUserId();
List<ChatHistoryGroupResponse> history = chatHistoryService.getHistory(userId);
return ResponseEntity.ok(ApiResponse.success(history, "Chat history retrieved successfully"));
```

---

## Checklist

- [ ] V4__add_message_suggested_questions.sql
- [ ] Message.java — thêm `suggestedQuestions`
- [ ] MessageRepository.java — `findByChatSessionSessionIdOrderByTimestampAsc`
- [ ] ChatSessionRepository.java — thêm `findAllByUserUid`
- [ ] AiServiceClient.java — `chat()` + `generateTitle()`
- [ ] secretKey.properties — thêm `AI_SERVICE_URL`
- [ ] MessageResponse.java
- [ ] GetMessagesResponse.java
- [ ] SendMessageRequest.java
- [ ] SendMessageResponse.java
- [ ] ChatHistorySessionItem.java
- [ ] ChatHistoryGroupResponse.java
- [ ] MessageService.java — `getMessages()` + `sendMessage()`
- [ ] ChatHistoryService.java — `getHistory()`
- [ ] ChatSessionService.java — sửa `createSession()` thêm greeting
- [ ] ChatController.java — thêm 3 endpoint
- [ ] `mvn compile` — BUILD SUCCESS
