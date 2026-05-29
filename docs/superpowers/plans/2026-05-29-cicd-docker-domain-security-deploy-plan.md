# CI/CD Docker Deployment, Domain, And Security Plan

Date: 2026-05-29

## 1. Goal

Build a repeatable deployment process for HistoryTalk on the current SSH server.

The target is not a one-time manual deployment. The target is a maintainable workflow:

- Java backend and AI backend run with Docker.
- Production configuration and secrets are separated from source code.
- Domain and HTTPS route traffic safely to the backend.
- GitHub Actions automatically validates, builds, and deploys on push.
- The server can be audited, restarted, rolled back, and monitored.

## 2. Current Repository Context

Repository root:

```text
Source-code/SWD392_FinalProject_HistoryTalk/
```

Important services:

```text
history-talk-backend-Java/
history-talk-backend-AI/
monitoring/
```

Java backend:

- Spring Boot 3.2.5
- Java 21
- Maven
- Runtime port: `8080`
- Servlet path: `/Historical-tell`
- Dockerfile already exists.
- Reads DB, JWT, PayOS, Google OAuth, and AI service settings from environment variables.

AI backend:

- FastAPI
- Python 3.12
- Runtime port: `8001`
- Main app: `history_talk_ai.main:app`
- Dockerfile exists. It previously pointed to the wrong module, `app.main:app`; Phase 2 fixes it to `history_talk_ai.main:app`.
- Calls Ollama and Supabase.
- Can call Java backend when character/context data is not prefilled.

## 3. Target Architecture

Recommended production flow:

```text
User / Frontend
    |
    | HTTPS
    v
api.<domain>
    |
    v
Caddy or Nginx reverse proxy
    |
    v
Java Backend container :8080
    |
    +--> AI Backend container :8001, internal Docker network only
    +--> PostgreSQL / external database
    +--> PayOS
    +--> Google OAuth

AI Backend container
    |
    +--> Ollama server
    +--> Supabase vector database
    +--> Java Backend container, internal Docker network if needed
```

Important rule:

```text
Only the reverse proxy should be public.
Java backend may be exposed through the domain.
AI backend should not be exposed directly to the public internet unless there is a specific reason.
```

## 4. Deployment Strategy

Use this stack:

- Docker for containerizing each service.
- Docker Compose for running services on the SSH server.
- GitHub Container Registry, GHCR, for storing built images.
- GitHub Actions for CI/CD.
- Caddy or Nginx for reverse proxy and HTTPS.

Recommended first version:

```text
GitHub Actions builds images -> pushes to GHCR -> SSH server pulls images -> docker compose up -d
```

This is better than building on the server because:

- Server does less work.
- Deployment is faster and more repeatable.
- Build failures happen in CI before touching production.
- Images are versioned and rollback is easier.

## 5. Phase 0: Server Audit

Before changing deployment, inspect the current server.

Run inside the VS Code SSH terminal:

```bash
pwd
ls -la
git status
docker --version
docker compose version
ps aux
ss -tulpn
df -h
free -h
```

Record:

- Project path on server.
- Current branch.
- Whether Docker is installed.
- Whether Docker Compose is installed.
- Ports currently in use.
- RAM and disk availability.
- Any existing Java, Python, PM2, systemd, or Docker process.

Decision after audit:

- If Docker is missing, install Docker and Docker Compose plugin.
- If old manual services are running, stop them only after confirming the new Docker deployment works.
- If ports `80`, `443`, `8080`, or `8001` are already used, identify the owner before changing anything.

### 5.1 Current Server Audit Result

Audit date: 2026-05-29

Server project path:

```text
/home/minhtringuyen/SWD392_FinalProject_HistoryTalk
```

Repository state:

```text
Branch: main
Status: clean
Remote state: behind origin/main by 4 commits
```

Docker state:

```text
docker: not installed
docker compose: not installed
```

Ports currently listening:

```text
22/tcp      SSH, public
80/tcp      public, already occupied by an unknown process
8001/tcp    127.0.0.1 only, Python process pid=1701020
11434/tcp   public on all interfaces, likely Ollama
```

Server resources:

```text
Disk: 296G total, 238G used, 46G available, 85% used
RAM: 31Gi total, 4.5Gi used, 26Gi available
Swap: none
```

Important findings:

- Docker must be installed before Docker Compose deployment can start.
- Existing Python AI service is already running manually on `127.0.0.1:8001`.
- Port `80` is already in use, so reverse proxy setup must identify the current owner before installing Caddy or Nginx.
- Ollama appears to be listening publicly on `*:11434`; this is a security concern unless intentionally protected elsewhere.
- Disk usage is high at `85%`; Docker image builds and logs may fill the disk if cleanup/log rotation is not configured.
- Server repository should be fast-forwarded with `git pull` before using the latest deployment files.

Next audit commands:

```bash
ps -fp 1701020
sudo ss -tulpn | grep ':80'
sudo ss -tulpn | grep ':11434'
systemctl status nginx --no-pager
systemctl status caddy --no-pager
systemctl status ollama --no-pager
du -h --max-depth=1 /home/minhtringuyen | sort -h
```

Do not stop existing services yet. First identify what owns port `80`, `8001`, and `11434`.

## 6. Phase 1: Fix Docker Readiness

### 6.1 Java Backend

Current Dockerfile is mostly production-ready:

- Multi-stage Maven build.
- Java 21 runtime.
- Non-root user.
- Exposes `8080`.
- Runs `app.jar`.

Validate locally or on CI:

```bash
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
docker build -t historytalk-java:test .
```

Required runtime environment variables:

```env
DB_URL=
DB_USER=
DB_PASSWORD=
DB_SCHEMA=
JWT_SECRET=
JWT_EXPIRATION_MS=
JWT_REFRESH_EXPIRATION_MS=
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=
PAYOS_CANCEL_URL=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FRONTEND_OAUTH_SUCCESS_URL=
FRONTEND_OAUTH_FAILURE_URL=
AI_SERVICE_URL=http://historytalk-ai:8001
AI_SERVICE_USERNAME=
AI_SERVICE_PASSWORD=
MONITORING_ALLOWED_IPS=
```

### 6.2 AI Backend

Fix Dockerfile command.

Current problematic command:

```dockerfile
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
```

Correct command:

```dockerfile
CMD ["uvicorn", "history_talk_ai.main:app", "--host", "0.0.0.0", "--port", "8001", "--app-dir", "src"]
```

Validate:

```bash
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python -m compileall -q src main.py
docker build -t historytalk-ai:test .
```

Required runtime environment variables:

```env
JAVA_BACKEND_URL=http://historytalk-java:8080/Historical-tell
CHARACTER_API_PATH=/api/v1/characters
CONTEXT_API_PATH=/api/v1/historical-contexts
JAVA_CLIENT_TIMEOUT=10
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_USERNAME=
OLLAMA_PASSWORD=
LLM_MODEL=qwen2.5:14b
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=1024
SUPABASE_URL=
SUPABASE_KEY=
SUPABASE_SCHEMA=historical_schema
APP_HOST=0.0.0.0
APP_PORT=8001
DEBUG=false
```

Security requirements:

- `OLLAMA_BASE_URL`, `SUPABASE_URL`, and `SUPABASE_KEY` must come from environment variables.
- `OLLAMA_USERNAME` and `OLLAMA_PASSWORD` must come from environment variables when Basic Auth is used.
- In Docker production, `OLLAMA_BASE_URL` should be `http://host.docker.internal:11434`.
- Do not commit real `.env` files.

## 7. Phase 2: Docker Compose Production File

Create a production Compose file at:

```text
Source-code/SWD392_FinalProject_HistoryTalk/docker-compose.prod.yml
```

Recommended initial shape:

```yaml
services:
  historytalk-java:
    image: ghcr.io/<owner>/<repo>/historytalk-java:latest
    container_name: historytalk-java
    restart: unless-stopped
    env_file:
      - .env.java.prod
    environment:
      AI_SERVICE_URL: http://historytalk-ai:8001
    networks:
      - historytalk-net

  historytalk-ai:
    image: ghcr.io/<owner>/<repo>/historytalk-ai:latest
    container_name: historytalk-ai
    restart: unless-stopped
    env_file:
      - .env.ai.prod
    environment:
      JAVA_BACKEND_URL: http://historytalk-java:8080/Historical-tell
      OLLAMA_BASE_URL: http://host.docker.internal:11434
    extra_hosts:
      - "host.docker.internal:host-gateway"
    networks:
      - historytalk-net

  caddy:
    image: caddy:2-alpine
    container_name: historytalk-caddy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - historytalk-java
    networks:
      - historytalk-net

networks:
  historytalk-net:
    driver: bridge

volumes:
  caddy_data:
  caddy_config:
```

Do not publish `8080:8080` or `8001:8001` in production unless needed for temporary debugging.

## 8. Phase 3: Domain And HTTPS

Use one domain for backend API, for example:

```text
api.example.com
```

DNS setup:

```text
Type: A
Name: api
Value: <server-public-ip>
TTL: Auto or 300
```

Recommended Caddyfile:

```caddyfile
api.example.com {
    reverse_proxy historytalk-java:8080
}
```

After DNS points to the server:

```bash
docker compose -f docker-compose.prod.yml up -d caddy
docker compose -f docker-compose.prod.yml logs -f caddy
```

Expected result:

- Caddy obtains TLS certificate automatically.
- `https://api.example.com/Historical-tell/...` reaches Java backend.

Important OAuth callback:

```text
https://api.example.com/Historical-tell/login/oauth2/code/google
```

This must be configured in Google Cloud Console.

PayOS return/cancel/webhook URLs must also use the production HTTPS domain.

## 9. Phase 4: Environment And Secrets

Create real env files on the server only:

```text
Source-code/SWD392_FinalProject_HistoryTalk/.env.java.prod
Source-code/SWD392_FinalProject_HistoryTalk/.env.ai.prod
```

Never commit those files.

Commit only examples:

```text
.env.java.prod.example
.env.ai.prod.example
```

Recommended `.gitignore` entries:

```gitignore
.env
.env.*
!.env.example
!.env.*.example
```

Preferred first setup:

- Store production env files manually on the server.
- GitHub Actions only pulls images and restarts Compose.
- This reduces the chance of leaking secrets through CI logs.

Later improvement:

- Generate env files from GitHub Actions secrets.
- Must ensure secrets are never printed.

## 10. Phase 5: Database And Migration

Current Java config:

```properties
spring.jpa.hibernate.ddl-auto=none
spring.flyway.enabled=false
```

Meaning:

- The app will not create tables.
- The app will not run Flyway migrations.
- Database schema must already exist.

Before deployment:

1. Confirm database host, username, password, schema.
2. Confirm the schema contains all expected tables.
3. Confirm migrations in `src/main/resources/db/migration` have been applied.
4. Backup production database before schema changes.

Decision needed:

- Keep Flyway disabled and run migrations manually.
- Or enable Flyway after validating all migration scripts.

Safer first deployment:

```text
Keep Flyway disabled.
Apply schema manually or through a controlled migration step.
Enable Flyway later after a backup and migration audit.
```

## 11. Phase 6: First Manual Docker Deployment

Run on server:

```bash
cd <server-project-path>/Source-code/SWD392_FinalProject_HistoryTalk
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Check logs:

```bash
docker compose -f docker-compose.prod.yml logs -f historytalk-java
docker compose -f docker-compose.prod.yml logs -f historytalk-ai
docker compose -f docker-compose.prod.yml logs -f caddy
```

Smoke tests from server:

```bash
curl -i http://historytalk-ai:8001/health
curl -i http://historytalk-java:8080/Historical-tell/actuator/health
curl -i https://api.example.com/Historical-tell/actuator/health
```

Smoke tests from browser:

```text
https://api.example.com/Historical-tell/api/v1/swagger-ui
```

## 12. Phase 7: GitHub Actions CI

Create workflow:

```text
.github/workflows/deploy.yml
```

CI jobs:

1. Checkout source.
2. Validate Java backend.
3. Validate AI backend.
4. Build Java Docker image.
5. Build AI Docker image.
6. Push images to GHCR.

Recommended validation:

```bash
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java
mvn -q -DskipTests compile
```

```bash
cd Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-AI
python -m compileall -q src main.py
```

Recommended tags:

```text
ghcr.io/<owner>/<repo>/historytalk-java:latest
ghcr.io/<owner>/<repo>/historytalk-java:<git-sha>
ghcr.io/<owner>/<repo>/historytalk-ai:latest
ghcr.io/<owner>/<repo>/historytalk-ai:<git-sha>
```

Use `<git-sha>` for rollback.

## 13. Phase 8: GitHub Actions CD

Deployment job:

1. SSH into server.
2. Go to project deploy path.
3. Pull latest images.
4. Restart containers.
5. Run smoke tests.

Expected deploy command:

```bash
cd <server-project-path>/Source-code/SWD392_FinalProject_HistoryTalk
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
curl -f http://localhost/health-or-domain-endpoint
```

GitHub repository secrets needed:

```text
SERVER_HOST
SERVER_USER
SERVER_PORT
SERVER_SSH_KEY
```

GHCR permissions:

```yaml
permissions:
  contents: read
  packages: write
```

Server must be able to pull private GHCR images.

Options:

- Make GHCR package public.
- Or run `docker login ghcr.io` on the server with a GitHub PAT.

## 14. Phase 9: Security Baseline

### 14.1 Firewall

Only expose:

```text
22/tcp  SSH
80/tcp  HTTP for TLS issuance/redirect
443/tcp HTTPS
```

Do not expose:

```text
8080/tcp Java direct access
8001/tcp AI direct access
```

Example if using UFW:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

### 14.2 CORS

Current Java CORS allows all origins with credentials.

Production target:

```text
Allow only frontend domain.
Example: https://app.example.com
```

### 14.3 Secrets

Do not store these in Git:

```text
JWT_SECRET
DB_PASSWORD
PAYOS_API_KEY
PAYOS_CHECKSUM_KEY
GOOGLE_CLIENT_SECRET
OLLAMA_PASSWORD
SUPABASE_KEY
```

### 14.4 AI Service

AI service should be private to Docker network.

If it must be exposed for testing:

- Add Basic Auth at reverse proxy.
- Or restrict by IP.
- Remove public route after testing.

### 14.5 Actuator

Health can be public:

```text
/Historical-tell/actuator/health
```

Prometheus metrics should be restricted:

```text
/Historical-tell/actuator/prometheus
```

Add actuator exposure if needed:

```properties
management.endpoints.web.exposure.include=health,prometheus
management.endpoint.health.probes.enabled=true
```

### 14.6 OAuth

Google OAuth must use HTTPS callback:

```text
https://api.example.com/Historical-tell/login/oauth2/code/google
```

Frontend success/failure redirects must also be HTTPS.

### 14.7 PayOS

PayOS webhook endpoint is public by design.

Required:

- Verify PayOS webhook signature/checksum.
- Use HTTPS production endpoint.
- Do not rely only on obscurity of the URL.

## 15. Phase 10: Monitoring And Logs

Minimum:

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs --tail=200 historytalk-java
docker compose -f docker-compose.prod.yml logs --tail=200 historytalk-ai
```

Recommended:

- Keep existing `monitoring/` Prometheus and Grafana setup.
- Expose Java actuator Prometheus endpoint.
- Add Docker log rotation.

Docker log rotation example in Compose:

```yaml
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

Basic endpoints to monitor:

```text
GET /Historical-tell/actuator/health
GET /health on AI internal service
```

## 16. Phase 11: Rollback

Rollback strategy:

1. Every image is tagged with git SHA.
2. Compose can pin a previous image tag.
3. Re-run Compose.

Example:

```yaml
image: ghcr.io/<owner>/<repo>/historytalk-java:<previous-sha>
```

Then:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Rollback checklist:

- Roll back app image.
- Do not roll back DB blindly.
- If migration changed DB, restore from backup only with team agreement.

## 17. Phase 12: Final Acceptance Checklist

Server:

- [ ] Docker installed.
- [ ] Docker Compose installed.
- [ ] Project path confirmed.
- [ ] Firewall only exposes `22`, `80`, `443`.
- [ ] Old manual services identified.

Docker:

- [ ] Java image builds.
- [ ] AI image builds.
- [ ] AI Dockerfile points to `history_talk_ai.main:app`.
- [ ] Compose starts all services.
- [ ] Java can call AI by `http://historytalk-ai:8001`.
- [ ] AI can call Java by `http://historytalk-java:8080/Historical-tell`.

Domain:

- [ ] DNS A record points to server IP.
- [ ] HTTPS certificate works.
- [ ] Swagger is reachable through domain.
- [ ] Google OAuth callback uses production domain.
- [ ] PayOS URLs use production domain.

Secrets:

- [ ] Production env files are on server.
- [ ] Production env files are not committed.
- [ ] GitHub Actions secrets are configured.
- [ ] No real secrets remain hard-coded in source.

CI/CD:

- [ ] Java compile runs in CI.
- [ ] AI compile runs in CI.
- [ ] Docker images build in CI.
- [ ] Images push to GHCR.
- [ ] Server can pull images.
- [ ] Deploy job restarts Compose.
- [ ] Smoke test runs after deploy.

Security:

- [ ] AI is not directly public.
- [ ] CORS is restricted to frontend domain.
- [ ] Actuator metrics are restricted.
- [ ] PayOS webhook verifies checksum.
- [ ] JWT secret is strong.

Operations:

- [ ] Logs are accessible.
- [ ] Health endpoints are tested.
- [ ] Rollback method is documented.
- [ ] Database backup/migration approach is decided.

## 18. Recommended Work Order

Do the work in this order:

1. Audit server.
2. Fix AI Dockerfile.
3. Create `.env.java.prod.example` and `.env.ai.prod.example`.
4. Create `docker-compose.prod.yml`.
5. Create Caddyfile or Nginx config.
6. Manually deploy once on server.
7. Smoke test Java, AI, domain, Swagger.
8. Configure OAuth and PayOS production URLs.
9. Add GitHub Actions CI build.
10. Add GitHub Actions CD deploy.
11. Lock down firewall and CORS.
12. Add monitoring and rollback notes.

## 19. Immediate Next Step

Run this on the SSH server and save the output for the next implementation step:

```bash
pwd
ls -la
git status
docker --version
docker compose version
ss -tulpn
df -h
free -h
```

After this audit, decide:

- Exact server deploy path.
- Whether Docker/Caddy must be installed.
- Which domain will point to the server.
- Whether production DB schema is already ready.
