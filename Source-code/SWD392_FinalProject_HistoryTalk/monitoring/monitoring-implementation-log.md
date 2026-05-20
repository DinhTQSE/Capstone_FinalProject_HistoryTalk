# HistoryTalk Monitoring Implementation Log

Date: 2026-05-20

This document records the work done around the HistoryTalk project handoff, backend environment fixes, and the first Monitoring/Grafana integration. It is intended as a tracking file so the team can continue development without losing context.

## 1. Project Understanding And Documentation

### What was reviewed

- Read the current project documentation under `docs/`.
- Confirmed the active Java backend is:
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`
- Confirmed the Python AI backend is:
  - `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI`
- Noted that `history-talk-backend` is an older/other backend copy and should not be treated as the main Java backend unless the team explicitly decides to switch.

### Important project facts captured

- Java backend uses Spring Boot and Java 21.
- Java backend currently uses conventional packages such as:
  - `controller`
  - `service`
  - `repository`
  - `entity`
  - `dto`
  - `mapper`
  - `config`
  - `security`
  - `exception`
  - `utils`
- Backend context path:

```text
/Historical-tell
```

- Java backend local port:

```text
8080
```

- Swagger UI:

```text
http://localhost:8080/Historical-tell/api/v1/swagger-ui
```

- Python AI service local port:

```text
8001
```

### Documentation files created

- `docs/project-business-overview.md`
  - Summarizes HistoryTalk business domain, roles, modules, business rules, and next development direction.
  - Note: this file should be reviewed once more because some earlier wording may still mention the older planned 3-layer Java layout. The active backend currently uses the conventional package structure listed above.

- `docs/SYSTEM_DASHBOARD_AND_MONITORING_PLAN.md`
  - Captures the intended design for the System Dashboard and Monitoring module.
  - Main decision: Grafana is for technical/system monitoring, while business dashboards for Admin/Staff should be implemented inside the HistoryTalk web app through REST APIs and aggregated tables.

## 2. Local Secret Configuration Fixes

### Problem encountered

The Java backend failed to start because required environment placeholders were missing:

```text
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

### Fix applied

Local ignored config files were used for development secrets:

- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/secretKey.properties`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/resources/secretKey.properties`

These files contain local values such as:

```properties
DB_URL=jdbc:postgresql://localhost:5432/history_talk_db
DB_USER=postgres
DB_PASSWORD=123456
DB_SCHEMA=historical_schema
JWT_SECRET=...
JWT_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000
AI_SERVICE_URL=http://localhost:8001
```

Important:

- These secret files are ignored by Git.
- They should not be committed.
- Production should use real environment variables or a secret manager.

## 3. Monitoring Architecture Decision

The team agreed on this direction:

```text
Spring Boot Actuator + Micrometer -> Prometheus -> Grafana
```

### Grafana scope

Grafana should be used mainly for:

- Java backend health
- JVM memory
- HTTP request rate
- HTTP latency
- HTTP error rate
- AI request and token metrics
- DevOps/Admin technical monitoring

Grafana should not be the main long-term business dashboard for Staff/Admin users.

### Business dashboard scope

The HistoryTalk application should later implement business dashboards through normal backend APIs, for example:

- Total users
- Active users
- Revenue
- Token usage by day
- Reported users
- Quiz activity
- Payments
- System usage summary

Recommended long-term approach:

- Add scheduled Java jobs using `@Scheduled`.
- Aggregate raw tables into reporting tables such as:
  - `daily_stats`
  - `system_usage_summary`
  - `ai_token_usage_daily`
- Expose REST APIs for the HistoryTalk frontend.
- Avoid letting Grafana query production business tables directly.

## 4. Java Backend Observability Changes

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml
```

### Dependencies added

Added Spring Boot Actuator:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Added Prometheus registry for Micrometer:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Purpose:

- Expose health and metrics endpoints.
- Allow Prometheus to scrape Spring Boot metrics.

## 5. Actuator Configuration

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties
```

### Properties added

```properties
# =============================================
# Observability / Actuator
# =============================================
# Expose only low-risk health and Prometheus scrape endpoints.
# /actuator/prometheus is protected in SecurityConfig by MONITORING_ALLOWED_IPS.
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.show-details=never
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=${spring.application.name}
monitoring.allowed-ips=${MONITORING_ALLOWED_IPS:127.0.0.1,0:0:0:0:0:0:0:1}
```

### Exposed endpoints

Health endpoint:

```text
http://localhost:8080/Historical-tell/actuator/health
```

Prometheus endpoint:

```text
http://localhost:8080/Historical-tell/actuator/prometheus
```

## 6. Actuator Security Hardening

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java
```

### What was added

Added a separate Spring Security filter chain for Actuator:

- `@Order(1)` for `/actuator/**`
- `@Order(2)` for the normal application API security chain

### Rules implemented

- `/actuator/health`
  - Publicly accessible.
  - Does not expose detailed health information.

- `/actuator/prometheus`
  - Protected by IP/CIDR allowlist.
  - Uses `MONITORING_ALLOWED_IPS`.
  - Supports normal remote address and `X-Forwarded-For`.

- Other `/actuator/**` endpoints
  - Denied.

### Why this matters

This prevents accidentally exposing sensitive system internals or environment-related information through Actuator in development or production.

## 7. Monitoring Environment Configuration

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/.env.example
```

### Added

```properties
# Monitoring / Actuator
# Comma-separated IP or CIDR allowlist for /actuator/prometheus.
# Local host-only: 127.0.0.1,0:0:0:0:0:0:0:1
# Local Docker bridge example: 127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1
```

### Recommended local value when using Docker Prometheus

Because Prometheus runs inside Docker and calls the Java backend through Docker networking, local development may need:

```properties
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
```

Add this to:

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/secretKey.properties
```

Then restart the Java backend.

## 8. Dockerfile Update

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/Dockerfile
```

### Environment variables added

```dockerfile
ENV AI_SERVICE_URL="http://localhost:8001"
ENV MONITORING_ALLOWED_IPS="127.0.0.1,0:0:0:0:0:0:0:1"
```

Purpose:

- Make AI service URL configurable when containerized.
- Make Actuator Prometheus allowlist configurable when containerized.

## 9. Custom AI Metrics

### File created

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/chat/AiMetricsService.java
```

### Metrics added

AI request counter:

```text
historytalk.ai.requests
```

Prometheus name:

```text
historytalk_ai_requests_total
```

Tags:

```text
operation
status
```

Example operations:

- `chat`
- `generate_title`

Example statuses:

- `success`
- `failure`

AI token counter:

```text
historytalk.ai.tokens
```

Prometheus name:

```text
historytalk_ai_tokens_total
```

Tags:

```text
provider
model
type
```

Token types:

- `prompt`
- `completion`
- `total`

### Important note

Token metrics will only show real values after the Python AI service returns token usage in its response using a shape compatible with:

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

Until then, the metric exists in Java code but may not show token data.

## 10. AI Service Client Instrumentation

### File changed

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/chat/AiServiceClient.java
```

### What changed

Injected:

```java
AiMetricsService aiMetricsService
```

Recorded metrics for:

- `/chat` success
- `/chat` failure
- `/generate-title` success
- `/generate-title` failure

Added optional token usage parsing in Java response records:

```java
@JsonProperty("tokenUsage") AiMetricsService.TokenUsage tokenUsage
```

Purpose:

- Track AI service availability and failures.
- Prepare the backend for token usage monitoring and cost tracking.

## 11. Docker Compose Monitoring Stack

### File created

```text
Source-code/SWD392_FinalProject_HistoryTalk/docker-compose.monitoring.yml
```

### Services added

Prometheus:

- Image: `prom/prometheus:v2.54.1`
- Port: `9090`
- Container name: `historytalk-prometheus`

Grafana:

- Image: `grafana/grafana:11.2.2`
- Port: `3001`
- Container name: `historytalk-grafana`
- Default login:

```text
admin / admin
```

### Run command

From:

```text
Source-code/SWD392_FinalProject_HistoryTalk
```

Run:

```powershell
docker compose -f docker-compose.monitoring.yml up -d
```

### Restart command

```powershell
docker compose -f docker-compose.monitoring.yml down
docker compose -f docker-compose.monitoring.yml up -d
```

## 12. Prometheus Configuration

### File created

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/prometheus/prometheus.yml
```

### Java backend scrape target

```yaml
scrape_configs:
  - job_name: "historytalk-java-backend"
    metrics_path: "/Historical-tell/actuator/prometheus"
    static_configs:
      - targets:
          - "host.docker.internal:8080"
        labels:
          service: "history-talk-backend-java"
```

### Meaning of the endpoint

Inside the Prometheus container:

```text
http://host.docker.internal:8080/Historical-tell/actuator/prometheus
```

Equivalent on the host machine:

```text
http://localhost:8080/Historical-tell/actuator/prometheus
```

Meaning:

- `host.docker.internal`: lets Docker container call services running on Windows host.
- `8080`: Java backend port.
- `/Historical-tell`: HistoryTalk servlet context path.
- `/actuator/prometheus`: Spring Boot Actuator metrics endpoint for Prometheus.

### Current status

Prometheus target was verified as:

```text
historytalk-java-backend: 1/1 UP
```

This means Prometheus can successfully scrape the Java backend.

## 13. Grafana Datasource Provisioning

### File created

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/datasources/prometheus.yml
```

### Datasource configured

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    uid: prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

Purpose:

- Grafana automatically connects to Prometheus.
- No need to add the datasource manually in Grafana UI.
- The datasource UID is fixed as `prometheus` so provisioned dashboards can reference it reliably.

### Fix applied after first dashboard load

Grafana initially displayed dashboard panels with `No data` and warning icons while Prometheus was already `UP`.

Root cause:

- The dashboard JSON referenced datasource UID `prometheus`.
- The datasource provisioning file originally did not set an explicit UID.
- Grafana may generate a random datasource UID if none is specified.

Fix:

```yaml
uid: prometheus
```

was added to:

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/datasources/prometheus.yml
```

## 14. Grafana Dashboard Provisioning

### Files created

Dashboard provider:

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/dashboards/historytalk.yml
```

Dashboard JSON:

```text
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/dashboards/historytalk-java-backend.json
```

### Docker Compose volume added

In:

```text
Source-code/SWD392_FinalProject_HistoryTalk/docker-compose.monitoring.yml
```

Added:

```yaml
- ./monitoring/grafana/dashboards:/var/lib/grafana/dashboards:ro
```

### Dashboard created

Grafana dashboard title:

```text
HistoryTalk Java Backend
```

Dashboard folder:

```text
HistoryTalk
```

### Panels included

- HTTP Request Rate
- HTTP p95 Latency
- JVM Memory Used
- AI Requests And Tokens

### Useful PromQL examples

HTTP request rate:

```promql
sum(rate(http_server_requests_seconds_count{application="history-talk-backend"}[5m]))
```

HTTP p95 latency:

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="history-talk-backend"}[5m])) by (le))
```

JVM memory:

```promql
sum(jvm_memory_used_bytes{application="history-talk-backend"}) by (area)
```

AI requests:

```promql
sum(increase(historytalk_ai_requests_total[5m])) by (operation, status)
```

AI tokens:

```promql
sum(increase(historytalk_ai_tokens_total[5m])) by (model, type)
```

## 15. Docker Desktop Issue Encountered

### Error

```text
unable to get image 'grafana/grafana:11.2.2'
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

### Meaning

Docker Desktop was not running, or the Linux engine was not available.

### Fix

- Start Docker Desktop.
- Wait until Docker says it is running.
- If needed, run:

```powershell
wsl --shutdown
```

Then start Docker Desktop again and rerun:

```powershell
docker compose -f docker-compose.monitoring.yml up -d
```

## 16. Grafana Login Issue Encountered

### Problem

Grafana login failed because an email address was used instead of the configured admin username.

### Correct default local login

```text
Username: admin
Password: admin
```

If password was changed and forgotten, reset by removing the Grafana Docker volume, then start again.

Warning: this deletes local Grafana data.

```powershell
docker compose -f docker-compose.monitoring.yml down -v
docker compose -f docker-compose.monitoring.yml up -d
```

## 17. Current Runtime Status

Current verified status:

- Docker monitoring stack can run.
- Grafana is accessible at:

```text
http://localhost:3001
```

- Prometheus is accessible at:

```text
http://localhost:9090
```

- Prometheus target page showed:

```text
historytalk-java-backend (1/1 up)
```

This confirms:

```text
Java Backend -> /actuator/prometheus -> Prometheus -> Grafana
```

is working.

## 18. Validation Run

Java compile check was run:

```powershell
mvn -q -DskipTests compile
```

Result:

```text
PASS
```

Note:

- The command needed network/cache access for Maven dependencies.

## 19. Files Changed Or Created

### Created

```text
docs/project-business-overview.md
docs/SYSTEM_DASHBOARD_AND_MONITORING_PLAN.md
docs/monitoring-implementation-log.md
Source-code/SWD392_FinalProject_HistoryTalk/docker-compose.monitoring.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/prometheus/prometheus.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/datasources/prometheus.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/provisioning/dashboards/historytalk.yml
Source-code/SWD392_FinalProject_HistoryTalk/monitoring/grafana/dashboards/historytalk-java-backend.json
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/chat/AiMetricsService.java
```

### Modified

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/.env.example
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/Dockerfile
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/application.properties
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/chat/AiServiceClient.java
```

### Local ignored files touched/used

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/secretKey.properties
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend/src/main/resources/secretKey.properties
```

These should remain uncommitted.

## 20. What To Do Next

### Immediate next steps

1. Restart monitoring compose after dashboard provisioning changes:

```powershell
cd "C:\Users\trand\OneDrive\Documents\Semester 7\SWD392\SWD392_FinalProject_Git\Source-code\SWD392_FinalProject_HistoryTalk"
docker compose -f docker-compose.monitoring.yml down
docker compose -f docker-compose.monitoring.yml up -d
```

2. Open Prometheus targets:

```text
http://localhost:9090/targets
```

Confirm:

```text
historytalk-java-backend: UP
```

3. Open Grafana:

```text
http://localhost:3001
```

Then go to:

```text
Dashboards -> HistoryTalk -> HistoryTalk Java Backend
```

If the dashboard list looks empty, clear the `Starred` filter.

### Backend next steps

1. Update Python AI service to return `tokenUsage`.
2. Add persistent token usage table for billing/audit.
3. Add scheduled aggregation tables for business dashboard.
4. Add REST APIs for Admin/Staff dashboard in Java backend.
5. Add frontend System Dashboard screens inside the HistoryTalk web app.

### Monitoring next steps

1. Add panels for:
   - HTTP 4xx rate
   - HTTP 5xx rate
   - Top slow endpoints
   - Database connection pool
   - JVM GC time
2. Add alert rules later:
   - Backend down
   - High 5xx rate
   - High latency
   - AI failure spike
3. Add Python AI service metrics endpoint later.

## 21. Important Design Reminder

Grafana is now suitable for technical monitoring.

The actual HistoryTalk System Dashboard for product/business users should still be built in the application itself, using:

```text
Spring scheduled aggregation jobs -> reporting tables -> REST APIs -> frontend dashboard
```

This avoids:

- Direct Grafana queries against business tables.
- Heavy SQL load on production tables.
- Complicated Grafana iframe authentication.
- Mixing DevOps dashboards with Staff/Admin business workflows.
