# Sprint 9 Implementation Plan: B2C/B2B Pricing Standardization, Customize B2B Plan, Request a Quote Flow & Unit/Integration Tests (Report 5)

> **Date:** 2026-11-06  
> **Author:** Trần Quốc Dinh (`DinhTQSE`)  
> **Specification Reference:** [`docs/sprints/sprint_9/business_flows/b2b_subscription_pricing_and_quote_specification.md`](../../sprints/sprint_9/business_flows/b2b_subscription_pricing_and_quote_specification.md)

---

## 1. Objectives & Locked-in Business Rules

1. **Unified Token & Infrastructure Pricing (B2C & B2B):**
   - **Token Unit Price:** **`0.15 VND / token`** (`150,000 VND / 1,000,000 tokens`).
   - **Base DB, Chat History, Quiz & Exam Mode Fee:** **`7,000 VND`** (unified across B2C `Plus`, `Pro` monthly plans and B2B per-student annual fee).
   - **B2C Plans (Monthly = 30 days, Yearly = 12 months with 15% discount):**
     - **`Free`:** `1,500 tokens/day` (`45,000/month`) — **`0 VND`**. Exam Mode: **Limited to 2 questions/messages per session** (`examQuestionCount <= 2`).
     - **`Plus`:** `5,000 tokens/day` (`150,000/month`) $\rightarrow$ `22,500 VND` (AI Token) + `7,000 VND` (DB/Quiz/Exam) = **`29,500 VND / month`** | Yearly (15% off): **`300,900 VND / year`**. Exam Mode & Quiz: **Unlimited**.
     - **`Pro`:** `10,000 tokens/day` (`300,000/month`) $\rightarrow$ `45,000 VND` (AI Token) + `7,000 VND` (DB/Quiz/Exam) = **`52,000 VND / month`** | Yearly (15% off): **`530,400 VND / year`**. Exam Mode & Quiz: **Unlimited**.
   - **B2B Per-User Annual Rates (Academic Year = 35 active weeks, 01/09 – 31/05):**
     - **1 Student (`STUDENT`):** `5,000 tokens/day` $\times$ `4 days/week` = `20,000 tokens/week` $\times$ `35 weeks` = **`700,000 tokens/year`** $\rightarrow$ `105,000 VND` (AI Token) + `7,000 VND` (DB/Quiz/Exam) = **`112,000 VND / student / year`**.
     - **1 Teacher (`TEACHER`):** `5,000 tokens/day` $\times$ `6 days/week` = `30,000 tokens/week` $\times$ `35 weeks` = **`1,050,000 tokens/year`** $\rightarrow$ `157,500 VND` (AI Token) + `42,500 VND` (LMS, Quiz Builder, S3 PDF/Video & Vector DB) = **`200,000 VND / teacher / year`**.
   - **B2B Volume Discount Tiers (by Student Count $S$):**
     - $S < 600$: **`0%`**
     - $600 \le S < 1,200$: **`10%`**
     - $1,200 \le S < 1,800$: **`20%`**
     - $S \ge 1,800$: **`30%`**
   - **3 Fixed B2B Packages & 1 Custom Package (`EnterprisePackage`):**
     - **`ENTERPRISE_SMALL`:** `600 students`, `15 teachers`, `435,750,000 tokens`, Subtotal `70,200,000 VND`, Discount `10%` $\rightarrow$ **`63,180,000 VND / year`**.
     - **`ENTERPRISE_MEDIUM`:** `1,400 students`, `35 teachers`, `1,016,750,000 tokens`, Subtotal `163,800,000 VND`, Discount `20%` $\rightarrow$ **`131,040,000 VND / year`**.
     - **`ENTERPRISE_LARGE`:** `2,000 students`, `50 teachers`, `1,452,500,000 tokens`, Subtotal `234,000,000 VND`, Discount `30%` $\rightarrow$ **`163,800,000 VND / year`**.
     - **`ENTERPRISE_CUSTOM`:** Dynamic student count $S$ & teacher count $T$ calculated via live quote calculator.

---

## 2. Database Migration (`V20261106150000__b2b_quote_request_and_updated_pricing.sql`)

1. **Update `historical_schema.tier` (B2C Pricing):**
   - `Free` (`00000000-0000-0000-0000-000000000001`): `amount = 0`, `limited_token = 1500`
   - `Plus Monthly` (`00000000-0000-0000-0000-000000000002`): `amount = 29500`, `no_month = 1`, `limited_token = 5000`
   - `Pro Monthly` (`00000000-0000-0000-0000-000000000003`): `amount = 52000`, `no_month = 1`, `limited_token = 10000`
   - `Plus Yearly` (`00000000-0000-0000-0000-000000000004`): `amount = 300900`, `no_month = 12`, `limited_token = 5000`
   - `Pro Yearly` (`00000000-0000-0000-0000-000000000005`): `amount = 530400`, `no_month = 12`, `limited_token = 10000`
2. **Update `school` table defaults:**
   - Default `default_daily_student_quota = 5000` and `default_daily_teacher_quota = 5000`.
   - Add optional `max_students INTEGER` and `max_teachers INTEGER` columns to `school`.
3. **Create `b2b_quote_request` table:**
   - Stores contact info (`first_name`, `last_name`, `contact_email`, `contact_phone`, `job_title`), school info (`school_name`, `school_code`, `school_address`), package & scale (`selected_package`, `number_of_students`, `number_of_teachers`, `student_daily_token_limit`, `teacher_daily_token_limit`), calculated breakdown (`total_token_pool`, `ai_token_cost_vnd`, `db_and_lms_cost_vnd`, `subtotal_vnd`, `discount_percent`, `discount_amount_vnd`, `final_price_vnd`), `promo_code`, `message`, `status` (`PENDING`, `CONTACTED`, `APPROVED`, `REJECTED`), `admin_note`, `reviewed_by_uid`, `reviewed_at`, `created_school_id`, `created_school_admin_uid`, `created_at`, `updated_at`.

---

## 3. Backend Components to Create / Modify

### 3.1. Enums & Entities
- **Modify [`EnterprisePackage.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/enums/EnterprisePackage.java):**
  - Update constants with new token pools, recommended students, recommended teachers, default daily student/teacher quotas (`5000`), and prices, plus add `ENTERPRISE_CUSTOM`.
- **Create `B2bQuoteStatus.java`:**
  - `PENDING`, `CONTACTED`, `APPROVED`, `REJECTED`.
- **Create `B2bQuoteRequest.java` (`entity/school/B2bQuoteRequest.java`):**
  - JPA entity mapping `b2b_quote_request`.
- **Modify [`School.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/school/School.java):**
  - Update default daily student/teacher quotas to `5000`, add `maxStudents` and `maxTeachers`.

### 3.2. Repositories
- **Create `B2bQuoteRequestRepository.java` (`repository/school/B2bQuoteRequestRepository.java`):**
  - Supports searching by `schoolName`, `contactEmail`, `quoteCode`, and filtering by `B2bQuoteStatus` with pagination.

### 3.3. DTOs (`dto/school/quote/`)
- `B2bPlanCatalogResponse.java`: Returns full B2B plan catalog (`Small`, `Medium`, `Large`, `Custom`) + unit economics constants (`0.15 VND/token`, `7,000 VND` student DB fee, `42,500 VND` teacher LMS fee, discount tiers).
- `CalculateB2bQuoteRequest.java`: Input for live calculator (`numberOfStudents`, `numberOfTeachers`, optional `selectedPackage`, optional `promoCode`).
- `B2bQuoteCalculationResponse.java`: Complete transparent breakdown (`studentTokensPerYear`, `teacherTokensPerYear`, `totalStudentTokens`, `totalTeacherTokens`, `totalTokenPool`, `aiTokenCostVnd`, `dbAndLmsCostVnd`, `subtotalVnd`, `discountPercent`, `discountAmountVnd`, `finalPriceVnd`, `averageMonthlyCostPerStudentVnd`).
- `SubmitB2bQuoteRequest.java`: Humy.ai-style `Request a Quote` form payload (`firstName`, `lastName`, `contactEmail`, `contactPhone`, `jobTitle`, `schoolName`, `schoolCode`, `schoolAddress`, `selectedPackage`, `numberOfStudents`, `numberOfTeachers`, `message`, `promoCode`).
- `B2bQuoteResponse.java`: Full quote details + status + provisioned school info.
- `UpdateB2bQuoteStatusRequest.java`: Admin status update (`CONTACTED`, `REJECTED`, `adminNote`, optional `customDiscountPercent`).
- `ApproveB2bQuoteResponse.java`: Returns approved quote + newly provisioned `SchoolResponse` + `SchoolAdminResponse` (including initial password).

### 3.4. Services
- **Create `B2bQuoteService.java` & `B2bQuoteServiceImpl.java` (`service/school/B2bQuoteServiceImpl.java`):**
  - `getB2bPlanCatalog()`: Returns fixed plans & pricing rules.
  - `calculateQuote(CalculateB2bQuoteRequest)`: Pure deterministic calculation of tokens, AI cost, DB/LMS cost, volume discount, promo code, and final price.
  - `submitQuoteRequest(SubmitB2bQuoteRequest)`: Validates input, calculates server-side pricing, generates unique `quoteCode` (`QT-2026-XXXX`), saves as `PENDING`.
  - `getQuoteRequests(B2bQuoteStatus, String search, Pageable)`: Paginated list for System Admin.
  - `getQuoteRequestById(UUID quoteId)`: Detailed view.
  - `updateQuoteStatus(UUID quoteId, UpdateB2bQuoteStatusRequest, UUID adminUid)`: Transitions to `CONTACTED` or `REJECTED`.
  - `approveQuoteAndProvisionSchool(UUID quoteId, UUID adminUid)`: Approves quote, automatically creates `School` with exact `totalTokenPool`, `defaultDailyStudentQuota = 5000`, `defaultDailyTeacherQuota = 5000`, `maxStudents`, `maxTeachers`, and creates the `SCHOOL_ADMIN` account for `contactEmail`.
- **Modify [`ChatSessionServiceImpl.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/chat/ChatSessionServiceImpl.java):**
  - Enforce `Free` B2C tier Exam Mode limit: max `2` questions per session (`examQuestionCount` capped at `2` when user is a `CUSTOMER` without an active paid `Plus`/`Pro` subscription), while `Plus`, `Pro`, and B2B school users remain unlimited.
- **Modify [`DailyTokenAllowanceServiceImpl.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/school/token/DailyTokenAllowanceServiceImpl.java) & [`SchoolManagementServiceImpl.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/school/SchoolManagementServiceImpl.java):**
  - Align default daily student & teacher quotas to `5000` and default package token pools to `435_750_000`, `1_016_750_000`, `1_452_500_000`.

### 3.5. Controllers & Security
- **Create `B2bQuoteController.java` (`controller/school/B2bQuoteController.java`):**
  - Public endpoints:
    - `GET /api/v1/b2b/plans`
    - `POST /api/v1/b2b/quotes/calculate`
    - `POST /api/v1/b2b/quotes/request`
  - System Admin endpoints (`@PreAuthorize("hasRole('SYSTEM_ADMIN')")`):
    - `GET /api/v1/admin/b2b-quotes`
    - `GET /api/v1/admin/b2b-quotes/{id}`
    - `PATCH /api/v1/admin/b2b-quotes/{id}/status`
    - `POST /api/v1/admin/b2b-quotes/{id}/approve`
- **Modify [`SecurityConfig.java`](../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java):**
  - Permit public access to `/api/v1/b2b/**` and `/Historical-tell/api/v1/b2b/**`.

---

## 4. Unit Tests & Integration Tests (For Report 5)

1. **`B2bQuoteServiceImplTest.java` (Unit Tests — 12 test cases):**
   - `getB2bPlanCatalog_ShouldReturnThreeFixedPlansAndPricingRules`
   - `calculateQuote_StarterScaleUnder600Students_ZeroDiscount`
   - `calculateQuote_SmallPackage600Students15Teachers_10PercentDiscount` (`435,750,000 tokens`, `Subtotal = 70,200,000`, `Final = 63,180,000`)
   - `calculateQuote_MediumPackage1400Students35Teachers_20PercentDiscount` (`1,016,750,000 tokens`, `Subtotal = 163,800,000`, `Final = 131,040,000`)
   - `calculateQuote_LargePackage2000Students50Teachers_30PercentDiscount` (`1,452,500,000 tokens`, `Subtotal = 234,000,000`, `Final = 163,800,000`)
   - `calculateQuote_Custom800Students20Teachers_10PercentDiscount` (`581,000,000 tokens`, `Subtotal = 93,600,000`, `Final = 84,240,000`)
   - `calculateQuote_WhenSelectedPackageIsFixed_UsesFixedPackageScaleIfNotOverridden`
   - `submitQuoteRequest_ValidHumyForm_SavesPendingQuoteWithGeneratedCode`
   - `updateQuoteStatus_FromPendingToContacted_UpdatesStatusAndNote`
   - `approveQuoteAndProvisionSchool_PendingQuote_CreatesSchoolAndSchoolAdmin`
   - `approveQuoteAndProvisionSchool_AlreadyApproved_ThrowsConflictException`
   - `approveQuoteAndProvisionSchool_RejectedQuote_ThrowsInvalidRequestException`
2. **`B2bQuoteControllerTest.java` (Integration Tests with MockMvc — 8 test cases):**
   - `getPlans_PublicAccess_Returns200`
   - `calculateQuote_PublicAccessValidInput_Returns200WithExactBreakdown`
   - `calculateQuote_InvalidStudentCount_Returns400`
   - `submitQuoteRequest_PublicAccessValidForm_Returns201`
   - `submitQuoteRequest_MissingRequiredContactEmail_Returns400`
   - `getQuotes_AsSystemAdmin_Returns200Paginated`
   - `updateQuoteStatus_AsSystemAdmin_Returns200`
   - `approveQuote_AsSystemAdmin_Returns200WithProvisionedSchoolAndAdmin`
