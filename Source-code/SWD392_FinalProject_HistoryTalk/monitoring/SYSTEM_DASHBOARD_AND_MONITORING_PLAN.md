# System Dashboard And Monitoring Plan

Last updated: 2026-05-20

## 1. Scope

The next module is a System Dashboard covering:

- User reports.
- Token usage.
- Revenue summaries.
- Chat/quiz/content usage.
- System health.
- AI service health and failures.

This project will split the concern into two dashboard types:

1. Technical monitoring dashboard for developers/operators.
2. Business dashboard for Staff/Admin inside the HistoryTalk web app.

## 2. Decision: Grafana Is For Technical Monitoring

Grafana is approved for technical observability:

- Java backend health.
- Python AI backend health after metrics are exposed.
- HTTP request count, latency, and status codes.
- JVM memory and garbage collection.
- Database pool health.
- AI service request success/failure.
- Real-time token usage metrics after token usage is reported by the AI service.

Grafana should not become the main Staff/Admin business dashboard inside the web app.

Reasons:

- Direct SQL panels against transactional business tables can create heavy queries and hurt application performance.
- Embedded Grafana dashboards create cross-authentication and authorization complexity.
- Business dashboard UX should follow the app's Staff/Admin roles and normal REST API security.

## 3. Business Dashboard Strategy

Business reporting should be served by Java backend APIs.

Recommended design:

- Create aggregate tables such as:
  - `daily_user_stats`
  - `daily_token_usage`
  - `daily_revenue_stats`
  - `system_usage_summary`
- Populate them with scheduled jobs, for example nightly `@Scheduled` batch jobs.
- Expose Staff/Admin REST APIs for the frontend dashboard.
- Keep raw transactional tables for business workflows, not dashboard fan-out queries.

The frontend should call Java REST APIs for business dashboard data.

## 4. Technical Monitoring Stack

Local/dev monitoring is provided as infrastructure-as-code:

```text
Source-code/SWD392_FinalProject_HistoryTalk/docker-compose.monitoring.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/prometheus/prometheus.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/datasources/prometheus.yml
```

Services:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`

Start monitoring:

```powershell
cd Source-code/SWD392_FinalProject_HistoryTalk
docker compose -f docker-compose.monitoring.yml up -d
```

Stop monitoring:

```powershell
docker compose -f docker-compose.monitoring.yml down
```

## 5. Actuator Security Rule

The Java backend exposes only:

```text
/actuator/health
/actuator/prometheus
```

With servlet path, the local endpoint is normally:

```text
/Historical-tell/actuator/prometheus
```

Security rules:

- `/actuator/health` is public for basic liveness checks.
- `/actuator/prometheus` is IP/CIDR allowlisted through `MONITORING_ALLOWED_IPS`.
- All other actuator endpoints are denied.

Default allowlist:

```text
127.0.0.1,0:0:0:0:0:0:0:1
```

Local Docker bridge example:

```text
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
```

Production must use only the private IP/CIDR of the Prometheus server or monitoring subnet.

## 6. AI Token Metrics

Actuator does not know LLM token usage by itself.

The Java backend now has a Micrometer service for AI metrics:

```text
historytalk.ai.requests.total
historytalk.ai.tokens.total
```

Current behavior:

- Java records AI request success/failure counters.
- Java can record token counters if the Python AI service returns optional `tokenUsage`.

Expected future AI response extension:

```json
{
  "success": true,
  "data": {
    "message": "string",
    "suggestedQuestions": ["string"],
    "tokenUsage": {
      "provider": "openai",
      "model": "gpt-4o-mini",
      "promptTokens": 100,
      "completionTokens": 80,
      "totalTokens": 180
    }
  }
}
```

Prometheus is for real-time technical/time-series metrics.

The database is still needed for billing, audit, reconciliation, and user-level reports.

## 7. Next Implementation Steps

Recommended order:

1. Verify Java `/Historical-tell/actuator/prometheus` with local allowlist.
2. Start Prometheus + Grafana through Docker Compose.
3. Add a basic Grafana dashboard for Java backend health and AI request counters.
4. Add Python AI `/metrics` endpoint later.
5. Add database `token_usage` table for audit/billing.
6. Add scheduled aggregate tables for Staff/Admin business dashboard.
7. Build Staff/Admin REST APIs for dashboard cards/charts.
