# 📚 HistoryTalk - Centralized Project Documentation Hub

Welcome to the centralized documentation repository for **SWD392 HistoryTalk**. This hub organizes all architectural guides, business specifications, database DDL schemas, API contracts, domain-specific backend specifications, AI service specs, and infrastructure monitoring guides in one structured location.

---

## 📂 Documentation Directory Structure

```
docs/
├── README.md (Current Index Hub)
├── architecture/         (System Architecture, Business Specs, Transfer Guides)
├── database/             (PostgreSQL DDL, ERD Checklist, PgVector RAG Schema)
├── api/                  (API Contracts, OpenAPI Specs & Frontend Contracts)
├── java-doc/             (Java Spring Boot Backend Specs - Domain Categorized)
│   ├── auth/             (AUTHENTICATION_SPECIFICATION.md)
│   ├── character-context/(CHARACTER_CONTEXT_SPECIFICATION.md & document-processor-strategy-plan.md)
│   ├── payment/          (PAYMENT_MODULE_SPECIFICATION.md)
│   ├── chat/             (chat-messages-plan.md & chat-authorization-fix-summary.md)
│   ├── quiz/             (QUIZ_MODULE_SPECIFICATION.md)
│   ├── media/            (MEDIA_UPLOAD_SPECIFICATION.md)
│   └── core-refactor/    (SOFT_DELETE_AND_TRASH_SPECIFICATION.md & USER_MANAGEMENT_SPECIFICATION.md)
├── ai-doc/               (Python FastAPI AI Service, RAG, Embeddings & OCR Pipeline)
└── monitoring-doc/       (Prometheus, Grafana, Metrics & System Dashboard)
```

---

## 🏛️ 1. Architecture & Business Specifications (`architecture/`)
Core documents explaining the product vision, domain model, and technical handoff instructions.

| Document | Description | Target Audience |
|---|---|---|
| 📘 [DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/DOMAIN_AND_TECHNICAL_TRANSFER_GUIDE.md) | High-level technical architecture & onboarding guide for new engineers. | All Engineers |
| 📜 [BUSINESS_DOMAIN_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/BUSINESS_DOMAIN_SPECIFICATION.md) | Full business domain specification (Characters, Tiers, Tokens, Quizzes). | All Engineers / PMs |
| 🚀 [BUSINESS_ONBOARDING_GUIDE.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/BUSINESS_ONBOARDING_GUIDE.md) | Business onboarding roadmap & domain conceptual alignment. | New Developers |
| 🗺️ [DATABASE_DESIGN_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/DATABASE_DESIGN_SPECIFICATION.md) | Architectural database design specification & entity relationships. | Backend / DB Devs |
| 🤝 [PROJECT_HANDOFF.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/PROJECT_HANDOFF.md) | System handoff checklist, environment setup, and release notes. | DevOps / Leads |
| 💡 [project-business-overview.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/architecture/project-business-overview.md) | Executive business overview of HistoryTalk ecosystem. | All |

---

## 🗄️ 2. Database & Schema Specifications (`database/`)
Single source of truth DDL files and database migration documentation.

| Document | Description | Type |
|---|---|---|
| 🐘 [schema.sql](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/database/schema.sql) | Consolidated PostgreSQL DDL (`historical_schema`) compiled from Flyway migrations V1 to V25. | PostgreSQL DDL |
| 🧠 [supabase_vector_schema.sql](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/database/supabase_vector_schema.sql) | PgVector storage schema (`vector_chunk`), HNSW index, and RPC function `match_history_chunks`. | PgVector SQL |
| 📋 [erd-alignment-migration-checklist.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/database/erd-alignment-migration-checklist.md) | Alignment checklist between JPA Entities, ERD diagrams, and Flyway SQL. | Migration Tracker |

---

## 🌐 3. API Communication Contracts (`api/`)
Interface specifications for Java Backend REST, FastAPI AI, and Frontend Integration.

| Document | Description | Format |
|---|---|---|
| 📄 [openapi.json](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/api/openapi.json) | Complete OpenAPI 3.0 specification for Java Backend endpoints (`/api/v1/...`). | JSON Spec |
| 📑 [API_CONTRACT.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/api/API_CONTRACT.md) | Comprehensive API endpoint reference guide for Frontend integration. | Markdown |
| 🗑️ [Trash_APIContract.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/api/FE-contract/Trash_APIContract.md) | API contract specification for soft-deleted trash management endpoints. | Markdown |

---

## ☕ 4. Java Backend Specifications (`java-doc/`)

### 📌 Master Summary
* 📗 [implementation-summary.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/implementation-summary.md): Master technical summary of Java Backend modules and implementation history.

### 🔐 Auth Domain (`java-doc/auth/`)
* 🔐 [AUTHENTICATION_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/auth/AUTHENTICATION_SPECIFICATION.md): Consolidated spec for Auth module, JWT dual tokens, RBAC roles, and Google OAuth2 integration.

### 👤 Character & Historical Context Domain (`java-doc/character-context/`)
* 🗿 [CHARACTER_CONTEXT_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/character-context/CHARACTER_CONTEXT_SPECIFICATION.md): Consolidated spec for Character & Context decoupling, draft/publish lifecycle, and 3D model GLTF assets.
* 🛠️ [document-processor-strategy-plan.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/character-context/document-processor-strategy-plan.md): Strategy/Factory pattern design for processing TEXT, MARKDOWN, and PDF documents.

### 💳 Payment & Subscriptions Domain (`java-doc/payment/`)
* 💳 [PAYMENT_MODULE_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/payment/PAYMENT_MODULE_SPECIFICATION.md): Consolidated spec for PayOS payment gateway, webhook fulfillment, double-fulfillment protection, tier upgrades, and billing history.

### 🎯 Quiz Gamification Domain (`java-doc/quiz/`)
* 🎯 [QUIZ_MODULE_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/quiz/QUIZ_MODULE_SPECIFICATION.md): Consolidated spec for Quiz gamification, historical context association, question banks, and attempt scoring.

### 🖼️ Media Upload Domain (`java-doc/media/`)
* 🖼️ [MEDIA_UPLOAD_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/media/MEDIA_UPLOAD_SPECIFICATION.md): Consolidated spec for polymorphic media attachments (`media_metadata`), file validation thresholds, and presigned URLs.

### 🗑️ Core Refactor & User Management (`java-doc/core-refactor/`)
* 🗑️ [SOFT_DELETE_AND_TRASH_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/core-refactor/SOFT_DELETE_AND_TRASH_SPECIFICATION.md): Consolidated spec for soft-delete FK conflict resolution (without `@Where`) and Admin Trash Can lifecycle.
* 👥 [USER_MANAGEMENT_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/core-refactor/USER_MANAGEMENT_SPECIFICATION.md): Consolidated spec for user profile management, avatar updates, monthly token resets, and password reset token security.
* 🏗️ [design-pattern-review.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/java-doc/core-refactor/design-pattern-review.md): Design pattern code review (Factory, Strategy, Builder).

---

## 🤖 5. Python AI Service Specifications (`ai-doc/`)
Architecture and endpoint contracts for the Python FastAPI RAG Service.

| Document | Description | Domain |
|---|---|---|
| 🤖 [PLAN.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/ai-doc/PLAN.md) | AI Service processing pipeline, persona prompt building, RAG, Ollama & Gemini mechanics. | AI Core / RAG |
| ⚡ [api-contract.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/ai-doc/api-contract.md) | FastAPI endpoint contract with Java Backend (`/chat`, `/suggested-questions`, `/generate-title`). | API Contract |
| 🧩 [PDF_RAG_AND_OCR_PIPELINE.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/ai-doc/PDF_RAG_AND_OCR_PIPELINE.md) | Consolidated spec for PDF text extraction, Tesseract OCR enhancement, chunking, vector embedding, and PgVector storage. | PDF / RAG / OCR |

---

## 📊 6. Infrastructure & Monitoring (`monitoring-doc/`)
Guides for Grafana, Prometheus, and backend metrics.

| Document | Description | Tool |
|---|---|---|
| 📊 [MONITORING_AND_GRAFANA_SPECIFICATION.md](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/docs/monitoring-doc/MONITORING_AND_GRAFANA_SPECIFICATION.md) | Consolidated spec for system metrics, Actuator IP security, Prometheus scraping, Grafana dashboards, and local setup guide. | Prometheus / Grafana |
