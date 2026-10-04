# CAPSTONE PROJECT REPORT  
## Report 1: Project Introduction  

**Project Title (English):** HistoryTalk: A SaaS Historical Education Platform Powered by Conversational AI and RAG Technology  
**Project Title (Vietnamese):** HistoryTalk: Nền tảng SaaS Giáo dục Lịch sử ứng dụng Trí tuệ Nhân tạo và công nghệ RAG  
**Academic Program:** Software Engineering (SWP490 / SEP490) — FPT University  
**Document Version:** 1.0  
**Submission Milestone:** Review 1 (Week 3 Deliverable)  

---

### 📑 TABLE OF CONTENTS
1. [Executive Summary](#1-executive-summary)
2. [Context & Educational Need](#2-context--educational-need)
3. [Problem Statement](#3-problem-statement)
4. [Proposed Solution](#4-proposed-solution)
5. [Project Scope & Objectives](#5-project-scope--objectives)
6. [Target Users & Persona Analysis](#6-target-users--persona-analysis)
7. [Team Members & Role Allocation](#7-team-members--role-allocation)

---

### 1. EXECUTIVE SUMMARY

HistoryTalk is an innovative Software-as-a-Service (SaaS) educational platform engineered to transform historical learning in Vietnam. Traditional history education suffers from passive rote memorization, leading to low student engagement, poor context retention, and significant grading overhead for teachers. 

HistoryTalk addresses these challenges by combining **Conversational Artificial Intelligence (AI)** with **Retrieval-Augmented Generation (RAG)** technology. Students can engage in first-person roleplay dialogues with historical figures (e.g., Vua Quang Trung, Nguyễn Trãi, Trần Hưng Đạo), ground their knowledge in verified historical databases, explore interactive spatio-temporal maps, and complete automated quiz assessments.

Operated under a dual-business model (**B2C** for individual learners and **B2B SaaS** for school organizations), HistoryTalk provides school administrators and teachers with class management, assignment distribution, and student progress tracking tools.

---

### 2. CONTEXT & EDUCATIONAL NEED

History education in Vietnamese secondary and high schools faces persistent systemic challenges:
1. **Passive Learning Paradigm:** Textbooks present historical events as static dates and numbers, removing human emotion, strategic decision-making, and contextual depth.
2. **High Hallucination Risk in Generic AI:** While students increasingly use general-purpose LLMs (such as ChatGPT), unconstrained LLMs frequently generate historical inaccuracies ("hallucinations"), misquoting dates, figures, and socio-political dynamics.
3. **Lack of EdTech SaaS Tools for Schools:** Secondary and high schools lack specialized digital platforms to assign localized historical content, monitor self-study hours, and track AI-based student interaction metrics.

---

### 3. PROBLEM STATEMENT

#### 3.1. Student Pain Points
* **Boredom & Poor Retention:** Abstract historical facts without interactive context lead to rapid forgetting.
* **Inability to Interrogate History:** Students cannot ask "Why did Vua Quang Trung launch the sudden attack on Lunar New Year 1789?" or explore historical motivations interactively.

#### 3.2. Educator & Administrator Pain Points
* **Manual Grading Overhead:** Teachers spend significant time creating, distributing, and grading paper-based or basic online quizzes.
* **Lack of Visibility into Student Self-Study:** No quantitative metrics exist to measure whether students actively engage with study materials outside the classroom.
* **Inefficient School Account Management:** Managing digital access for thousands of students across dozens of classes requires automated B2B onboarding.

---

### 4. PROPOSED SOLUTION

HistoryTalk delivers a comprehensive multi-tenant SaaS platform featuring:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             HISTORYTALK PLATFORM                            │
├──────────────────────────────────────┬──────────────────────────────────────┤
│               B2C FLOW               │               B2B FLOW               │
│        (Individual Customers)        │           (Schools & Edus)           │
│  - Free & Premium Tier Subscriptions │  - School Admin Class Onboarding      │
│  - Token-Based Daily Allowance       │  - Teacher Assignment Distribution   │
│  - Individual Quiz & Character Chat  │  - Progress Tracking & Score Reports │
└──────────────────────────────────────┴──────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CORE ENGINE MODULES                               │
├──────────────────┬──────────────────┬──────────────────┬────────────────────┤
│   AI RAG CHAT    │ INTERACTIVE MAP  │   QUIZ ENGINE    │  DOCUMENT & OCR    │
│ First-Person     │ Spatio-Temporal  │ Automated Tests  │ PDFBox & Tess4J    │
│ Roleplay System  │ Timeline Viewer  │ & Analytics      │ Content Extractor  │
└──────────────────┴──────────────────┴──────────────────┴────────────────────┘
```

1. **RAG-Powered Conversational AI:** Integrates Large Language Models with a two-step Retrieval-Augmented Generation pipeline (Vector Search via Supabase PgVector + Cross-Encoder Reranking via Kaggle API). This enforces historical grounding, eliminating hallucinations.
2. **First-Person Historical Roleplay:** Prompts enforce authentic 1st-person Vietnamese roleplay pronouns (*"Ta", "Trẫm", "Tôi"*), immersing learners in historical dialogues.
3. **B2B Classroom Management SaaS:** Enables School Admins to onboard classrooms via batch Excel imports and allows Teachers to assign multi-task homework (Context Reading + AI Character Dialogue + Quiz Tests).
4. **Automated Assessment & Token Economy:** Employs a Token economy linked to subscription tiers and provides real-time quiz scoring and progress analytics.

---

### 5. PROJECT SCOPE & OBJECTIVES

#### 5.1. Primary Objectives
* **Functional Objective:** Deliver a fully operational web application supporting B2C and B2B SaaS workflows, character roleplay, document extraction (PDF/OCR), payment integration (PayOS), and analytics dashboards.
* **Academic/Research Objective:** Formulate a $4 \times 4$ experimental evaluation matrix (4 Chunking strategies $\times$ 4 Retrieval methods) evaluated via the RAGAS framework (LLM-as-a-Judge) on Vietnamese historical texts to establish RAG Best Practices.

#### 5.2. System Boundaries & Constraints
* **In-Scope:**
  * Multi-role authentication (System Admin, School Admin, Teacher, School Student, Customer, Content Admin).
  * Document upload and OCR processing (Tess4J tiếng Việt + Apache PDFBox).
  * PayOS payment gateway integration for B2C token/tier top-ups.
  * Server-Sent Events (SSE) streaming for AI chat responses.
  * System, Revenue, Token, and Quiz analytics dashboards.
* **Out-of-Scope for Iteration 1:** Native mobile apps (planned on long-term roadmap).

---

### 6. TARGET USERS & PERSONA ANALYSIS

| User Role | Persona Profile | Key System Interactions |
| :--- | :--- | :--- |
| **School Student** | High school student studying for history exams. | Logs in via school credentials, views assigned homework, chats with historical AI personae, completes timed quizzes. |
| **Teacher** | History instructor managing 4–5 classes. | Creates local quizzes/contexts, distributes multi-task assignments, views class completion rates and score reports. |
| **School Admin** | School IT administrator. | Batch imports students/teachers via 2-sheet Excel templates, manages school class lists and teacher assignments. |
| **Customer (B2C)** | Independent learner / History enthusiast. | Registers via Email/Google OAuth, subscribes to Premium tiers via PayOS, chats with AI figures. |
| **System Admin** | Platform super-administrator. | Provisions new school tenants, sets SaaS subscription expiration dates, views global system health and revenue metrics. |
| **Content Admin** | History academic specialist. | Curates global historical contexts, character bio files, reference documents, and global quiz banks. |

---

### 7. TEAM MEMBERS & ROLE ALLOCATION

* **Nguyễn Minh Trí (SE192645) — Team Leader & AI Engineer & Backend Developer:** Project management, RAG pipeline architecture, streaming AI chat execution, prompt engineering, token management logic.
* **Trần Quốc Dinh (SE196495) — Backend Developer & DevOps:** SaaS B2B classroom management APIs, user management, progress tracking & system analytics, CI/CD pipeline automation & server deployment.
* **Võ Đồng Đức Khải (SE196405) — Backend Developer & Tester:** Multi-tenant database schema design, RBAC security & Google OAuth2 integration, content CRUD APIs, document/PDF knowledge base upload pipeline.
* **Nguyễn Công Thành (SE196309) — Frontend Developer & Tester:** Student web interface (Chat, Interactive Map, Quiz), B2B Teacher & Admin Portals, UI/UX design, test case execution.
* **Đỗ Quốc Bảo (SE193803) — Backend Developer & Tester:** Quiz engine APIs, PayOS online payment integration, interactive map geolocation APIs, test case execution.
