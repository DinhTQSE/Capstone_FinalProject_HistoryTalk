# CAPSTONE PROJECT REPORT  
## Report 2: Project Management Plan (PMP) & Schedule  

**Project Title (English):** HistoryTalk: A SaaS Historical Education Platform Powered by Conversational AI and RAG Technology  
**Academic Program:** Software Engineering (SWP490 / SEP490) — FPT University  
**Document Version:** 1.0  
**Submission Milestone:** Review 1 (Week 3 Deliverable)  

---

### 📑 TABLE OF CONTENTS
1. [Executive Overview & Objectives](#1-executive-overview--objectives)
2. [Work Breakdown Structure (WBS) & Effort Estimation](#2-work-breakdown-structure-wbs--effort-estimation)
3. [Project Schedule & Sprint Iteration Plan](#3-project-schedule--sprint-iteration-plan)
4. [Responsibility Assignment (RACI Matrix)](#4-responsibility-assignment-raci-matrix)
5. [Configuration & Quality Management](#5-configuration--quality-management)
6. [Risk Management & Mitigation Strategy](#6-risk-management--mitigation-strategy)

---

### 1. EXECUTIVE OVERVIEW & OBJECTIVES

The Project Management Plan (PMP) outlines the execution strategy, resource allocation, schedule, risk controls, and quality assurance mechanisms for the HistoryTalk project across the 15-week Capstone lifecycle.

#### 1.1. Project Success Metrics
* **On-Time Delivery:** Complete 100% of mandatory features chotted in Review 2 by Week 13.
* **Code Quality & Build Stability:** Zero critical defects in main branch; 100% build pass rate on `mvn clean install` and FastAPI startup.
* **Response Latency:** Streaming AI response TTFT (Time-To-First-Token) $< 1.5$ seconds.
* **Documentation Rigor:** Complete Report 1 through Report 7 in English according to course standards.

---

### 2. WORK BREAKDOWN STRUCTURE (WBS) & EFFORT ESTIMATION

Effort is estimated in **Man-Days** (1 man-day = 8 working hours) for 5 team members across 15 weeks (total effort budget: ~150 man-days).

```
1.0 Project Management & Quality Control (20 man-days)
  1.1 Project Initialization & Proposal (4 man-days)
  1.2 SRS & SDD Documentation (8 man-days)
  1.3 Weekly Tracking, Sprints & Review Preparation (8 man-days)

2.0 Java Backend Service (45 man-days)
  2.1 Auth, Security & OAuth2 Integration (8 man-days)
  2.2 SaaS B2B School & Classroom Management (10 man-days)
  2.3 Document Processor & OCR Pipeline (8 man-days)
  2.4 PayOS Payment & Token Economy (7 man-days)
  2.5 System Dashboard & Analytics Engine (7 man-days)
  2.6 Quiz Management & Scoring APIs (5 man-days)

3.0 AI Backend Microservice (35 man-days)
  3.1 Vector Chunking & Embedding Pipeline (8 man-days)
  3.2 Supabase PgVector & Kaggle Reranker Integration (10 man-days)
  3.3 First-Person Character Roleplay Prompt Engine (9 man-days)
  3.4 SSE Streaming & Follow-up Suggestion Generator (8 man-days)

4.0 Frontend Web Application (35 man-days)
  4.1 Authentication & Profile UI (4 man-days)
  4.2 Student Learning Portal (Chat, Interactive Map, Quiz) (12 man-days)
  4.3 Teacher & School Admin B2B SaaS Portal (12 man-days)
  4.4 System Admin & Analytics Dashboard UI (7 man-days)

5.0 Testing & DevOps Infrastructure (15 man-days)
  5.1 Test Plan, Unit Test Matrix & Defect Tracking (7 man-days)
  5.2 CI/CD Automation, Docker & Cloud Server Deployment (8 man-days)
```

---

### 3. PROJECT SCHEDULE & SPRINT ITERATION PLAN

```
Tuần 1–3  : Phase 1 — Feasibility & Requirements Gathering (Review 1 Submission)
Tuần 4–6  : Phase 2 — Scope Finalization & Architecture Baseline (Review 1 Defense & Scope Freeze)
Tuần 7–9  : Phase 3 — Iteration 1 & System Design (Review 2 Submission & Code Package 1)
Tuần 10–13: Phase 4 — Iteration 2 & 3, Full System Testing & Optimization (Full Code Package)
Tuần 14   : Phase 5 — Committee 1.1 (Pre-defense Qualification)
Tuần 15   : Phase 6 — Committee 1.2 (Final Project Defense)
```

---

### 4. RESPONSIBILITY ASSIGNMENT (RACI MATRIX)

**Legend:** **R** = Responsible (Does the work), **A** = Accountable (Final approver), **C** = Consulted (Provides input), **I** = Informed (Kept updated).

| Task / Deliverable | Nguyễn Minh Trí (Leader) | Trần Quốc Dinh (DevOps/BE) | Võ Đồng Đức Khải (BE) | Nguyễn Công Thành (FE) | Đỗ Quốc Bảo (BE) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Project Management & Schedule** | **A / R** | C | C | C | C |
| **AI RAG Pipeline & SSE Streaming** | **A / R** | C | C | I | I |
| **SaaS B2B Class & Progress APIs** | C | **A / R** | R | I | I |
| **DevOps, CI/CD & Server Deploy** | C | **A / R** | I | I | I |
| **Auth, OAuth2 & Security Filter** | C | I | **A / R** | I | I |
| **Document Upload & OCR Pipeline** | I | I | **A / R** | I | I |
| **Frontend Web Application (All)** | I | I | I | **A / R** | C |
| **Quiz Engine & PayOS Integration** | I | I | I | C | **A / R** |
| **System Test Plan & Defect Log** | C | R | R | **A / R** | R |

---

### 5. CONFIGURATION & QUALITY MANAGEMENT

#### 5.1. Version Control & Branching Strategy
* **Monorepo Structure:** `Source-code/SWD392_FinalProject_HistoryTalk/`
* **Main Branches:**
  * `main`: Production-ready code. Protection enabled; direct pushes prohibited.
  * `deployment_v5` / topic branches: `feature/<feature-name>`, `fix/<bug-name>`.
* **Commit Conventions:** Conventional Commits (e.g., `feat(java): ...`, `fix(ai): ...`, `docs: ...`).

#### 5.2. Documentation Location Standards
* Service-specific Java docs: `docs/services/history-talk-backend/`
* Service-specific AI docs: `docs/services/history-talk-backend-ai/`
* Review Deliverables: `docs/review1_deliverables/`

---

### 6. RISK MANAGEMENT & MITIGATION STRATEGY

| Risk ID | Risk Description | Severity | Likelihood | Mitigation Strategy |
| :---: | :--- | :---: | :---: | :--- |
| **R-01** | **LLM Hallucination on Historical Data:** Model invents inaccurate historical dates or facts. | **High** | Medium | Implement two-step RAG (Vector Search + Kaggle Cross-Encoder Reranker) and strict system prompts. |
| **R-02** | **Student Chat Spamming:** Students type random characters to trick message counter. | **Medium** | High | Implement Spam Filter ($\ge 10$ chars) and position Quiz as the primary grading anchor. |
| **R-03** | **PayOS Webhook Failure:** Payment notification dropped during network instability. | **High** | Low | Implement automated reconciliation background scheduler (`PaymentFulfillmentReconciliationScheduler`). |
| **R-04** | **Scope Creep before Review 2:** Expanding features beyond 15-week budget. | **High** | Medium | Freeze scope at Week 6 (Phiếu đăng ký) and strictly adhere to mandatory feature list. |
| **R-05** | **Multi-tenant Account Collision:** Duplicate usernames across different school tenants. | **High** | Low | Enforce strict naming convention with mandatory `school_code` prefix (e.g., `lhp_hs_1001`). |
