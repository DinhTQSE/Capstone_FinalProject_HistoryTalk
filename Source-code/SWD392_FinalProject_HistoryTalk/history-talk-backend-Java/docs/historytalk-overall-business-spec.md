# HistoryTalk - Overall Business Domain Specification
**Document Code:** BUS-SPEC-OVERALL-01  
**Target Audience:** Product Owners, Developers, QA, Business Analysts  
**Domain:** Educational Technology (EdTech), AI Roleplay, Gamification, B2B SaaS  
**Version:** 2.0  

---

## 1. PRODUCT VISION & OVERVIEW

**HistoryTalk** is an interactive educational ecosystem that transforms passive history learning into an engaging, conversational experience. By combining generative AI roleplay, Retrieval-Augmented Generation (RAG) grounded in verified historical documents, and gamification, HistoryTalk enables students and historical enthusiasts to converse directly with historical figures within specific contexts.

```
+-----------------------------------------------------------------------+
|                         HISTORYTALK ECOSYSTEM                         |
+-----------------------------------------------------------------------+
|  [ AI Roleplay Chat ]     [ Historical Knowledge ]   [ Gamification ] |
|  - Character Persona      - Contexts & Events        - XP & Leveling  |
|  - Era Linguistics        - RAG Vector Indexing      - Streaks & Quests|
|  - Suggested Questions    - Verified Documents       - Quiz Assessment|
+-----------------------------------------------------------------------+
|                        DELIVERY CHANNELS                              |
|  - B2C Individual Learners (Free / Plus / Pro Subscriptions)          |
|  - B2B Classroom SaaS (Schools, Teachers, Student Classes)            |
+-----------------------------------------------------------------------+
```

---

## 2. CORE BUSINESS DOMAINS & MECHANICS

### 2.1 Historical Contexts & Events
* **Concept**: Represents historical eras, pivotal battles, political treaties, or cultural movements (e.g., *Trận Bạch Đằng 938*, *Chiến dịch Điện Biên Phủ*).
* **Era Classification (`EventEra`)**: `ANCIENT` (Cổ đại), `MEDIEVAL` (Trung đại), `MODERN` (Cận đại), `CONTEMPORARY` (Hiện đại).
* **Category Classification (`EventCategory`)**: `WAR`, `POLITICS`, `CULTURE`, `DIPLOMACY`, `REVOLUTION`.

### 2.2 Historical Characters & Decoupled Mapping
* **Character Profile**: Stores biographical details, title/position, life span (`born_date` - `death_date`), personality traits, and default linguistic style.
* **Decoupled Many-to-Many Mapping (`context_character_mapping`)**:
  * Characters and Contexts are independently defined.
  * A single historical character can be linked to multiple historical contexts (e.g., *Trần Hưng Đạo* appears in both the *1285 Resistance* and *1288 Bạch Đằng Campaign*).
  * Prevents data duplication while preserving context-specific character roles.

### 2.3 AI Roleplay Chat Engine
* **Chat Sessions (`ChatSession`)**: Bound to a triad `(User, Character, Context)`.
* **Immersive Persona Rules**:
  * AI adopts authentic pronouns based on status (e.g., *Ta - Ngươi*, *Trẫm - Khanh*, *Lão thần*).
  * AI provides historically grounded answers based on RAG documents.
  * AI proactively asks open-ended questions in ~30% of turns to encourage deeper exploration.
  * AI appends 3 follow-up suggested questions (`suggestedQuestions`) at the end of each response.
* **Token Quota Enforcement**: Chat token usage is deducted from the user's tier quota or the school's shared token pool.

### 2.4 Document Processing & RAG Knowledge Base
* **Source Documents**: Supports TEXT, MARKDOWN, and PDF formats containing verified historical records.
* **Vector Indexing**: Documents are sanitized, chunked, embedded via `bge-m3`, and stored in Supabase Vector DB to eliminate LLM hallucinations.

### 2.5 Quiz & Assessment Engine
* **Context-Bound Quizzes**: Multiple-choice quizzes associated with historical contexts.
* **Assessment Rules**:
  * Students submit quiz answers and receive instant scoring.
  * Detailed historical explanations are provided for each question.
  * Completion awards Experience Points (XP) and contributes to active Quests.

### 2.6 Gamification Engine
* **XP & Leveling System**: Earned through AI chat interactions, daily logins, quiz completions, and quest fulfillment.
* **Streaks & Daily Check-in**: Tracks consecutive days of active learning to drive retention.
* **Quests System**:
  * **Daily Quests**: Reset every 24 hours (e.g., *Chat 5 messages with a historical character*).
  * **Weekly Quests**: Reset every 7 days (e.g., *Complete 3 quizzes with >80% score*).
  * **Event Quests**: Published by Staff for special historical anniversaries.

### 2.7 Subscriptions & PayOS Payments
* **User Tiers**: `FREE` (Basic token limit), `PLUS`, `PRO` (Higher token limits, access to premium characters/contexts).
* **PayOS Payment Gateway**: Handles B2C tier purchases securely via QR code / Webhook integration.

### 2.8 Classroom SaaS Module (B2B Multi-tenant)
* **School Onboarding**: `SYSTEM_ADMIN` initializes schools with standardized codes (`{MA_TINH}_{CAP_HOC}_{TEN_RUT_GON}`).
* **School Roles**:
  * `SCHOOL_ADMIN`: Manages school accounts, teachers, and shared token quota.
  * `TEACHER`: Manages classrooms, assigns homework (AI chat tasks or quizzes).
  * `SCHOOL_STUDENT`: Logins via username (`{school_code}_hs_{ma_hs}`), completes class assignments.
* **Shared School Token Pool**: Token consumption by students is deducted from `school.monthly_token_quota`.
* **First-Login Password Enforcement**: Accounts initialized with default passwords must complete a mandatory password change (`is_first_login = true`) before accessing core APIs.

### 2.9 Administrative Oversight & System Trash
* **Dashboard Analytics**: Tracks active users, revenue, AI token usage, top historical characters, and chat metrics.
* **System Trash (`deleted_at`)**: Centralized soft-delete management allowing Staff/Admins to inspect, restore, or permanently purge deleted items.

---

## 3. ROLE-BASED ACCESS CONTROL (RBAC) MATRIX

| Business Feature / API Area | GUEST | CUSTOMER | SCHOOL_STUDENT | TEACHER | SCHOOL_ADMIN | STAFF / CONTENT_ADMIN | ADMIN / SYSTEM_ADMIN |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| View Public Contexts & Characters | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AI Character Chat | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| Complete Quizzes & Gamification | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| Purchase Tier via PayOS | ❌ | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Manage Classrooms & Assignments | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Manage School Accounts (Excel Import) | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ✅ |
| Manage Characters, Contexts, Docs | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| View Analytics & System Trash | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
