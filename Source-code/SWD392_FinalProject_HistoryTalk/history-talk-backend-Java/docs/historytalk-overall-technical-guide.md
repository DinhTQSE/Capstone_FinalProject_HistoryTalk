# HistoryTalk - Overall Technical Onboarding Guide
**Document Code:** TECH-GUIDE-OVERALL-01  
**Target Audience:** New Developers Onboarding HistoryTalk  
**Architecture:** Dual-Backend System (Java Spring Boot 3 + Python FastAPI AI Service)  
**Version:** 2.0  

---

## 1. SYSTEM ARCHITECTURE & ECOSYSTEM

HistoryTalk is built on a **supporting service dual-backend model** that separates core business logic from computationally intensive AI roleplay and vector operations.

```
[ Frontend (Web / Mobile) ]
           │
           │ REST API + Bearer JWT
           ▼
[ Java Spring Boot Backend (Port 8080) ] ◄──► [ PostgreSQL (historical_schema) ]
           │
           │ Internal REST Calls (Asynchronous / Synchronous)
           ▼
[ Python FastAPI AI Service (Port 8001) ] ◄──► [ Supabase Vector DB (PgVector) ]
           │
           ▼
[ LLM Providers (Gemini / OpenAI / Ollama) ]
```

### 1.1 Service Responsibilities
* **Java Spring Boot Backend (Port 8080)**:
  * Primary gatekeeper and single source of truth for business logic.
  * User authentication (JWT, OAuth2), Authorization (RBAC).
  * Domain management: Historical Contexts, Characters, Quizzes, Gamification (XP, Streaks, Quests), PayOS Payments.
  * Chat session management, token quota enforcement, message history persistence.
  * Direct client interaction (Frontend NEVER communicates with Python AI Service directly).
* **Python FastAPI AI Service (Port 8001)**:
  * "Computational Brain" for historical roleplay and RAG operations.
  * Dynamic System Prompt Builder enforcing character personas, historical eras, and linguistic rules.
  * RAG Pipeline: Text extraction, chunking, embedding generation using `bge-m3`, vector similarity search in Supabase Vector DB.
  * LLM abstraction supporting Google Gemini, OpenAI, and local Ollama models.

### 1.2 Storage Infrastructure
* **PostgreSQL (`historical_schema`)**: Core relational database for users, characters, contexts, sessions, quizzes, transactions, and SaaS school data.
* **Supabase PgVector (`vector_chunk`)**: Vector storage utilizing HNSW indexing and Cosine distance metric for fast semantic search.
* **In-Memory Blacklist**: JWT revocation and token invalidation management.

---

## 2. JAVA SPRING BOOT BACKEND ARCHITECTURE & PATTERNS

### 2.1 Package Conventions
The Java backend enforces a strict layered package structure:
```
com.historytalk
├── config          // Spring Security, Swagger, CORS, PayOS, REST Template configs
├── controller      // REST Endpoints handling HTTP requests/responses
├── dto             // Request & Response Data Transfer Objects (Records / Lombok)
├── entity          // JPA Entities mapped to historical_schema tables
├── enums           // System Enums (UserRole, EventEra, EventCategory, etc.)
├── exception       // Global Exception Handler (@ControllerAdvice)
├── repository      // Spring Data JPA Repositories
├── security        // JwtTokenProvider, JwtAuthenticationFilter, UserPrincipal
├── service         // Business logic interfaces & ServiceImpl classes
└── util            // Password generator, String utilities, Strategy classes
```

### 2.2 Authentication & Security Architecture
* **Stateless JWT Authentication**: Access tokens carry user claims (`userId`, `username`, `role`, `is_first_login`, `school_id`).
* **Role-Based Access Control (RBAC)**: Unified `UserRole` enum:
  * `GUEST`, `CUSTOMER`, `STAFF`, `CONTENT_ADMIN`, `ADMIN`, `SYSTEM_ADMIN`, `SCHOOL_ADMIN`, `TEACHER`, `SCHOOL_STUDENT`.
* **Password Encryption**: BCrypt hashing via `PasswordEncoder`.

### 2.3 Soft-Delete & Database Strategy
To prevent `EntityNotFoundException` crashes during Hibernate lazy loading and maintain full audit trails:
* **Manual Filtering Pattern**: `@Where` and `@SQLDelete` annotations are explicitly removed from JPA Entities.
* **`deleted_at` Audit Field**: Soft-deleted entities retain a non-null `deleted_at` timestamp.
* **Service-Layer Filtering**: 
  * Regular User queries filter out deleted records (`deletedAt IS NULL`).
  * Admin / Staff queries can view soft-deleted records for inspection, restoration, or permanent purge (`SystemTrashController`).

### 2.4 Document Processing Strategy & Factory Pattern
For historical knowledge base ingestion, document sanitization uses Strategy & Factory design patterns:
* `DocumentProcessorStrategy`: Interface defining `extractAndSanitizeText()`.
* Format Implementations: `MarkdownDocumentProcessor`, `PdfDocumentProcessor`, `PlainTextDocumentProcessor`.
* `DocumentProcessorFactory`: Instantiates the appropriate strategy based on file extension/MIME type.

---

## 3. PYTHON FASTAPI AI SERVICE ARCHITECTURE

### 3.1 Roleplay Prompt Engine
The AI Service constructs structured prompts dynamically based on character metadata and historical contexts:
* **Persona Enforcement**: Integrates character life story, traits, and worldview.
* **Linguistic Rules**: Mandates era-appropriate honorifics and pronouns (e.g., *Ta - Ngươi*, *Trẫm - Khanh*, *Lão thần*).
* **Proactive Engagement**: AI prompts instruct LLMs to ask open-ended historical questions in ~30% of responses.
* **Suggested Questions**: AI appends 3 relevant follow-up questions (`suggestedQuestions`) in structured JSON output for user guidance.

### 3.2 Asynchronous RAG Pipeline
1. **Document Ingestion**: Java Backend receives document upload $\rightarrow$ calls Python AI Service asynchronously via REST (`POST /api/v1/ai/documents/embed`).
2. **Chunking & Embedding**:
   * Text is split into overlapping chunks (e.g., 500 characters, 50-character overlap).
   * High-dimensional embeddings are generated using the `bge-m3` model.
3. **Vector Storage**: Embeddings are persisted in Supabase Vector DB (`vector_chunk` table) with metadata (`character_id`, `context_id`, `document_id`).
4. **Context Retrieval**: During a chat session, user query embeddings are matched against `vector_chunk` using Cosine similarity (`match_vector_chunks` database function).

---

## 4. INTEGRATIONS & HARDENING STANDARDS

### 4.1 PayOS Payment Gateway Integration
* **Webhook Fulfillment**: Payment status updates are received via PayOS Webhooks.
* **HMAC Verification**: Webhook payloads are verified against PayOS Checksum Key before modifying user tier or token quota.
* **Asynchronous Activation**: Prevents double-fulfillment and ensures transaction idempotency.

### 4.2 Multi-Tenant SaaS Integration (Module 2)
* **School Code Convention**: `{MA_TINH}_{CAP_HOC}_{TEN_RUT_GON}` (e.g., `HCM_THPT_LHP`).
* **First-Login Security Gate**:
  * Accounts initialized with default passwords carry `is_first_login = true`.
  * `JwtAuthenticationFilter` intercepts requests with `is_first_login == true` and restricts authorities to `ROLE_PRE_CHANGE_PASSWORD`.
  * Users are blocked from all operational endpoints until `POST /api/v1/auth/change-password` succeeds.

---

## 5. LOCAL DEVELOPMENT & ENVIRONMENT SETUP

### 5.1 Docker Compose Deployment
The entire stack can be launched locally via Docker Compose:
```bash
docker-compose up -d
```
Services included:
* `postgres_db`: PostgreSQL on Port 5432.
* `historytalk_backend`: Spring Boot App on Port 8080.
* `historytalk_ai`: FastAPI Service on Port 8001.

### 5.2 Key Configuration Files
* Java Backend: `application.properties` (Database credentials, JWT secret, PayOS keys, AI service URL).
* Python AI Service: `.env` (Gemini API Key, Supabase Vector URL & Key, Ollama host).
