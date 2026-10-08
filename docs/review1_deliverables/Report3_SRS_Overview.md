# CAPSTONE PROJECT REPORT  
## Report 3: Software Requirements Specification (SRS) — System Overview  

**Project Title (English):** HistoryTalk: A SaaS Historical Education Platform Powered by Conversational AI and RAG Technology  
**Academic Program:** Software Engineering (SWP490 / SEP490) — FPT University  
**Document Version:** 1.0  
**Submission Milestone:** Review 1 (Week 3 Deliverable)  

---

### 📑 TABLE OF CONTENTS
1. [Product Overview & System Context](#1-product-overview--system-context)
2. [User Actors & Role Hierarchy](#2-user-actors--role-hierarchy)
3. [Use Case Diagrams & Requirements](#3-use-case-diagrams--requirements)
4. [System Functional Overview (8 Core Modules)](#4-system-functional-overview-8-core-modules)
5. [Non-Functional Requirements](#5-non-functional-requirements)

---

### 1. PRODUCT OVERVIEW & SYSTEM CONTEXT

HistoryTalk is designed as a decoupled microservices system. The platform comprises a **Java Spring Boot Core Service** for business operations and a **Python FastAPI Microservice** dedicated to high-performance RAG vector search, reranking, and streaming LLM roleplay.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             SYSTEM ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  Frontend Client (Web / Next.js & React)                                    │
└──────┬──────────────────────────────────────────────────────┬───────────────┘
       │ REST / JSON                                          │ SSE Stream / REST
       ▼                                                      ▼
┌──────────────────────────────┐              ┌──────────────────────────────┐
│  Java Backend (Spring Boot)  │              │    Python AI Microservice    │
│  - Auth & Security (JWT)     │              │    (FastAPI Async)           │
│  - B2B School & Class Module │              │  - Vector Search (PgVector)  │
│  - Payment (PayOS)           │              │  - Kaggle Cross Reranker     │
│  - PDF/OCR Document Processor│              │  - SSE LLM Roleplay Stream   │
│  - Analytics & Dashboards    │              │  - Semantic Chunking Engine  │
└──────────────┬───────────────┘              └──────────────┬───────────────┘
               │                                             │
               ▼                                             ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            SUPABASE & DATABASE                              │
│  - Relational Schema: PostgreSQL (Users, Classes, Payments, Quizzes)       │
│  - Vector Storage: Supabase PgVector (vector_chunk table & match RPC)      │
│  - Unstructured Storage: Supabase Storage Bucket (documents/media)          │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 2. USER ACTORS & ROLE HIERARCHY

HistoryTalk categorizes users into 6 distinct roles across B2C and B2B SaaS workflows:

```
                  ┌──────────────────────────────┐
                  │         SYSTEM_ADMIN         │
                  └──────────────┬───────────────┘
                                 │ Provisions
                                 ▼
                  ┌──────────────────────────────┐
                  │         SCHOOL_ADMIN         │
                  └──────────────┬───────────────┘
                                 │ Imports
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
  ┌──────────────────────────────┐┌──────────────────────────────┐
  │           TEACHER            ││        SCHOOL_STUDENT        │
  └──────────────────────────────┘└──────────────────────────────┘

  ┌──────────────────────────────┐┌──────────────────────────────┐
  │        CUSTOMER (B2C)        ││        CONTENT_ADMIN         │
  └──────────────────────────────┘└──────────────────────────────┘
```

---

### 3. USE CASE DIAGRAMS & REQUIREMENTS

#### 3.1. Primary System Use Cases
* **UC-01 Authentication & Onboarding:** Login via Email/Password or Google OAuth2; first-time password reset for school accounts.
* **UC-02 School & Class Administration:** School Admin batch-imports teachers and students via 2-sheet Excel files; system auto-provisions accounts and class assignments.
* **UC-03 Homework Assignment Distribution:** Teacher creates assignments comprising Context Reading, AI Character Dialogue, and Timed Quiz Tasks.
* **UC-04 AI Character Roleplay:** Student/Customer initiates streaming SSE conversation with historical figure, backed by PgVector retrieval.
* **UC-05 Automated Quiz Assessment:** Student completes timed quiz; system evaluates responses against answer keys and records `QuizSession`.
* **UC-06 Token & Subscription Management:** Customer purchases token packs or upgrades tier via PayOS.

---

### 4. SYSTEM FUNCTIONAL OVERVIEW (8 CORE MODULES)

#### Module 1 — Authentication & Authorization
* Multi-role authentication with JWT tokens (`JwtAuthenticationFilter`).
* Google OAuth2 integration for B2C Customers.
* Automated account provisioning with prefix-based usernames (`{school_code}_hs_{ma_hs}`) preventing multi-tenant collision.

#### Module 2 — SaaS B2B Classroom Management
* **School Admin Portal:** School onboarding, 2-sheet Excel batch import (`Danh_Sach_Giao_Vien` and `Danh_Sach_Hoc_Sinh_Va_Lop`), class roster management.
* **Teacher Portal:** Create classrooms, assign multi-task homework (`Assignment`), track student completion rates.
* **Local Content:** Teachers create school-specific historical contexts and local quizzes visible only to their school's students.

#### Module 3 — Interactive Learning & AI Chat
* First-person character roleplay ("*Ta*", "*Trẫm*", "*Tôi*") enforcing historical persona boundaries.
* Server-Sent Events (SSE) streaming for real-time response rendering.
* Interactive Spatio-Temporal Map displaying historical events by timeline year and geographic coordinates.

#### Module 4 — Token Economy & Daily Allowances
* Daily token reset for Free customers and School students upon daily login.
* Token deduction based on message character length and interaction complexity.
* Chat restriction when balance reaches zero, prompting upgrade options.

#### Module 5 — Progress Tracking & Analytics Dashboards
* **Student View:** "My Assignments" portal displaying task completion status (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `LATE_SUBMITTED`).
* **Teacher View:** Class gradebook and student engagement metrics (AI message volume and quiz score distributions).
* **System Admin View:** Global JVM health, user activity trends, top wrong quiz questions, and revenue analytics.

#### Module 6 — Payment Gateway Integration (B2C)
* Integration with PayOS online payment gateway.
* Support for Tier subscriptions (Free, Plus, Pro) and one-time token packages.
* Asynchronous fulfillment reconciliation scheduler ensuring reliable token credit upon webhook receipt.

#### Module 7 — Global Content Management
* Content Admin CRUD operations for global historical contexts, character bio profiles, reference documents, and quiz question banks.
* Draft, Published, and Soft-Delete (Trash) lifecycle state management.

#### Module 8 — Mobile Application Roadmap
* Long-term architecture alignment for Flutter/React Native mobile applications consuming the REST API Gateway.

---

### 5. NON-FUNCTIONAL REQUIREMENTS

| NFR Category | Requirement Specification |
| :--- | :--- |
| **Performance & Latency** | Time-To-First-Token (TTFT) for streaming AI chat $< 1.5$s; REST API response time $< 300$ms for 95% of requests. |
| **Historical Accuracy** | RAG accuracy backed by Semantic Chunking and Kaggle Cross-Encoder Reranker; zero tolerance for hallucinated historical dates. |
| **Security & Isolation** | Strict Multi-tenant data isolation by `school_id`; BCrypt password hashing; JWT expiration and refresh token rotation. |
| **Academic Integrity** | Quiz tests serve as the primary grading anchor; AI chat spam filters enforce valid message lengths ($\ge 10$ chars). |
| **Availability & Scale** | HikariCP connection pooling; Flyway database migration versioning (`V1`–`V26`); stateless REST APIs enabling horizontal scale. |
