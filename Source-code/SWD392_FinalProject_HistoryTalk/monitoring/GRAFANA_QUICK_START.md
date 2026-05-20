# HistoryTalk Grafana Quick Start

This guide explains how to run and use the local monitoring stack for HistoryTalk.

The monitoring stack includes:

- Prometheus: collects metrics from the Java backend.
- Grafana: displays metrics as dashboards.

## 1. Requirements

Before starting, make sure these are running:

1. Docker Desktop
2. HistoryTalk Java backend

The Java backend should run on:

```text
http://localhost:8080
```

The backend metrics endpoint is:

```text
http://localhost:8080/Historical-tell/actuator/prometheus
```

## 2. Important Backend Config

When running Prometheus in Docker, the Java backend must allow Docker network IPs to access `/actuator/prometheus`.

In this file:

```text
history-talk-backend-Java/src/main/resources/secretKey.properties
```

make sure this value exists:

```properties
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
```

After changing it, restart the Java backend.

## 3. Start Monitoring

Open terminal at:

```text
Source-code/SWD392_FinalProject_HistoryTalk
```

Run:

```powershell
docker compose -f docker-compose.monitoring.yml up -d
```

This starts:

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:3001
```

## 4. Login To Grafana

Open:

```text
http://localhost:3001
```

Default local account:

```text
Username: admin
Password: admin
```

If Grafana asks to change password, you can set a new local password.

## 5. Open The Dashboard

In Grafana, go to:

```text
Dashboards -> HistoryTalk -> HistoryTalk Java Backend
```

If you do not see the dashboard, check if the `Starred` filter is enabled. Clear the filter and search again.

The dashboard currently includes:

- HTTP Request Rate
- HTTP p95 Latency
- JVM Memory Used
- AI Requests And Tokens

## 6. Check Prometheus Target

Open:

```text
http://localhost:9090/targets
```

You should see:

```text
historytalk-java-backend: UP
```

If it is `UP`, Prometheus can collect metrics from the Java backend.

If it is `DOWN`, check:

1. Is the Java backend running on port `8080`?
2. Is this endpoint accessible?

```text
http://localhost:8080/Historical-tell/actuator/prometheus
```

3. Does `secretKey.properties` include this?

```properties
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
```

4. Did you restart the Java backend after changing config?

## 7. Stop Monitoring

Run from:

```text
Source-code/SWD392_FinalProject_HistoryTalk
```

```powershell
docker compose -f docker-compose.monitoring.yml down
```

## 8. Restart Monitoring

Use this after changing Grafana or Prometheus config:

```powershell
docker compose -f docker-compose.monitoring.yml down
docker compose -f docker-compose.monitoring.yml up -d
```

## 9. Reset Grafana Password/Data

Only do this if you forgot the local Grafana password.

Warning: this deletes local Grafana data.

```powershell
docker compose -f docker-compose.monitoring.yml down -v
docker compose -f docker-compose.monitoring.yml up -d
```

Then login again with:

```text
admin / admin
```

## 10. Common Problems

### Docker Desktop pipe error

Error example:

```text
open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified
```

Meaning:

Docker Desktop is not running.

Fix:

1. Open Docker Desktop.
2. Wait until Docker is ready.
3. Run the compose command again.

If Docker still has issues, try:

```powershell
wsl --shutdown
```

Then reopen Docker Desktop.

### Grafana shows No data

Check Prometheus first:

```text
http://localhost:9090/targets
```

If target is `UP`, wait 15-30 seconds and refresh Grafana.

Some panels may still show no data until the related feature is used:

- AI Requests And Tokens only has data after Java backend calls the AI service.
- Token metrics only have full data after the Python AI service returns `tokenUsage`.

### Prometheus target is 403

The backend blocked Prometheus.

Fix `secretKey.properties`:

```properties
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1,172.16.0.0/12
```

Then restart the Java backend.

## 11. Current Design Note

Grafana is used for technical monitoring.

Examples:

- Backend health
- JVM memory
- HTTP latency
- HTTP request rate
- AI call metrics

The business dashboard for Admin/Staff should be built inside the HistoryTalk web app using backend REST APIs.

Examples:

- Revenue
- Total users
- Reported users
- Daily active users
- Token usage by user/package
- Payment statistics
