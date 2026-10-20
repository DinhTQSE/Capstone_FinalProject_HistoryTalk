# CAPSTONE PROJECT REPORT — REPORT 4: SOFTWARE DESIGN DOCUMENT (SDD)

**Project Title:** HistoryTalk: A Multi-Tenant SaaS Historical Education Platform Powered by Conversational AI and Hybrid RAG Technology  
**Subject Code:** SWP490 / SEP490 — Capstone Project  
**Milestone:** Review 2 (Tag `review-2`)  
**Version:** 2.0  
**Date:** October 2026  

---

## 0. PlantUML Source Code Index (Stored in Repository for Review 2 Verification)

All diagrams in this Software Design Document are stored as PlantUML source files (`*.puml`) under `docs/report4_sdd/uml/` and rendered to high-resolution PNG figures under `docs/report4_sdd/overleaf/figures/`:

1. **System & AI Pipeline Architecture:**
   - [arch_system_overview.puml](uml/arch_system_overview.puml) — System Architecture Overview (Microservices, Data Stores & External Services)
   - [arch_ai_rag_pipeline.puml](uml/arch_ai_rag_pipeline.puml) — Hybrid RAG Ingestion & Conversational AI Roleplay Pipeline
2. **Package Diagrams:**
   - [pkg_backend_java.puml](uml/pkg_backend_java.puml) — Core Backend Service (`com.historytalk` — Java 21 / Spring Boot 3.2.5)
   - [pkg_backend_ai.puml](uml/pkg_backend_ai.puml) — AI & Hybrid RAG Microservice (`history_talk_ai` — Python 3.11 / FastAPI)
   - [pkg_frontend_web.puml](uml/pkg_frontend_web.puml) — Frontend Web Application (Next.js / React TypeScript)
3. **Database Design (Logical & Physical ERD — 32 Tables):**
   - [erd_full_overview.puml](uml/erd_full_overview.puml) — Complete Logical ERD across all 32 Tables (`V1`–`V38` + `vector_chunk`)
4. **Detailed Design Class Diagrams (6 Core Subsystems):**
   - [cls_01_auth_rbac.puml](uml/cls_01_auth_rbac.puml) — Authentication, Account Lifecycle & Multi-Tenant RBAC
   - [cls_02_school_classroom_token.puml](uml/cls_02_school_classroom_token.puml) — B2B SaaS School, Classroom & Token Quota Allocation
   - [cls_03_content_character_map.puml](uml/cls_03_content_character_map.puml) — Historical Content, Character, Document OCR & Interactive Map
   - [cls_04_chat_and_ai_rag.puml](uml/cls_04_chat_and_ai_rag.puml) — Conversational AI Roleplay & Hybrid RAG Microservice
   - [cls_05_quiz_and_assignment.puml](uml/cls_05_quiz_and_assignment.puml) — Quiz Engine, Question Bank & Classroom Assignments
   - [cls_06_payment_and_gamification.puml](uml/cls_06_payment_and_gamification.puml) — B2C Subscription, Gamification & PayOS Payment Fulfillment
5. **Detailed Design Sequence Diagrams (10 Core Flows):**
   - [seq_01_login_oauth_jwt.puml](uml/seq_01_login_oauth_jwt.puml) — User Login, Google OAuth2 & Dual-Token JWT Rotation
   - [seq_02_password_reset_and_lifecycle.puml](uml/seq_02_password_reset_and_lifecycle.puml) — Password Reset & Admin Account Lifecycle Management
   - [seq_03_school_provisioning_and_import.puml](uml/seq_03_school_provisioning_and_import.puml) — B2B School Onboarding & Bulk CSV Student Enrollment
   - [seq_04_token_boost_and_allowance.puml](uml/seq_04_token_boost_and_allowance.puml) — Daily Student Token Quota Check & Token Boost Approval
   - [seq_05_document_ocr_and_rag_sync.puml](uml/seq_05_document_ocr_and_rag_sync.puml) — PDF Upload, Hybrid PDFBox/Tess4J OCR & Async RAG Vector Indexing
   - [seq_06_local_content_approval.puml](uml/seq_06_local_content_approval.puml) — School Local History Content Creation, Review & Classroom Assignment
   - [seq_07_ai_roleplay_chat_rag.puml](uml/seq_07_ai_roleplay_chat_rag.puml) — Streaming Conversational AI Roleplay with Hybrid RAG & Kaggle Reranker
   - [seq_08_quiz_attempt_and_grading.puml](uml/seq_08_quiz_attempt_and_grading.puml) — Multi-Type Quiz Execution, Automated Grading & Assignment Sync
   - [seq_09_classroom_assignment_submission.puml](uml/seq_09_classroom_assignment_submission.puml) — Classroom Assignment Creation, Auto-Tracking & Gradebook Analytics
   - [seq_10_payos_checkout_and_fulfillment.puml](uml/seq_10_payos_checkout_and_fulfillment.puml) — PayOS Checkout, Webhook Verification & Resilient Tier Fulfillment
6. **Entity State Machine Diagrams (9 Domain Lifecycles):**
   - [stm_01_content_and_account.puml](uml/stm_01_content_and_account.puml) — `ContentStatus`, `AccountStatus`, and `SchoolStatus` State Machines
   - [stm_02_assignment_and_token.puml](uml/stm_02_assignment_and_token.puml) — `AssignmentStatus`, `SubmissionStatus`, and `TokenBoostRequestStatus` State Machines
   - [stm_03_payment_lifecycle.puml](uml/stm_03_payment_lifecycle.puml) — `PaymentOrderStatus`, `PaymentFulfillmentStatus`, and `PaymentTransactionStatus` State Machines
7. **Main Business Workflow Activity Diagrams (5 Defense 1.1 Workflows):**
   - [act_wf01_school_onboarding_and_enrollment.puml](uml/act_wf01_school_onboarding_and_enrollment.puml) — **WF-01**: B2B School Onboarding, Classroom Setup & Bulk Student Enrollment
   - [act_wf02_rag_content_and_local_approval.puml](uml/act_wf02_rag_content_and_local_approval.puml) — **WF-02**: Historical Document OCR Ingestion, RAG Indexing & Local Content Approval
   - [act_wf03_ai_character_roleplay_and_token_boost.puml](uml/act_wf03_ai_character_roleplay_and_token_boost.puml) — **WF-03**: Grounded AI Historical Roleplay Chat & Daily Token Boost Workflow
   - [act_wf04_classroom_assignment_and_quiz_grading.puml](uml/act_wf04_classroom_assignment_and_quiz_grading.puml) — **WF-04**: Classroom Assignment Dispatch, Multi-Type Quiz & Automated Grading
   - [act_wf05_b2c_payos_subscription_and_gamification.puml](uml/act_wf05_b2c_payos_subscription_and_gamification.puml) — **WF-05**: B2C Subscription Upgrade via PayOS & Daily Gamification Rewards

---

## 1. LaTeX Overleaf Project Structure

The full LaTeX Overleaf document is located at `docs/report4_sdd/overleaf/`:
- [main.tex](overleaf/main.tex) — Master document, Cover Page, Record of Changes, and SDD Team Ownership
- [sections/01_system_design.tex](overleaf/sections/01_system_design.tex) — Section 1: System Architecture & Package Diagrams (Java, Python AI, Next.js Web)
- [sections/02_database_design.tex](overleaf/sections/02_database_design.tex) — Section 2: Database Design (32-Table ERD, Table Descriptions & Complete Data Dictionary)
- [sections/03_detailed_design.tex](overleaf/sections/03_detailed_design.tex) — Section 3: Detailed Design (6 Subsystems with Class Diagrams, Class Tables & 10 Sequence Diagrams)
- [sections/04_review2_workflows_and_states.tex](overleaf/sections/04_review2_workflows_and_states.tex) — Section 4: Entity State Machine Diagrams & 5 Main Business Workflow Activity Diagrams
- [sections/05_technology_and_ai_declarations.tex](overleaf/sections/05_technology_and_ai_declarations.tex) — Section 5: 5-Group Technology & AI Tool Declaration + 5-Item AI/LLM/RAG Technical Specification
