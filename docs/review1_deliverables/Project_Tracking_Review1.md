# CAPSTONE PROJECT REPORT  
## Deliverable 4: Project Tracking Document & Progress Matrix  

**Project Title (English):** HistoryTalk: A SaaS Historical Education Platform Powered by Conversational AI and RAG Technology  
**Academic Program:** Software Engineering (SWP490 / SEP490) — FPT University  
**Document Version:** 1.0  
**Submission Milestone:** Review 1 (Week 3 Deliverable)  

---

### 📑 TABLE OF CONTENTS
1. [Overview of Tracking Matrix](#1-overview-of-tracking-matrix)
2. [Work Breakdown Structure & Progress (Weeks 1–3)](#2-work-breakdown-structure--progress-weeks-13)
3. [Weekly Advisor Meeting Logs (Weeks 1, 2, 3)](#3-weekly-advisor-meeting-logs-weeks-1-2-3)
4. [Issue & Technical Risk Log](#4-issue--technical-risk-log)
5. [Q&A Log with Project Advisor](#5-qa-log-with-project-advisor)

---

### 1. OVERVIEW OF TRACKING MATRIX

The Project Tracking Document maintains continuous visibility into task completion, sprint velocity, advisor feedback, risk logs, and issue resolution across the 15-week Capstone development cycle.

---

### 2. WORK BREAKDOWN STRUCTURE & PROGRESS (WEEKS 1–3)

| Task ID | Task Description | Assigned Member | Target Week | Status | Completion % |
| :---: | :--- | :---: | :---: | :---: | :---: |
| **WBS-1.1** | Team Formation & Topic Proposal Formulation | Nguyễn Minh Trí | Week 1 | **Completed** | 100% |
| **WBS-1.2** | Report 1: Project Introduction & Problem Definition | Nguyễn Minh Trí | Week 1 | **Completed** | 100% |
| **WBS-2.1** | Report 2: Project Management Plan (PMP) & Schedule | Trần Quốc Dinh | Week 2 | **Completed** | 100% |
| **WBS-2.2** | WBS & Effort Estimation Matrix Formulation | Trần Quốc Dinh | Week 2 | **Completed** | 100% |
| **WBS-3.1** | Report 3 (Overview & System Functional Requirements) | Võ Đồng Đức Khải | Week 3 | **Completed** | 100% |
| **WBS-3.2** | Multi-tenant Database Schema Design & Flyway Migrations | Võ Đồng Đức Khải | Week 3 | **Completed** | 100% |
| **WBS-3.3** | Frontend Wireframing & Student/Teacher UI Prototypes | Nguyễn Công Thành | Week 3 | **Completed** | 100% |
| **WBS-3.4** | Quiz Engine Architecture & PayOS Payment Integration Design | Đỗ Quốc Bảo | Week 3 | **Completed** | 100% |
| **WBS-3.5** | AI RAG Pipeline & Semantic Chunking Specification | Nguyễn Minh Trí | Week 3 | **Completed** | 100% |

---

### 3. WEEKLY ADVISOR MEETING LOGS (WEEKS 1, 2, 3)

#### 📅 Meeting 1 — Week 1 (Kick-off & Problem Definition)
* **Date:** September 2, 2026
* **Attendees:** All team members + Project Advisor (GVHD).
* **Agenda:** Proposal review, problem statement verification, technology selection.
* **Advisor Feedback:** Approved the dual B2C/B2B SaaS direction for HistoryTalk. Recommended integrating RAG accuracy benchmarking (RAGAS) to strengthen the academic research contribution.
* **Action Items:** Finalize Report 1 and proceed to Project Management Plan.

#### 📅 Meeting 2 — Week 2 (PMP & Architecture Alignment)
* **Date:** September 9, 2026
* **Attendees:** All team members + Project Advisor.
* **Agenda:** Review WBS man-days estimation, microservice architecture, and Review 1 submission checklist.
* **Advisor Feedback:** Emphasized strict multi-tenant account isolation for schools. Advised creating mandatory prefix-based usernames (`{school_code}_hs_{ma_hs}`) to prevent duplicate accounts.
* **Action Items:** Update Report 2 PMP, draft Report 3 SRS overview, and finalize Review 1 submission package.

#### 📅 Meeting 3 — Week 3 (Review 1 Final Sign-off)
* **Date:** September 14, 2026
* **Attendees:** All team members + Project Advisor.
* **Agenda:** Final review of documents (Report 1, Report 2, Report 3 Overview, Project Tracking) before Review 1 submission.
* **Advisor Feedback:** Verified complete documentation package. Approved team to submit for Review 1 defense with external reviewers.
* **Action Items:** Submit deliverable package to LMS portal.

---

### 4. ISSUE & TECHNICAL RISK LOG

| Issue ID | Category | Description | Impact | Status | Resolution |
| :---: | :---: | :--- | :---: | :---: | :--- |
| **ISS-01** | AI | Hallucination risk on Vietnamese historical facts. | High | **Resolved** | Implemented PgVector retrieval + Kaggle Cross-Encoder Reranker. |
| **ISS-02** | Security | Potential username collisions when multiple schools import students with identical IDs. | High | **Resolved** | Enforced prefix-based username convention (`{school_code}_hs_{ma_hs}`). |
| **ISS-03** | Academic | Potential for student gaming of AI chat interaction limits. | Medium | **Resolved** | Set minimum character filter ($\ge 10$ chars) and designated Quiz tests as primary grading anchor. |

---

### 5. Q&A LOG WITH PROJECT ADVISOR

* **Q1:** *How will the platform prevent students from bypassing reading materials?*  
  **Answer:** Reading contexts serve as supplemental background. Academic integrity and grades are strictly anchored to timed, randomized Quiz assessments.
* **Q2:** *How will school admins onboard thousands of students efficiently?*  
  **Answer:** Via a 2-sheet Excel template (`Danh_Sach_Giao_Vien` & `Danh_Sach_Hoc_Sinh_Va_Lop`). The backend automatically provisions accounts and assigns classrooms in a single transactional batch.
