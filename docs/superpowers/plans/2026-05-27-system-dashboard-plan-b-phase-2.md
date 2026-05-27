# System Dashboard Plan B - Phase 2

Date: 2026-05-27

Status: planning for review

Related old plan:

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/system-dashboard-backend-plan.md
```

## 1. Current Decision

Plan A is already implemented for:

- User analytics
- Content summary
- Chat activity
- Basic system health

Plan B can now move forward because the backend now has:

- Payment/revenue entities and service flow
- Tier/package entities
- Quiz entities and service flow

Token usage and AI cost are still not ready for direct dashboard implementation because the Python AI service does not return `tokenUsage` yet, even though the Java backend is already prepared to receive it.

Updated Phase 2B decision:

- Add `tokenUsage` to `message`.
- If `message.is_from_ai = false`, `message.token_usage` is prompt token usage.
- If `message.is_from_ai = true`, `message.token_usage` is completion token usage.
- `user.token` is the remaining token balance of the user.
- Dashboard token usage should read from `message`, not from a separate token audit table in the MVP.

## 2. Source Review Summary

### Payment And Revenue

Current Java source has payment modules:

```text
entity/payment/Tier.java
entity/payment/UserTier.java
entity/payment/PaymentOrder.java
entity/payment/PaymentTransaction.java
repository/payment/TierRepository.java
repository/payment/UserTierRepository.java
repository/payment/PaymentOrderRepository.java
repository/payment/PaymentTransactionRepository.java
service/payment/PaymentService.java
service/payment/PaymentWebhookService.java
```

Dashboard-relevant fields:

```text
payment_order.amount
payment_order.status
payment_order.paid_at
payment_order.created_at
payment_order.tier_id
payment_order.uid
payment_transaction.status
payment_transaction.transaction_date
tier.title
tier.amount
tier.limited_token
user_tier.start_time
user_tier.end_time
user_tier.is_active
```

Revenue source of truth:

```text
payment_order where status = 'PAID' and deleted_at is null
```

Use `payment_transaction` for gateway audit and transaction status counts, not as the primary revenue source, to avoid double counting if gateway callbacks are duplicated.

### Quiz

Current Java source has quiz modules:

```text
entity/quiz/Quiz.java
entity/quiz/Question.java
entity/quiz/QuizSession.java
entity/quiz/QuizAnswerDetail.java
repository/QuizRepository.java
repository/QuestionRepository.java
repository/QuizSessionRepository.java
service/quiz/QuizServiceImpl.java
```

Dashboard-relevant fields:

```text
quiz.quiz_id
quiz.title
quiz.level
quiz.is_published
quiz.created_at
quiz.deleted_at
quiz_session.session_id
quiz_session.quiz_id
quiz_session.uid
quiz_session.start_time
quiz_session.end_time
quiz_session.score
quiz_session.created_at
quiz_answer_detail.question_id
quiz_answer_detail.session_id
quiz_answer_detail.is_correct
question.quiz_id
question.deleted_at
```

Missing repository for analytics:

```text
QuizAnswerDetailRepository
```

Add it in Plan B to query wrong-answer rates and per-question accuracy.

### AI Token Usage

Java backend already expects token usage:

```text
service/chat/AiServiceClient.java
service/chat/AiMetricsService.java
```

Java expected response shape:

```json
{
  "tokenUsage": {
    "provider": "ollama",
    "model": "qwen2.5:14b",
    "promptTokens": 100,
    "completionTokens": 50,
    "totalTokens": 150
  }
}
```

Current Python AI response does not include it:

```text
presentation/chat/schemas.py
presentation/chat/router.py
application/chat/service.py
```

Current Python response data:

```text
ChatResponseData:
  message
  suggestedQuestions

GenerateTitleResponseData:
  title
```

Current `_call_ollama()` only returns:

```python
data.get("message", {}).get("content", "")
```

So Java receives `tokenUsage = null`, and `AiMetricsService.recordTokens()` does nothing.

Confirmed backend storage direction:

```text
message.token_usage
```

Token interpretation:

```text
message.is_from_ai = false -> promptTokens
message.is_from_ai = true  -> completionTokens
```

User balance:

```text
user.token = remaining tokens
```

## 3. Plan B Phase 2 Scope

### Include Now

- Revenue dashboard
- Payment order analytics
- Payment transaction analytics
- Tier/package analytics
- Quiz analytics
- AI request counters from Micrometer if available
- Token balance summary from current `user.token`

### Include After AI Token Pipeline

- Prompt tokens
- Completion tokens
- Total AI tokens
- Estimated AI cost
- Token usage by model/provider
- Token usage by user/session
- Token usage trend
- Remaining token distribution from `user.token`

## 4. API Contract Proposal

All endpoints keep the current prefix:

```text
/api/v1/system-admin/dashboard
```

All endpoints require:

```text
SYSTEM_ADMIN
```

Common query params for time-series endpoints:

```text
from=2026-05-01
to=2026-05-27
granularity=day|week|month
```

Default date behavior should match Plan A:

```text
from = to - 29 days
to = today
max range = 180 days
```

### 4.1 Revenue

```text
GET /api/v1/system-admin/dashboard/revenue
```

Response shape:

```json
{
  "summary": {
    "totalRevenue": 0,
    "revenueToday": 0,
    "revenueThisMonth": 0,
    "paidOrders": 0,
    "averageOrderValue": 0
  },
  "ordersByStatus": [
    { "status": "PENDING", "count": 0 },
    { "status": "PAID", "count": 0 },
    { "status": "CANCELLED", "count": 0 },
    { "status": "EXPIRED", "count": 0 },
    { "status": "FAILED", "count": 0 }
  ],
  "revenueByTier": [
    { "tierId": "uuid", "tierTitle": "plus", "revenue": 0, "paidOrders": 0 }
  ],
  "trend": [
    { "date": "2026-05-27", "revenue": 0, "paidOrders": 0 }
  ]
}
```

Rules:

- Revenue only counts `payment_order.status = PAID`.
- Use `paid_at` for revenue period.
- If `paid_at` is null on old data, fallback to `updated_at` only after confirming with team.
- Do not count `PENDING`, `CANCELLED`, `EXPIRED`, or `FAILED` as revenue.

### 4.2 Payments

```text
GET /api/v1/system-admin/dashboard/payments
```

Response shape:

```json
{
  "summary": {
    "totalOrders": 0,
    "pendingOrders": 0,
    "paidOrders": 0,
    "cancelledOrders": 0,
    "expiredOrders": 0,
    "failedOrders": 0,
    "successfulTransactions": 0,
    "failedTransactions": 0
  },
  "transactionTrend": [
    { "date": "2026-05-27", "success": 0, "failed": 0 }
  ]
}
```

Rules:

- Payment order status comes from `payment_order.status`.
- Transaction status comes from `payment_transaction.status`.
- Transaction count is for gateway audit, not revenue.

### 4.3 Tiers

```text
GET /api/v1/system-admin/dashboard/tiers
```

Response shape:

```json
{
  "summary": {
    "activeTiers": 0,
    "currentPaidUsers": 0,
    "currentFreeUsers": 0,
    "activeSubscriptions": 0,
    "expiringSoonSubscriptions": 0,
    "freeToPaidConversionRate": 0.0
  },
  "usersByTier": [
    { "tierId": "uuid", "tierTitle": "free", "users": 0 }
  ],
  "purchasesByTier": [
    { "tierId": "uuid", "tierTitle": "plus", "paidOrders": 0, "revenue": 0 }
  ]
}
```

Rules:

- Current user tier comes from `user.tier_id`.
- Active subscription comes from `user_tier.is_active = true`, `deleted_at is null`, and `end_time >= now`.
- Paid tier means `tier.amount > 0`.
- Free-to-paid conversion rate:

```text
distinct customers with at least one PAID order for a paid tier / total customers
```

### 4.4 Quiz Analytics

```text
GET /api/v1/system-admin/dashboard/quiz
```

Response shape:

```json
{
  "summary": {
    "totalQuizzes": 0,
    "publishedQuizzes": 0,
    "draftQuizzes": 0,
    "deletedQuizzes": 0,
    "startedSessions": 0,
    "completedSessions": 0,
    "completionRate": 0.0,
    "averageScorePercentage": 0.0
  },
  "sessionsTrend": [
    { "date": "2026-05-27", "started": 0, "completed": 0 }
  ],
  "topQuizzes": [
    {
      "quizId": "uuid",
      "title": "string",
      "level": "EASY",
      "startedSessions": 0,
      "completedSessions": 0,
      "averageScorePercentage": 0.0
    }
  ],
  "topWrongQuestions": [
    {
      "questionId": "uuid",
      "quizId": "uuid",
      "quizTitle": "string",
      "wrongAnswers": 0,
      "totalAnswers": 0,
      "wrongRate": 0.0
    }
  ]
}
```

Rules:

- Started sessions count `quiz_session.deleted_at is null`.
- Completed sessions count `end_time is not null` and `deleted_at is null`.
- Completion rate:

```text
completedSessions / startedSessions
```

- Score percentage should be computed against active question count for that quiz:

```text
quiz_session.score / activeQuestionCount
```

- `QuizAnswerDetailRepository` is required for question wrong-rate queries.

### 4.5 AI Usage

```text
GET /api/v1/system-admin/dashboard/ai-usage
```

Phase 2 initial response:

```json
{
  "summary": {
    "aiRequests": 0,
    "aiFailures": 0,
    "successRate": 0.0,
    "promptTokens": 0,
    "completionTokens": 0,
    "totalTokens": 0,
    "estimatedCost": 0
  },
  "requestsByOperation": [
    { "operation": "chat", "success": 0, "failure": 0 },
    { "operation": "generate_title", "success": 0, "failure": 0 }
  ],
  "tokensByModel": [
    { "provider": "ollama", "model": "qwen2.5:14b", "promptTokens": 0, "completionTokens": 0, "totalTokens": 0, "estimatedCost": 0 }
  ],
  "trend": [
    { "date": "2026-05-27", "requests": 0, "failures": 0, "tokens": 0, "estimatedCost": 0 }
  ]
}
```

Important limitation:

- Request counters can come from Micrometer.
- Historical token data cannot be reliably returned from REST API until token usage is persisted into `message.token_usage`.
- Model/provider breakdown requires either storing provider/model on message or adding another metadata source later. MVP can report prompt/completion/total tokens without provider/model split.

## 5. Required AI Token Pipeline

### 5.1 Python AI Changes

Add response models:

```python
class TokenUsage(BaseModel):
    provider: str
    model: str
    promptTokens: int = 0
    completionTokens: int = 0
    totalTokens: int = 0
```

Update:

```text
ChatResponseData.tokenUsage
GenerateTitleResponseData.tokenUsage
```

Change `_call_ollama()` to return both content and usage metadata.

Ollama chat responses usually include token-like counters such as:

```text
prompt_eval_count
eval_count
```

Mapping:

```text
promptTokens = prompt_eval_count
completionTokens = eval_count
totalTokens = prompt_eval_count + eval_count
provider = "ollama"
model = settings.LLM_MODEL
```

If a provider does not return usage:

- Return zero tokens.
- Add a warning log.
- Do not estimate silently unless the team explicitly approves a tokenizer fallback.

### 5.2 Java Backend Changes

Current Java only records token usage into Micrometer:

```text
historytalk.ai.tokens
```

For business dashboard MVP, add token persistence to `message`:

```text
message.token_usage INT NOT NULL DEFAULT 0
```

Java entity field:

```text
Message.tokenUsage
```

Implementation notes:

- Use Flyway migration to add `token_usage` to `message`.
- Keep all existing UUID primary key types unchanged.
- Python returns `promptTokens`, `completionTokens`, and `totalTokens`.
- Java saves the user message with `tokenUsage = promptTokens`.
- Java saves the assistant message with `tokenUsage = completionTokens`.
- Java deducts `user.token` by `totalTokens`.
- Java still records Micrometer counters for Grafana.
- Failed AI calls should not create completion token usage. The already-saved user message can keep `tokenUsage = 0` unless prompt token counting is available before the AI call returns.

Dashboard token calculations:

```text
promptTokens     = SUM(message.token_usage WHERE is_from_ai = false)
completionTokens = SUM(message.token_usage WHERE is_from_ai = true)
totalTokens      = SUM(message.token_usage)
remainingTokens  = SUM(user.token) or per-user distribution from user.token
```

Recommended user token deduction rule:

```text
remaining = max(0, currentUser.token - tokenUsage.totalTokens)
```

Open decision:

```text
Should the chat request be blocked when currentUser.token <= 0, or should it still allow one final request and clamp to 0 after deduction?
```

Recommended MVP:

```text
Block when user.token <= 0 before calling AI.
After AI returns, deduct totalTokens and clamp to 0.
```

### 5.3 Cost Calculation

For Ollama/self-hosted:

```text
estimatedCost = 0 by default
```

Reason:

- Ollama has infrastructure cost, not provider per-token billing by default.
- Token count is still useful for usage, quota, and performance monitoring.

If later using OpenAI/Gemini:

Add config-driven pricing:

```text
ai_pricing_config
```

Suggested fields:

```text
provider
model
prompt_price_per_1k_tokens
completion_price_per_1k_tokens
currency
is_active
created_at
updated_at
deleted_at
```

Cost formula:

```text
promptCost = promptTokens / 1000 * promptPrice
completionCost = completionTokens / 1000 * completionPrice
estimatedCost = promptCost + completionCost
```

## 6. Repository And Service Work

### 6.1 New Repository Queries

Payment:

```text
PaymentOrderRepository
PaymentTransactionRepository
TierRepository
UserTierRepository
```

Add projection queries for:

- Revenue summary
- Revenue trend by period
- Revenue by tier
- Orders by status
- Transaction trend
- Active subscriptions
- Users by tier

Quiz:

```text
QuizRepository
QuizSessionRepository
QuestionRepository
QuizAnswerDetailRepository
```

Add projection queries for:

- Quiz summary
- Started/completed session trend
- Top quizzes by completed sessions
- Average score percentage
- Top wrong questions

AI:

```text
MessageRepository
UserRepository
```

Add projection queries for:

- Token totals
- Prompt tokens from user messages
- Completion tokens from AI messages
- Total tokens by period
- Token usage by user/session through message -> chat_session -> user
- Remaining token summary from `user.token`
- AI usage trend
- Estimated cost trend only if cost formula is later approved

### 6.2 Service Structure

Keep current package direction:

```text
dto/dashboard
repository/dashboard
service/dashboard
controller/dashboard
```

Add focused DTOs:

```text
DashboardRevenueResponse
DashboardPaymentResponse
DashboardTierAnalyticsResponse
DashboardQuizAnalyticsResponse
DashboardAiUsageResponse
```

Extend service interface:

```java
DashboardRevenueResponse getRevenue(LocalDate from, LocalDate to, String granularity);
DashboardPaymentResponse getPayments(LocalDate from, LocalDate to, String granularity);
DashboardTierAnalyticsResponse getTiers(LocalDate from, LocalDate to, String granularity);
DashboardQuizAnalyticsResponse getQuiz(LocalDate from, LocalDate to, String granularity);
DashboardAiUsageResponse getAiUsage(LocalDate from, LocalDate to, String granularity);
```

## 7. Overview Enrichment

After Plan B endpoints are stable, extend:

```text
GET /api/v1/system-admin/dashboard/overview
```

Add optional cards:

```json
{
  "revenue": {
    "totalRevenue": 0,
    "revenueThisMonth": 0,
    "paidOrders": 0
  },
  "quiz": {
    "publishedQuizzes": 0,
    "completedSessions": 0,
    "averageScorePercentage": 0.0
  },
  "ai": {
    "aiRequests": 0,
    "aiFailures": 0,
    "totalTokens": 0,
    "estimatedCost": 0
  }
}
```

Do this after FE confirms which cards are shown on the first screen.

## 8. Implementation Order

### Step 1 - Finalize Metrics

- [ ] Confirm FE wants separate tabs/cards for `revenue`, `payments`, `tiers`, `quiz`, `ai-usage`.
- [ ] Confirm currency display is VND.
- [ ] Confirm revenue source is `payment_order.status = PAID`.
- [ ] Confirm `paid_at` is required for revenue trend.
- [ ] Confirm score display uses percentage.

### Step 2 - Payment/Revenue Dashboard

- [ ] Add payment dashboard projections.
- [ ] Add `DashboardRevenueResponse`.
- [ ] Add `DashboardPaymentResponse`.
- [ ] Add service methods.
- [ ] Add controller endpoints.
- [ ] Add Swagger annotations.
- [ ] Verify with Supabase sample data.

### Step 3 - Tier Analytics

- [ ] Add tier/user tier projections.
- [ ] Add `DashboardTierAnalyticsResponse`.
- [ ] Compute current users by tier.
- [ ] Compute active subscriptions.
- [ ] Compute free-to-paid conversion.
- [ ] Verify paid tier logic with `tier.amount > 0`.

### Step 4 - Quiz Analytics

- [ ] Add `QuizAnswerDetailRepository`.
- [ ] Add quiz/session/question projections.
- [ ] Add `DashboardQuizAnalyticsResponse`.
- [ ] Compute completion rate.
- [ ] Compute average score percentage.
- [ ] Compute top quizzes.
- [ ] Compute top wrong questions.
- [ ] Verify against quiz history data.

### Step 5 - AI Token Usage Contract

- [ ] Update Python schemas to include `tokenUsage`.
- [ ] Change `_call_ollama()` to preserve usage metadata.
- [ ] Return token usage from `/v1/ai/chat`.
- [ ] Return token usage from `/v1/ai/generate-title`.
- [ ] Add AI service tests or manual sample response.
- [ ] Verify Java receives non-null `tokenUsage`.

### Step 6 - Message Token Persistence And User Balance

- [ ] Add Flyway migration for `message.token_usage INT NOT NULL DEFAULT 0`.
- [ ] Add `tokenUsage` field to `Message` entity.
- [ ] Save user message `tokenUsage = promptTokens`.
- [ ] Save assistant message `tokenUsage = completionTokens`.
- [ ] Deduct `user.token` by `totalTokens`.
- [ ] Block chat request when `user.token <= 0`.
- [ ] Add dashboard queries on `message.token_usage`.
- [ ] Keep Micrometer counters for Grafana.
- [ ] Add dashboard `ai-usage` endpoint.

### Step 7 - API Contract

- [ ] After review approval, update `docs/API_CONTRACT.md`.
- [ ] Add Plan B endpoints and response examples.
- [ ] Mark cost fields as zero or hidden until cost formula is approved.
- [ ] Document that prompt/completion token usage comes from `message.tokenUsage`.
- [ ] Document that `user.token` is remaining token balance.
- [ ] Sync with FE contract/openapi if FE wants generated OpenAPI shape.

### Step 8 - Validation

- [ ] `mvn -q -DskipTests compile`
- [ ] Run Spring Boot with prod/local profile.
- [ ] Login as `SYSTEM_ADMIN`.
- [ ] Test all Plan B endpoints through Swagger.
- [ ] Validate dashboard numbers against Supabase queries.
- [ ] Start AI service and verify chat response includes `tokenUsage`.
- [ ] Verify Grafana still receives Micrometer AI counters.

## 9. Risks And Decisions Needed

### Risk 1 - AI Cost Is Not Real Cost Yet

Current AI provider is Ollama. For Ollama, token usage can be measured, but `estimatedCost` should stay `0` unless the team defines an infrastructure-cost formula.

Decision needed:

```text
Should Ollama cost be displayed as 0, hidden, or estimated by internal infra cost?
```

### Risk 2 - User Token Is Remaining Balance

`user.token` is confirmed as the remaining token balance. Token usage history should not be read from this field. Usage history should be read from `message.token_usage`.

Implementation rule:

```text
message.is_from_ai = false -> prompt token usage
message.is_from_ai = true  -> completion token usage
user.token                 -> remaining token balance
```

### Risk 3 - Revenue Period Field

Revenue trend should use `paid_at`. If existing paid records have null `paid_at`, the dashboard may undercount historical revenue.

Decision needed:

```text
Should old paid orders with null paid_at fallback to updated_at?
```

### Risk 4 - Quiz Score Percent

`quiz_session.score` stores number of correct answers, not percentage. Percent requires active question count.

Decision needed:

```text
Should deleted questions be excluded from historical score percentage?
```

Recommended default:

```text
Use current non-deleted questions for MVP, then add stored total_questions later if historical accuracy becomes important.
```

## 10. Recommended Phase 2 Split

### Phase 2A - Business Metrics Ready Now

Implement first:

- Revenue
- Payments
- Tiers
- Quiz

Reason:

- Required source tables exist.
- Metrics can be computed from current DB.
- No cross-service contract is required.

### Phase 2B - AI/Token Metrics After Contract Fix

Implement second:

- Python `tokenUsage`
- Java `message.token_usage` persistence
- User token deduction from `user.token`
- AI usage/cost endpoint
- Token usage endpoint

Reason:

- Current Python response does not return token data.
- Java Micrometer counters alone are not enough for business dashboard history.
- The confirmed MVP source of truth for token usage history is `message.token_usage`.
- Cost rules are not finalized for Ollama.
