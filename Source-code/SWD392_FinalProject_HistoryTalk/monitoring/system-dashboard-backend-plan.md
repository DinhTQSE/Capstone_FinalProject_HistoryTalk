# System Dashboard Backend Plan

This document defines the proposed backend implementation plan for the HistoryTalk System Dashboard module.

No code should be implemented from this plan until the team reviews and approves the scope.

## 1. Goal

Build an in-app System Dashboard for Admin/Staff users in HistoryTalk.

The dashboard should support:

- User statistics
- Reported users
- Token usage
- Revenue
- AI usage and cost
- System health summary
- Activity trends

Grafana remains a technical monitoring tool for developers or technical admins. It should not be used as the main business dashboard for Staff/Admin users.

## 2. Design Direction

The project should separate two dashboard concerns:

| Area | Tool | Audience | Purpose |
| --- | --- | --- | --- |
| Technical monitoring | Grafana + Prometheus | Developers, technical admins | JVM, HTTP latency, backend health, AI technical metrics |
| Business dashboard | HistoryTalk Web App + Java REST APIs | Admin, Staff | Users, revenue, reports, token usage, activity summaries |

This avoids:

- Heavy Grafana SQL queries on production business tables
- Complicated Grafana iframe authentication
- Mixing DevOps monitoring with business workflows
- Performance issues caused by dashboard queries against raw tables

## 3. Phase 1: Review Data Sources

Review the current Java backend source code to identify which data already exists and which data is missing.

Areas to review:

- User/account
- Role
- Chat session/message
- Payment/transaction/subscription
- Quiz/activity
- Report user/moderation
- AI call/token usage

Expected output:

```text
Dashboard metric -> source table -> query logic -> missing data
```

Example:

```text
Total users -> users table -> count all active users -> available
Daily new users -> users.created_at -> group by day -> available if created_at exists
Revenue -> payments table -> sum successful payments -> depends on payment table
Reported users -> user_reports table -> count pending/resolved reports -> depends on report module
AI tokens -> ai_token_usage table -> sum tokens by day/model/user -> table may need to be added
```

## 4. Phase 2: Define API Contract

Design REST APIs before implementation so the frontend can develop against a stable contract.

Proposed endpoints:

```text
GET /api/v1/admin/dashboard/overview
GET /api/v1/admin/dashboard/users
GET /api/v1/admin/dashboard/revenue
GET /api/v1/admin/dashboard/tokens
GET /api/v1/admin/dashboard/reports
GET /api/v1/admin/dashboard/system-health
```

Common query parameters:

```text
from=2026-05-01
to=2026-05-20
granularity=day|week|month
```

API responses should be frontend-friendly. The backend should return data that can be rendered directly as cards, tables, and charts.

The frontend should not need to perform complex calculations.

## 5. Phase 3: Database Design For Analytics

Avoid querying raw production tables every time the dashboard is opened.

Recommended approach:

- Store raw/audit events where necessary.
- Aggregate data into daily summary tables.
- Let dashboard APIs read from summary tables.

Proposed tables:

```text
ai_token_usage
daily_user_stats
daily_revenue_stats
daily_report_stats
daily_ai_usage_stats
system_usage_summary
```

### Table Purpose

| Table | Purpose |
| --- | --- |
| `ai_token_usage` | Raw AI token usage record for each AI call |
| `daily_user_stats` | Daily user totals, new users, active users |
| `daily_revenue_stats` | Daily revenue and payment summary |
| `daily_report_stats` | Daily report user/moderation summary |
| `daily_ai_usage_stats` | Daily AI requests, token totals, estimated cost |
| `system_usage_summary` | General dashboard summary data |

### Business Rules

- Aggregation jobs must be idempotent.
- Running the same aggregation twice must not duplicate data.
- Revenue only counts successful payments.
- Token usage should store:
  - provider
  - model
  - prompt tokens
  - completion tokens
  - total tokens
  - estimated cost
- Dashboard date range should be limited, for example 90 or 180 days.
- Admin-only data such as revenue and cost should be protected more strictly than general stats.

## 6. Phase 4: Token Usage Pipeline

Java backend already has Micrometer metrics prepared for AI request and token tracking.

The next backend work should persist token usage into the database.

### Expected Python AI Response

The Python AI service should return token usage with chat/title responses:

```json
{
  "tokenUsage": {
    "provider": "openai",
    "model": "gpt-4o-mini",
    "promptTokens": 100,
    "completionTokens": 50,
    "totalTokens": 150
  }
}
```

### Pipeline

```text
Python AI service returns tokenUsage
        ↓
Java AiServiceClient receives response
        ↓
Java records Micrometer metrics
        ↓
Java stores raw token record in ai_token_usage
        ↓
Scheduled job aggregates into daily_ai_usage_stats
        ↓
Dashboard API returns token charts/cards
```

## 7. Phase 5: Scheduled Aggregation Jobs

Add scheduled jobs to summarize dashboard data.

Proposed jobs:

```text
UserStatsAggregationJob
RevenueStatsAggregationJob
ReportStatsAggregationJob
AiUsageStatsAggregationJob
SystemUsageSummaryJob
```

Recommended schedule:

```text
Daily at midnight
```

Optional development/admin endpoint:

```text
POST /api/v1/admin/dashboard/aggregation/backfill
```

Purpose:

- Rebuild dashboard summary data for a date range.
- Useful after adding new aggregate tables.
- Useful for local development and testing.

Security:

- Backfill endpoint should be Admin-only.

## 8. Phase 6: Dashboard Services And APIs

Follow the current backend package style.

Recommended structure:

```text
controller/dashboard
service/dashboard
dto/dashboard
repository/analytics
entity/analytics
```

Business logic should be placed in services, not controllers.

### Proposed Services

```text
DashboardOverviewService
DashboardUserStatsService
DashboardRevenueService
DashboardTokenUsageService
DashboardReportService
DashboardSystemHealthService
```

### Security Rules

General dashboard APIs:

```java
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
```

Sensitive APIs such as revenue/cost:

```java
@PreAuthorize("hasRole('ADMIN')")
```

Exact role names should be verified against the current project role model before implementation.

## 9. Phase 7: Testing And Verification

Minimum validation before merging:

- Unit tests for aggregation logic
- Repository query tests for complex queries
- Security tests for Admin/Staff/User access
- Swagger/manual verification for all dashboard endpoints
- Verify Grafana still scrapes:

```text
/Historical-tell/actuator/prometheus
```

Recommended manual checks:

```text
Admin can access all dashboard APIs
Staff can access allowed dashboard APIs
Normal user cannot access admin dashboard APIs
Invalid date range returns validation error
Large date range is rejected or capped
Aggregation jobs can run twice without duplicating data
```

## 10. Recommended Implementation Order

1. Review current entities and repositories.
2. Map each dashboard metric to a source table.
3. Identify missing tables and missing fields.
4. Design DTO/API responses.
5. Add `ai_token_usage`.
6. Implement token usage persistence.
7. Add daily aggregate tables.
8. Implement scheduled aggregation jobs.
9. Implement dashboard REST APIs.
10. Add tests.
11. Update Swagger/docs.
12. Verify Grafana still works.

## 11. First Step After Approval

The first implementation step should be:

```text
Review the current backend source code and map every dashboard metric to the exact table, entity, repository, and query strategy.
```

This should produce a metric mapping document before writing feature code.

Suggested output:

```text
Metric Name
Business Meaning
Source Table/Entity
Required Query
API Response Field
Missing Data
Implementation Notes
```

## 12. Open Questions For Review

The team should confirm these before implementation:

1. Which roles can access the dashboard?
2. Should Staff see revenue and AI cost, or only Admin?
3. Which payment table/status represents completed revenue?
4. Does the current report-user module exist, or must it be built first?
5. Does the AI service already know token usage, or do we need to add provider-specific token tracking?
6. What maximum date range should dashboard APIs allow?
7. Should dashboard data be real-time, daily aggregated, or both?
