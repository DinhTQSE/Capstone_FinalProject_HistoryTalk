# Report 4 (Software Design Document - SDD) Implementation Plan for Review 2

**Date:** 2026-10-20  
**Scope:** Capstone Review 2 — Report 4 (Software Design Document) in LaTeX Overleaf + PlantUML (`.puml`) Source Files & Rendered High-Resolution Figures  
**Target Deliverables:**
1. **PlantUML Source Files (`.puml`) in Git Repository:** `docs/report4_sdd/uml/*.puml` (27 diagrams covering System Architecture, Package Diagrams, Logical ERD, Detailed Design Class & Sequence Diagrams, State Machine Diagrams, Activity Diagrams, and AI/RAG Pipeline Architecture).
2. **Rendered PNG Diagrams (`figures/*.png`):** Automatically compiled from `.puml` via Kroki/PlantUML for direct inclusion (`\includegraphics`) in Overleaf.
3. **LaTeX Overleaf Project (`overleaf/` & `Report4_SDD_Overleaf.zip`):** Complete, modular LaTeX codebase (`main.tex` + `sections/*.tex` + `figures/*.png`) formatted to match FPT University Capstone Report 4 / Report 7 (Part IV) standards and `SWD392___History_Talk.pdf`.
4. **Repository Markdown Mirror (`docs/report4_sdd/Report4_Software_Design_Document.md`):** Repository-readable SDD linking all `.puml` files and tables so the Review 2 AI Self-Review Agent (Form BM02) can verify 100% of the design against the codebase and database schema.

---

## 1. Document Structure & Alignment with Review 2 Guidelines

Following `Report7_Final_Project_Report.docx.pdf` (Part IV: Software Design Document) and `SWP490_R2_Huong-dan-chuan-bi-Review-2_1.pdf`:

### Section 1: System Design (`sections/01_system_design.tex`)
- **1.1 System Architecture**
  - `arch_system_overview.puml` / `.png`: End-to-end microservices architecture (Next.js Web Client, Java Spring Boot 3.2.5 Core Backend, Python FastAPI RAG AI Service, PostgreSQL 16 + Supabase PgVector, Cloudflare R2 / Supabase Storage, PayOS Payment Gateway, Google OAuth2, SMTP Mail Server, Ollama / External LLM & BGE-M3 Embedding, Kaggle Cross-Encoder Reranker, Apache PDFBox + Tess4J OCR, Prometheus + Grafana Monitoring).
  - Architectural tiers and communication protocols breakdown.
- **1.2 Package Diagram**
  - **1.2.1 Core Backend Packages (Java Spring Boot):** `pkg_backend_java.puml` / `.png` + Package Descriptions table (`controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `security`, `config`, `exception`, `utils`).
  - **1.2.2 AI & RAG Microservice Packages (Python FastAPI):** `pkg_backend_ai.puml` / `.png` + Package Descriptions table (`presentation.chat`, `application.chat`, `application.prompting`, `dataaccess.java_backend`, `common.config`, `common.errors`).
  - **1.2.3 Frontend Web Application Packages (Next.js / React):** `pkg_frontend_web.puml` / `.png` + Package Descriptions table (`Presentation`, `Features`, `Infrastructure`, `Core / State`).

### Section 2: Database Design (`sections/02_database_design.tex`)
- **2.1 Logical & Physical Entity-Relationship Diagram (ERD)**
  - `erd_full_overview.puml` / `.png`: Complete Logical ERD of all 32 database tables across 6 bounded domains.
  - `erd_core_and_saas.puml` / `.png`: Domain 1 & 2 (Multi-Tenant SaaS School, Classroom, User, Local Content, Historical Context, Character, Document, Media, Interactive Map).
  - `erd_learning_and_billing.puml` / `.png`: Domain 3, 4, 5 & 6 (AI Chat, Vector Chunk, Quiz & Question Bank, Classroom Assignments, Gamification, Tier Subscriptions & PayOS Billing).
- **2.2 Table Descriptions (32 Tables Summary with PK & FK)**
  - Standardized table (`No | Table | Description with Primary keys & Foreign keys`) matching `Report7` pages 290–295 for all 32 tables.
- **2.3 Detailed Data Dictionary**
  - Column-by-column specifications (`Name | Data Type | Length / Constraints | Description`) for all 32 tables synchronized 100% with JPA `@Entity` classes and Flyway migrations `V1`–`V38`.

### Section 3: Detailed Design (`sections/03_detailed_design.tex`)
Structured into **7 core feature subsystems**, each containing a **Class Diagram (`.puml` + `.png`)**, a **Class Description Table (`Class | Description`)**, and **Sequence Diagrams (`.puml` + `.png`)**:
- **3.1 Authentication, Account Lifecycle & Multi-Tenant RBAC**
  - Class Diagram: `cls_01_auth_rbac.puml` (`AuthController`, `AdminUserController`, `UserController`, `AuthService`, `AuthServiceImpl`, `GoogleOAuthServiceImpl`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `SchoolSubscriptionGuardInterceptor`, `UserRepository`, `User`).
  - Sequence Diagrams: `seq_01_login_oauth_jwt.puml` (Login, Google OAuth2 & Dual-Token Refresh), `seq_02_password_reset_and_lifecycle.puml` (Forgot/Reset Password & Account Activation/Deactivation).
- **3.2 B2B SaaS School, Classroom & Token Quota Allocation**
  - Class Diagram: `cls_02_school_classroom_token.puml` (`AdminSchoolController`, `SchoolLifecycleAndTokenController`, `ClassroomController`, `ClassroomStudentController`, `SchoolAdminQuotaController`, `SchoolManagementServiceImpl`, `AcademicLifecycleServiceImpl`, `ClassroomServiceImpl`, `StudentImportServiceImpl`, `DailyTokenAllowanceServiceImpl`, `TokenBoostRequestServiceImpl`, `School`, `Classroom`, `ClassStudent`, `TokenBoostRequest`).
  - Sequence Diagrams: `seq_03_school_provisioning_and_import.puml` (School Onboarding & Bulk CSV Student Enrollment), `seq_04_token_boost_and_allowance.puml` (Daily Student Token Quota Check & Token Boost Request Approval).
- **3.3 Historical Content, Character, Document OCR & Interactive Map**
  - Class Diagram: `cls_03_content_character_map.puml` (`HistoricalContextController`, `CharacterController`, `DocumentFileController`, `PdfExtractionController`, `MapPinController`, `MapFocusController`, `LocalContentController`, `HistoricalContextServiceImpl`, `CharacterServiceImpl`, `DocumentContentServiceImpl`, `PdfExtractionServiceImpl`, `OcrServiceImpl`, `LocalContentServiceImpl`, `MapPinServiceImpl`, `HistoricalContext`, `Character`, `Document`, `DocumentMediaMetadata`, `MapPin`, `MapFocus`, `ClassLocalContent`).
  - Sequence Diagrams: `seq_05_document_ocr_and_rag_sync.puml` (PDF Upload, PDFBox/Tess4J OCR Extraction & Async AI RAG Indexing), `seq_06_local_content_approval.puml` (Teacher Local Content Creation & School Admin Review/Approval).
- **3.4 Conversational AI Roleplay & Hybrid RAG Microservice**
  - Class Diagram: `cls_04_chat_and_ai_rag.puml` (`ChatController`, `ChatSessionServiceImpl`, `MessageServiceImpl`, `ChatHistoryServiceImpl`, `AiServiceClient`, FastAPI `chat_router`, `chat_service`, `prompt_builder`, `JavaBackendClient`, `ChatSession`, `Message`, `VectorChunk`).
  - Sequence Diagrams: `seq_07_ai_roleplay_chat_rag.puml` (Initialize Chat Session, BGE-M3 Vector Retrieval, Kaggle Reranking, Streaming LLM Roleplay & Follow-up Question Generation).
- **3.5 Quiz Engine, Question Bank & Classroom Assignments**
  - Class Diagram: `cls_05_quiz_and_assignment.puml` (`QuizController`, `StaffQuizController`, `StaffQuestionBankController`, `TeacherAssignmentController`, `StudentAssignmentController`, `QuizServiceImpl`, `TeacherAssignmentServiceImpl`, `StudentAssignmentServiceImpl`, `Quiz`, `Question`, `QuizQuestion`, `QuizSession`, `QuizAnswerDetail`, `Assignment`, `AssignmentSubmission`).
  - Sequence Diagrams: `seq_08_quiz_attempt_and_grading.puml` (Multi-Type Quiz Execution & Auto-Grading), `seq_09_classroom_assignment_submission.puml` (Teacher Assignment Creation & Student Auto-Tracked Submission).
- **3.6 B2C Subscription, Gamification & PayOS Payment Fulfillment**
  - Class Diagram: `cls_06_payment_and_gamification.puml` (`PaymentController`, `PayOSWebhookController`, `GamificationController`, `TierController`, `PaymentService`, `PaymentWebhookService`, `PaymentFulfillmentService`, `PaymentFulfillmentReconciliationScheduler`, `PaymentExpiryScheduler`, `GamificationServiceImpl`, `Tier`, `UserTier`, `PaymentOrder`, `PaymentTransaction`, `DailyQuest`, `UserQuestProgress`, `DailyCheckIn`).
  - Sequence Diagrams: `seq_10_payos_checkout_and_fulfillment.puml` (PayOS Checkout Creation, Webhook Signature Verification, Resilient Tier Fulfillment & Reconciliation Scheduler).

### Section 4: Entity State Machines & Main Workflow Activity Diagrams (`sections/04_review2_workflows_and_states.tex`)
- **4.1 Entity State Machine Diagrams (Mandatory for Review 2 Check #4)**
  - `stm_01_content_and_account.puml`: State machines for `ContentStatus` (`DRAFT`, `PENDING_REVIEW`, `ACTIVE`, `INACTIVE`), `AccountStatus` (`ACTIVE`, `DEACTIVATED`), and `SchoolStatus` (`ACTIVE`, `INACTIVE`, `SUSPENDED`, `EXPIRED`).
  - `stm_02_assignment_and_token.puml`: State machines for `AssignmentStatus` (`ACTIVE`, `CANCELLED`, `ARCHIVED`), `SubmissionStatus` (`NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`, `LATE_SUBMITTED`, `MISSED`, `EXEMPTED`), and `TokenBoostRequestStatus` (`PENDING`, `APPROVED`, `REJECTED`).
  - `stm_03_payment_lifecycle.puml`: State machines for `PaymentOrderStatus` (`PENDING`, `PAID`, `CANCELLED`, `EXPIRED`, `FAILED`), `PaymentFulfillmentStatus` (`PENDING`, `PROCESSING`, `FULFILLED`, `FAILED`), and `PaymentTransactionStatus` (`PENDING`, `SUCCESS`, `FAILED`).
- **4.2 Main Business Workflow Activity Diagrams (Mandatory for Review 2 Check #5 & Defense 1.1)**
  - `act_wf01_school_onboarding_and_enrollment.puml`: **WF-01** — B2B School Subscription Provisioning, Classroom Setup & Bulk Student Enrollment (`System Admin`, `School Admin`, `Teacher`, `Student`).
  - `act_wf02_rag_content_and_local_approval.puml`: **WF-02** — Historical Content, PDF OCR Ingestion, Vector Indexing & School Local Content Approval (`Content Admin`, `Teacher`, `School Admin`).
  - `act_wf03_ai_character_roleplay_and_token_boost.puml`: **WF-03** — Grounded AI Historical Roleplay Chat, Daily Token Quota Enforcement & Token Boost Approval (`Student / Customer`, `Teacher`, `School Admin`).
  - `act_wf04_classroom_assignment_and_quiz_grading.puml`: **WF-04** — Classroom Assignment Dispatch, Multi-Type Quiz Assessment & Gradebook Analytics (`Teacher`, `Student`).
  - `act_wf05_b2c_payos_subscription_and_gamification.puml`: **WF-05** — B2C Tier Upgrade via PayOS Webhook Fulfillment & Daily Gamification Quest Claim (`Customer`, `PayOS Gateway`, `System Admin`).

### Section 5: Technology & AI / LLM / RAG Declarations (`sections/05_technology_and_ai_declarations.tex`)
- **5.1 Five-Group Technology & AI Usage Declaration** (Third-party services, Development stack with versions, Source Control & CI/CD, Deployment environments & rollback, AI tools across project stages).
- **5.2 AI / LLM / RAG Technical Specification (5 Mandatory Review 2 Items)**:
  - `arch_ai_rag_pipeline.puml` / `.png`: Detailed RAG Ingestion & Retrieval-Reranking-Generation Pipeline Diagram.
  - Models & Versions (`Gemma-2-9B-IT` / OpenAI-compatible LLM, `BAAI/bge-m3` 768-D embeddings, Cross-Encoder Reranker, `Tess4J 5.10.0` Vietnamese OCR).
  - Pipeline specification (Dynamic 80th-percentile semantic chunking, Supabase PgVector HNSW `m=16, ef_construction=64`, Top-10 hybrid retrieval $\rightarrow$ Kaggle Reranker $\rightarrow$ 1st-person persona prompt with 30% Socratic reverse-questioning).
  - Evaluation Metrics & Thresholds (TTFT $< 1.5\text{s}$, Faithfulness / Groundedness, Historical Fact Accuracy, Context Recall).
  - Fallback & Guardrail Mechanisms (Reranker fallback to Top-2 cosine chunks, Ollama `/api/embeddings` $\leftrightarrow$ `/api/embed` retry with exponential backoff, strict "Sử sách không ghi chép" anti-hallucination guardrail, PayOS reconciliation scheduler).
