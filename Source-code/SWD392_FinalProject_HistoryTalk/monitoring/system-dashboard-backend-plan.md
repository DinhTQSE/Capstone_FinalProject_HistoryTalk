# System Dashboard Backend Plan

This plan is based on the current target ERD and actor definition provided by the team.

The System Dashboard is a **System Admin** feature.

For implementation, build the dashboard functions first. Authorization will be added after the APIs and data shape are stable.

## 1. Target Actors

The target actor model has four actors:

| Actor | Responsibility |
| --- | --- |
| Guest | Can view public account tiers/pricing and register a new account. |
| Customer | Uses learning features: history list, character chat/voice/talk, map interaction, quiz, and personal progress. Limited by daily token quota and can upgrade to premium. |
| Content Admin | Internal content operator. Manages core historical data such as Historical Context, Character, Location/Map data, and Quiz. |
| System Admin | Platform-level administrator. Manages identity, login/logout policy, overall platform analytics, user analytics, and revenue reports from tiers. |

System Dashboard belongs to:

```text
System Admin
```

## 2. Current Dashboard Decision

The first version of System Dashboard should be split into two groups:

- **Plan A - Do Now**
- **Plan B - Do Later**

This is necessary because several ERD areas are not implemented or not stable yet.

## 3. Plan A - Do Now

Plan A includes only features that can be built now without waiting for payment, package, token-cost, AI-cost, or quiz refactor work.

### Plan A Includes

- User analytics
- Account status summary
- Role distribution
- Content inventory summary
- Chat activity summary
- Basic system health summary
- Dashboard REST APIs for frontend

### Plan A Excludes

- Revenue
- Payment statistics
- Tier/package purchase analytics
- Token usage
- AI usage and cost
- Quiz analytics

## 4. Plan B - Do Later

Plan B includes features that depend on unfinished or unstable business modules.

### Plan B Includes

- Revenue dashboard
- Tier/package analytics
- Order/transaction analytics
- Token usage analytics
- AI usage and cost
- Quiz analytics

## 5. Why These Features Are Deferred

### Revenue

Revenue is deferred because:

- Payment function is not implemented yet.
- Tier/package purchase workflow is not ready.
- Order/transaction status rules are not finalized.

### Token Usage

Token usage is deferred because:

- Token counting algorithm is not finalized.
- Token deduction rules are not finalized.
- User daily quota and tier quota behavior need confirmation.

### AI Usage And Cost

AI cost is deferred because:

- Token usage is not finalized.
- Provider/model pricing is not finalized.
- Cost estimation formula is not finalized.

### Quiz Analytics

Quiz analytics is deferred because:

- Quiz business logic is being refactored.
- Building analytics now may create throwaway code.

## 6. Role And Authorization Direction

Target dashboard access:

```text
System Admin only
```

However, authorization will be added later.

### Current Implementation Rule

For the first implementation pass:

- Build dashboard APIs and data logic first.
- Do not block implementation on authorization.
- Add `@PreAuthorize` after the role model is finalized in backend.

### Future Authorization Rule

When authorization is added:

```java
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
```

Current backend role mismatch to resolve later:

| Target ERD/API Role | Current Backend Role |
| --- | --- |
| `CUSTOMER` | `CUSTOMER` |
| `CONTENT_ADMIN` | currently similar to `STAFF` |
| `SYSTEM_ADMIN` | currently similar to `ADMIN` |

This role mapping must be finalized before adding real authorization.

## 7. ERD Alignment Notes

The target ERD contains these dashboard-relevant entities:

- `User`
- `Tier`
- `Order`
- `Transaction`
- `HistoricalContext`
- `Character`
- `Document`
- `ChatSession`
- `Message`
- `Quiz`
- `QuizSession`
- `Question`
- `AnswerDetail`

For Plan A, use only stable areas:

- `User`
- `HistoricalContext`
- `Character`
- `Document`
- `ChatSession`
- `Message`

For Plan B, use later areas:

- `Tier`
- `Order`
- `Transaction`
- Token fields/cost logic
- Quiz-related tables

## 8. Plan A Dashboard Metrics

### 8.1 User Analytics

Metrics:

```text
totalUsers
activeUsers
inactiveUsers
deletedUsers
customers
contentAdmins
systemAdmins
newUsersToday
newUsersThisMonth
```

Source:

```text
User
```

Target ERD fields:

```text
uid
role
is_active
created_date
updated_date
deleted_date
last_active_date
```

Current backend note:

- Existing backend may still use older fields such as `deleted_at`.
- Before implementation, verify current table columns in Supabase.
- If needed, create migration to align the user table with the target ERD.

### 8.2 Role Distribution

Metrics:

```text
usersByRole
```

Expected roles:

```text
CUSTOMER
CONTENT_ADMIN
SYSTEM_ADMIN
```

Temporary backend mapping if needed:

```text
CUSTOMER -> CUSTOMER
STAFF -> CONTENT_ADMIN
ADMIN -> SYSTEM_ADMIN
```

### 8.3 Account Status Summary

Metrics:

```text
activeAccounts
inactiveAccounts
deletedAccounts
recentlyActiveUsers
```

Source:

```text
User.is_active
User.deleted_date
User.last_active_date
```

Current backend note:

- If `last_active_date` is not implemented yet, skip recently active users in the first API version or return `0`.

### 8.4 Content Inventory Summary

Metrics:

```text
totalHistoricalContexts
publishedHistoricalContexts
activeHistoricalContexts
totalCharacters
publishedCharacters
activeCharacters
totalDocuments
activeDocuments
```

Sources:

```text
HistoricalContext
Character
Document
```

Target ERD fields:

```text
is_published
is_active
created_date
updated_date
deleted_date
```

Current backend note:

- The current backend may still use `HistoricalContextDocument` and `CharacterDocument` instead of a single ERD `Document` table.
- For Plan A, count documents using the current implemented document tables.

### 8.5 Chat Activity Summary

Metrics:

```text
totalChatSessions
activeChatSessions
totalMessages
userMessages
aiMessages
chatSessionsToday
messagesToday
```

Sources:

```text
ChatSession
Message
```

Target ERD fields:

```text
ChatSession.created_date
ChatSession.is_active
ChatSession.deleted_date
Message.created_date
Message.is_from_ai
Message.is_active
Message.deleted_date
```

Current backend note:

- Existing message entity may use `timestamp` instead of `created_date`.
- Use existing fields first, then align later if the team approves ERD migration.

### 8.6 Basic System Health Summary

Metrics:

```text
backendStatus
uptime
jvmMemoryUsed
jvmMemoryMax
httpRequestCount
httpErrorCount
lastCheckedAt
```

Source:

```text
Spring Boot Actuator / Micrometer
```

Notes:

- Grafana remains the detailed technical monitoring tool.
- The in-app System Dashboard only needs a simple health summary.

## 9. Plan A API Proposal

All endpoints follow the API contract response wrapper:

```json
{
  "success": true,
  "message": "string",
  "data": {},
  "timestamp": "ISO8601"
}
```

Recommended endpoint prefix:

```text
/api/v1/system-admin/dashboard
```

If frontend prefers the previous naming, use:

```text
/api/v1/admin/dashboard
```

The team should choose one before implementation.

### 9.1 Overview API

```text
GET /api/v1/system-admin/dashboard/overview
```

Purpose:

- Return first-screen dashboard cards.

Suggested response:

```json
{
  "users": {
    "total": 0,
    "active": 0,
    "inactive": 0,
    "deleted": 0,
    "newToday": 0,
    "newThisMonth": 0
  },
  "roles": {
    "customers": 0,
    "contentAdmins": 0,
    "systemAdmins": 0
  },
  "content": {
    "historicalContexts": 0,
    "publishedHistoricalContexts": 0,
    "characters": 0,
    "publishedCharacters": 0,
    "documents": 0
  },
  "chat": {
    "sessions": 0,
    "messages": 0,
    "messagesToday": 0
  },
  "systemHealth": {
    "status": "UP",
    "lastCheckedAt": "2026-05-22T10:00:00Z"
  }
}
```

### 9.2 User Analytics API

```text
GET /api/v1/system-admin/dashboard/users
```

Query params:

```text
from=2026-05-01
to=2026-05-22
granularity=day|week|month
```

Suggested response:

```json
{
  "summary": {
    "total": 0,
    "active": 0,
    "inactive": 0,
    "deleted": 0
  },
  "byRole": [
    { "role": "CUSTOMER", "count": 0 },
    { "role": "CONTENT_ADMIN", "count": 0 },
    { "role": "SYSTEM_ADMIN", "count": 0 }
  ],
  "trend": [
    { "date": "2026-05-22", "newUsers": 0, "activeUsers": 0 }
  ]
}
```

### 9.3 Content Summary API

```text
GET /api/v1/system-admin/dashboard/content
```

Suggested response:

```json
{
  "historicalContexts": {
    "total": 0,
    "published": 0,
    "active": 0
  },
  "characters": {
    "total": 0,
    "published": 0,
    "active": 0
  },
  "documents": {
    "total": 0,
    "active": 0
  }
}
```

### 9.4 Chat Activity API

```text
GET /api/v1/system-admin/dashboard/chat-activity
```

Query params:

```text
from=2026-05-01
to=2026-05-22
granularity=day|week|month
```

Suggested response:

```json
{
  "summary": {
    "sessions": 0,
    "messages": 0,
    "userMessages": 0,
    "aiMessages": 0
  },
  "trend": [
    { "date": "2026-05-22", "sessions": 0, "messages": 0 }
  ]
}
```

### 9.5 System Health API

```text
GET /api/v1/system-admin/dashboard/system-health
```

Suggested response:

```json
{
  "status": "UP",
  "uptime": "string",
  "jvmMemoryUsed": 0,
  "jvmMemoryMax": 0,
  "httpRequestCount": 0,
  "httpErrorCount": 0,
  "lastCheckedAt": "2026-05-22T10:00:00Z"
}
```

## 10. Plan A Backend Structure

Follow the current backend package style.

Suggested structure:

```text
controller/dashboard
service/dashboard
dto/dashboard
repository/dashboard
```

Suggested classes:

```text
DashboardController
DashboardOverviewService
DashboardUserAnalyticsService
DashboardContentService
DashboardChatActivityService
DashboardSystemHealthService
```

Suggested DTO files:

```text
DashboardOverviewResponse
DashboardUserAnalyticsResponse
DashboardContentSummaryResponse
DashboardChatActivityResponse
DashboardSystemHealthResponse
DashboardTrendPoint
DashboardRoleCount
```

## 11. Plan A Database Work

Plan A should avoid new tables unless the current schema is missing required ERD fields.

### 11.1 Required Schema Check

Before implementation, verify Supabase/current DB columns for:

```text
User.created_date
User.updated_date
User.deleted_date
User.is_active
User.last_active_date
HistoricalContext.is_active
HistoricalContext.is_published
Character.is_active
Character.is_published
ChatSession.created_date
Message.created_date or Message.timestamp
```

### 11.2 Possible Migration

If the current DB does not match the target ERD, create migration:

```text
V10__align_dashboard_required_columns.sql
```

Only add fields needed for Plan A.

Do not add payment/token/quiz analytics tables in Plan A.

## 12. Plan A Implementation Order

1. Finalize endpoint prefix with frontend:

```text
/api/v1/system-admin/dashboard
```

or

```text
/api/v1/admin/dashboard
```

2. Inspect current Supabase schema and current JPA entities.
3. Add minimal migration only if required for dashboard fields.
4. Create dashboard DTOs.
5. Create dashboard repository queries or repository projection methods.
6. Implement user analytics service.
7. Implement content summary service.
8. Implement chat activity service.
9. Implement system health service.
10. Implement dashboard controller.
11. Return all responses through `ApiResponse`.
12. Test APIs through Swagger.
13. Add authorization later after role mapping is finalized.

## 13. Plan B Details

### 13.1 Revenue Dashboard

Wait until these ERD modules are implemented:

```text
Tier
Order
Transaction
```

Future metrics:

```text
totalRevenue
revenueToday
revenueThisMonth
ordersByStatus
transactionsByStatus
revenueByTier
```

### 13.2 Tier And Package Analytics

Wait until tier purchase and upgrade flow is stable.

Future metrics:

```text
usersByTier
activeTierCount
mostPurchasedTier
freeToPaidConversion
```

### 13.3 Token Usage

Wait until token rules are finalized.

Future metrics:

```text
tokensUsedToday
tokensUsedThisMonth
tokensByUser
tokensByTier
remainingTokenDistribution
```

### 13.4 AI Usage And Cost

Wait until token usage and model pricing are finalized.

Future metrics:

```text
aiRequests
aiFailures
promptTokens
completionTokens
estimatedCost
costByModel
```

### 13.5 Quiz Analytics

Wait until quiz refactor is complete.

Future metrics:

```text
quizSessions
quizCompletionRate
averageScore
popularQuizzes
questionWrongRate
```

## 14. Plan B Implementation Order

1. Finish Tier, Order, Transaction modules.
2. Implement revenue dashboard.
3. Finalize token algorithm.
4. Implement token usage tracking.
5. Finalize AI cost formula.
6. Implement AI usage and cost dashboard.
7. Finish quiz refactor.
8. Implement quiz analytics.

## 15. Review Checklist

Before starting implementation, confirm:

- Which endpoint prefix should frontend use?
- Should Plan A use target roles in response: `CUSTOMER`, `CONTENT_ADMIN`, `SYSTEM_ADMIN`?
- Should current backend roles be mapped temporarily from `STAFF` and `ADMIN`?
- Which user status field is the source of truth: `is_active`, `deleted_date`, or current `deleted_at`?
- Does the current DB already have `created_date` for User, ChatSession, and Message?
- Should system health use Micrometer/MeterRegistry in Plan A or return a simpler health response first?
- Is authorization definitely postponed until after functional APIs are finished?
