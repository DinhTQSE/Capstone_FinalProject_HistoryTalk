# Hướng dẫn Deploy HistoryTalk lên AWS EC2

**Ngày tạo:** 2026-06-30  
**Server:** AWS EC2 `c6a.large` — ap-southeast-1 — Amazon Linux 2023  
**Thực hiện bởi:** Nhóm SWD392

---

## Thông tin Server

| Thông số        | Giá trị                          |
|-----------------|----------------------------------|
| Region          | `ap-southeast-1` (Singapore)     |
| Instance type   | `c6a.large`                      |
| Architecture    | `x86_64 / linux/amd64`           |
| vCPU / RAM      | 2 vCPU / 4 GB RAM                |
| OS              | Amazon Linux 2023 x86_64         |
| Root disk       | 30 GB gp3                        |
| Swap            | 4 GB (cần cấu hình thủ công)     |
| Default user    | `ec2-user`                       |

---

## Tổng quan các bước

```
Bước 1  → Cấu hình Security Group trên AWS Console
Bước 2  → Cấu hình Elastic IP
Bước 3  → SSH vào EC2
Bước 4  → Kiểm tra môi trường ban đầu
Bước 5  → Cấu hình Swap
Bước 6  → Cài Docker trên Amazon Linux 2023
Bước 7  → Clone repository
Bước 8  → Tạo file .env trên server
Bước 9  → Cập nhật GitHub Secrets
Bước 10 → Sửa đường dẫn trong deploy.yml
Bước 11 → Trỏ DNS historytalk.app sang EC2
Bước 12 → Deploy thủ công lần đầu
Bước 13 → Verify toàn bộ hệ thống
Bước 14 → Test CI/CD pipeline
```

---

## Bước 1 — Cấu hình Security Group trên AWS Console

Vào **EC2 → Security Groups → chọn Security Group của instance → Edit inbound rules**.

Thêm các rules sau (nếu chưa có):

| Type        | Protocol | Port | Source    | Mục đích                      |
|-------------|----------|------|-----------|-------------------------------|
| SSH         | TCP      | 22   | Your IP   | SSH từ máy local của bạn      |
| HTTP        | TCP      | 80   | 0.0.0.0/0 | Caddy nhận TLS challenge      |
| HTTPS       | TCP      | 443  | 0.0.0.0/0 | HTTPS public                  |

> ⚠️ **Quan trọng:** KHÔNG mở port `8080` hay `8001` ra public.  
> Các port đó chỉ được dùng nội bộ trong Docker network `historytalk-net`.

---

## Bước 2 — Cấu hình Elastic IP

Elastic IP giúp IP của EC2 không thay đổi mỗi khi instance restart.

1. Vào **EC2 → Elastic IPs → Allocate Elastic IP address**.
2. Chọn **Amazon's pool of IPv4 addresses**, nhấn **Allocate**.
3. Chọn IP vừa tạo → **Actions → Associate Elastic IP address**.
4. Chọn đúng EC2 instance của bạn → nhấn **Associate**.
5. **Ghi lại Elastic IP** — dùng cho DNS ở Bước 11 và GitHub Secrets ở Bước 9.

---

## Bước 3 — SSH vào EC2

Từ máy local, chạy lệnh sau (thay `<your-key.pem>` và `<ELASTIC_IP>` bằng giá trị thực):

```bash
ssh -i <your-key.pem> ec2-user@<ELASTIC_IP>
```

Nếu gặp lỗi permissions trên Windows:
```powershell
# PowerShell — cấp quyền đúng cho file .pem
icacls <your-key.pem> /inheritance:r /grant:r "$($env:USERNAME):(R)"
```

---

## Bước 4 — Kiểm tra môi trường ban đầu

Chạy các lệnh sau ngay sau khi SSH vào để nắm được trạng thái server:

```bash
# Xem user hiện tại
whoami
# Kết quả phải là: ec2-user

# Kiểm tra OS
cat /etc/os-release

# Kiểm tra RAM và Swap
free -h

# Kiểm tra disk
df -h

# Kiểm tra port đang dùng
ss -tulpn

# Kiểm tra xem Docker đã có chưa
docker --version 2>/dev/null || echo "Docker chưa được cài"
```

**Kết quả mong đợi:**
- `free -h`: RAM ~4GB, swap có thể chưa có (sẽ cấu hình ở Bước 5)
- `df -h`: thấy 30GB disk ở `/`
- Docker chưa có (sẽ cài ở Bước 6)

---

## Bước 5 — Cấu hình Swap

> EC2 `c6a.large` chỉ có 4GB RAM. Java backend (~1.5GB) + AI backend (~1.5GB) + Caddy sẽ dùng gần hết RAM.  
> Swap 4GB giúp tránh OOM kill khi có spike traffic.

```bash
# Tạo swap file 4GB
sudo fallocate -l 4G /swapfile

# Đặt quyền đúng
sudo chmod 600 /swapfile

# Định dạng thành swap
sudo mkswap /swapfile

# Kích hoạt swap
sudo swapon /swapfile

# Kiểm tra
free -h
# Phải thấy: Swap: 4.0G

# Bật tự động mount swap khi reboot
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# Tối ưu: giảm swappiness (Linux ít dùng swap hơn, chỉ dùng khi thực sự cần)
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

---

## Bước 6 — Cài Docker trên Amazon Linux 2023

> Amazon Linux 2023 dùng `dnf`, không dùng `apt`. Các lệnh bên dưới đúng cho AL2023.

```bash
# Cài Docker
sudo dnf install -y docker

# Khởi động Docker service và bật auto-start khi reboot
sudo systemctl start docker
sudo systemctl enable docker

# Kiểm tra Docker đang chạy
sudo systemctl status docker

# Thêm ec2-user vào group docker để không cần sudo mỗi lần
sudo usermod -aG docker ec2-user

# Đăng xuất và SSH lại để group change có hiệu lực
exit
```

SSH lại vào server, rồi tiếp tục:

```bash
ssh -i <your-key.pem> ec2-user@<ELASTIC_IP>

# Kiểm tra Docker hoạt động không cần sudo
docker run --rm hello-world

# Cài Docker Compose plugin (V2)
DOCKER_COMPOSE_VERSION=$(curl -s https://api.github.com/repos/docker/compose/releases/latest | grep '"tag_name"' | cut -d'"' -f4)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# Xác nhận
docker compose version
# Kết quả mong đợi: Docker Compose version v2.x.x
```

---

## Bước 7 — Clone Repository về EC2

> Nếu repository là **private**, bạn cần GitHub Personal Access Token (PAT) với quyền `read:packages` và `contents:read`.

```bash
# Clone về thư mục home của ec2-user
git clone https://github.com/DinhTQSE/SWD392_FinalProject_HistoryTalk.git \
  /home/ec2-user/SWD392_FinalProject_HistoryTalk

# Di chuyển vào thư mục
cd /home/ec2-user/SWD392_FinalProject_HistoryTalk

# Đảm bảo đang ở nhánh main
git checkout main
git status
git log --oneline -5
```

Xác nhận các file cấu hình đã có:

```bash
ls -la
# Phải thấy: docker-compose.prod.yml, Caddyfile, .gitignore, get-docker.sh, ...

ls Source-code/SWD392_FinalProject_HistoryTalk/
# Phải thấy: .env.java.prod.example, .env.ai.prod.example, history-talk-backend-Java/, history-talk-backend-AI/, ...
```

---

## Bước 8 — Tạo file .env trên Server

> Các file `.env.java.prod` và `.env.ai.prod` **KHÔNG được commit lên Git**.  
> Chúng chỉ tồn tại trực tiếp trên server này.  
> Vị trí: `/home/ec2-user/SWD392_FinalProject_HistoryTalk/` (cùng cấp với `docker-compose.prod.yml`)

### 8.1 Tạo file .env.java.prod

```bash
cd /home/ec2-user/SWD392_FinalProject_HistoryTalk/

# Copy từ template
cp Source-code/SWD392_FinalProject_HistoryTalk/.env.java.prod.example .env.java.prod

# Chỉnh sửa và điền giá trị thật
nano .env.java.prod
```

Điền đầy đủ các giá trị sau trong file:

```env
# Database
DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USER=<database_user>
DB_PASSWORD=<database_password>
DB_SCHEMA=historical_schema

# JWT
JWT_SECRET=<chuỗi ngẫu nhiên mạnh, dùng: openssl rand -base64 32>
JWT_EXPIRATION_MS=3600000
JWT_REFRESH_EXPIRATION_MS=604800000

# Java → AI service (trong Docker network — KHÔNG đổi)
AI_SERVICE_URL=http://historytalk-ai:8001
AI_SERVICE_USERNAME=
AI_SERVICE_PASSWORD=

# PayOS
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=https://historytalk.app/Historical-tell/api/v1/payments/payos/return
PAYOS_CANCEL_URL=https://historytalk.app/Historical-tell/api/v1/payments/payos/cancel

# Supabase document storage
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=
SUPABASE_STORAGE_BUCKET=documents

# Google OAuth
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
FRONTEND_OAUTH_SUCCESS_URL=https://app.historytalk.app/oauth/success
FRONTEND_OAUTH_FAILURE_URL=https://app.historytalk.app/oauth/failure

# Monitoring — chỉ cho phép localhost
MONITORING_ALLOWED_IPS=127.0.0.1,0:0:0:0:0:0:0:1
```

Lưu file: `Ctrl+O` → `Enter` → `Ctrl+X`.

### 8.2 Tạo file .env.ai.prod

```bash
cp Source-code/SWD392_FinalProject_HistoryTalk/.env.ai.prod.example .env.ai.prod
nano .env.ai.prod
```

Điền đầy đủ:

```env
# Java backend (trong Docker network — KHÔNG đổi)
JAVA_BACKEND_URL=http://historytalk-java:8080/Historical-tell
CHARACTER_API_PATH=/api/v1/characters
CONTEXT_API_PATH=/api/v1/historical-contexts
JAVA_CLIENT_TIMEOUT=10

# Ollama chạy trên server RIÊNG — điền URL của server Ollama bên ngoài
# Ví dụ: http://103.x.x.x:11434 hoặc https://ollama.yourdomain.com
OLLAMA_BASE_URL=http://<OLLAMA_SERVER_IP>:11434
OLLAMA_USERNAME=
OLLAMA_PASSWORD=
LLM_MODEL=qwen2.5:14b
LLM_TEMPERATURE=0.7
LLM_MAX_TOKENS=1024

# Supabase / vector database
SUPABASE_URL=
SUPABASE_KEY=
SUPABASE_SCHEMA=historical_schema

# App runtime — KHÔNG đổi
APP_HOST=0.0.0.0
APP_PORT=8001
DEBUG=false
```

> **Lưu ý về `extra_hosts` trong `docker-compose.prod.yml`:**  
> File hiện có dòng `extra_hosts: host-gateway` — dùng để container gọi localhost của host.  
> Vì Ollama nằm ở server khác, dòng này không cần thiết nhưng cũng không gây hại — có thể để nguyên.

### 8.3 Xác nhận quyền file

```bash
# Đảm bảo chỉ owner đọc được file env
chmod 600 .env.java.prod .env.ai.prod
ls -la .env.java.prod .env.ai.prod
# Kết quả: -rw------- (chỉ ec2-user đọc được)
```

---

## Bước 9 — Cập nhật GitHub Secrets

Vào **GitHub Repository → Settings → Secrets and variables → Actions**.

Cập nhật (hoặc tạo mới) 3 secrets sau:

| Secret Name      | Giá trị cần điền                              | Ghi chú                                           |
|------------------|-----------------------------------------------|---------------------------------------------------|
| `SERVER_IP`      | Elastic IP của EC2 (VD: `18.xx.xx.xx`)        | IP cố định đã tạo ở Bước 2                        |
| `SERVER_USER`    | `ec2-user`                                    | **Khác với server cũ** (trước là `minhtringuyen`) |
| `SERVER_SSH_KEY` | Toàn bộ nội dung file `.pem`                  | Bao gồm cả dòng `-----BEGIN` và `-----END`        |

> **Cách lấy nội dung file .pem trên Windows:**
> ```powershell
> Get-Content <your-key.pem> | Set-Clipboard
> # Sau đó paste vào ô GitHub Secret
> ```

> **Cách lấy nội dung file .pem trên Mac/Linux:**
> ```bash
> cat <your-key.pem> | pbcopy  # Mac
> cat <your-key.pem>           # Linux — copy thủ công
> ```

---

## Bước 10 — Sửa Đường dẫn trong deploy.yml

Mở file `.github/workflows/deploy.yml` trong project local của bạn.

Tìm **dòng 51** (trong phần `script:` của job `deploy`):

**TRƯỚC:**
```yaml
            cd /home/minhtringuyen/SWD392_FinalProject_HistoryTalk/
```

**SAU:**
```yaml
            cd /home/ec2-user/SWD392_FinalProject_HistoryTalk/
```

Lưu file và commit + push lên nhánh `main`:

```bash
git add .github/workflows/deploy.yml
git commit -m "fix(ci): update deploy path for ec2-user on Amazon Linux 2023"
git push origin main
```

> ⚠️ **Chưa push ngay** nếu DNS chưa trỏ về EC2 (Bước 11). GitHub Actions sẽ trigger ngay sau khi push và cố SSH vào server. Đảm bảo EC2 đã sẵn sàng trước.

---

## Bước 11 — Trỏ DNS historytalk.app sang EC2

Vào DNS provider đang quản lý domain `historytalk.app` (Cloudflare, Route53, GoDaddy, Namecheap...).

Cập nhật hoặc tạo các DNS records:

| Type | Name  | Value               | TTL      |
|------|-------|---------------------|----------|
| A    | `@`   | `<ELASTIC_IP>`      | 300 giây |
| A    | `www` | `<ELASTIC_IP>`      | 300 giây |

Sau khi lưu, kiểm tra propagation từ máy local:

```bash
# Chờ 1-5 phút, sau đó kiểm tra
nslookup historytalk.app
# hoặc
dig historytalk.app A

# Kết quả phải trả về Elastic IP của EC2
```

Có thể dùng công cụ online để kiểm tra: https://www.whatsmydns.net/#A/historytalk.app

> **Caddy sẽ tự động xin SSL certificate từ Let's Encrypt** sau khi:
> - DNS đã trỏ đúng về EC2
> - Port 80 và 443 đã mở trong Security Group
> - Caddy container đang chạy
>
> Không cần cấu hình SSL thủ công.

---

## Bước 12 — Deploy Thủ công Lần đầu trên EC2

SSH vào EC2 và thực hiện:

### 12.1 Đăng nhập GHCR để kéo private images

```bash
cd /home/ec2-user/SWD392_FinalProject_HistoryTalk/

# Đăng nhập GitHub Container Registry
# Dùng GitHub Personal Access Token (PAT) có quyền read:packages
echo "<GITHUB_PAT>" | docker login ghcr.io -u <GHCR_USERNAME> --password-stdin
# Kết quả mong đợi: Login Succeeded
```

### 12.2 Pull Docker images từ GHCR

```bash
docker compose -f docker-compose.prod.yml pull
# Sẽ pull:
# - ghcr.io/dinhtqse/historytalk-java:latest
# - ghcr.io/dinhtqse/historytalk-ai:latest
# - caddy:2-alpine
```

### 12.3 Khởi chạy tất cả services

```bash
docker compose -f docker-compose.prod.yml up -d
# Flag -d: chạy nền (detached mode)
```

### 12.4 Kiểm tra trạng thái containers

```bash
docker compose -f docker-compose.prod.yml ps
```

**Kết quả mong đợi sau khoảng 30-60 giây:**

```
NAME                    STATUS
historytalk-java        healthy
historytalk-ai          healthy
historytalk-caddy       running
```

Nếu container không chuyển sang `healthy`, xem logs:

```bash
# Java service logs
docker compose -f docker-compose.prod.yml logs --tail=100 historytalk-java

# AI service logs
docker compose -f docker-compose.prod.yml logs --tail=100 historytalk-ai

# Caddy logs
docker compose -f docker-compose.prod.yml logs --tail=100 caddy
```

---

## Bước 13 — Verify Toàn bộ Hệ thống

### 13.1 Kiểm tra nội bộ từ EC2

```bash
# AI service health
curl -i http://localhost:8001/health
# Kết quả mong đợi: HTTP 200 OK

# Java service health
curl -i http://localhost:8080/Historical-tell/actuator/health
# Kết quả mong đợi: {"status":"UP"}
```

### 13.2 Kiểm tra AI container kết nối được Ollama server bên ngoài

```bash
# Xem logs của AI container — tìm lỗi kết nối đến Ollama server ngoài
docker compose -f docker-compose.prod.yml logs --tail=50 historytalk-ai | grep -i "ollama\|error\|warn\|connect"

# Test từ trong container AI (nếu cần debug)
docker exec -it historytalk-ai curl -s http://<OLLAMA_SERVER_IP>:11434/api/tags
# Phải thấy danh sách models
```

### 13.3 Kiểm tra qua Domain (sau khi DNS propagate)

Mở browser và truy cập:

```
# AI health endpoint
https://historytalk.app/health

# Java health endpoint
https://historytalk.app/Historical-tell/actuator/health

# Swagger UI
https://historytalk.app/Historical-tell/api/v1/swagger-ui
```

### 13.4 Kiểm tra HTTPS Certificate

```bash
# Từ máy local
curl -vI https://historytalk.app 2>&1 | grep -E "subject:|issuer:|SSL connection"
# Phải thấy: issuer: Let's Encrypt
```

### 13.5 Kiểm tra Caddy routing đúng

```bash
# Test routing /v1 → AI service
curl -i https://historytalk.app/v1/health 2>/dev/null | head -5

# Test routing /Historical-tell → Java service
curl -i https://historytalk.app/Historical-tell/actuator/health 2>/dev/null | head -5
```

---

## Bước 14 — Test CI/CD Pipeline End-to-End

Sau khi xác nhận tất cả manual steps đã xong (Bước 9, 10, 11, 12, 13):

```bash
# Trên máy local — tạo một commit nhỏ để trigger pipeline
# Ví dụ sửa comment hoặc thêm dòng trắng trong README
git add README.md  # hoặc bất kỳ file nào
git commit -m "ci: test ec2 deployment pipeline"
git push origin main
```

Vào **GitHub Repository → Actions** và theo dõi:

1. Job `build` phải hoàn thành: build Java image + AI image + push lên GHCR.
2. Job `deploy` phải hoàn thành: SSH vào EC2, git pull, docker pull, docker compose up.

Nếu deploy job thành công, kiểm tra lại:

```bash
# SSH vào EC2
docker compose -f docker-compose.prod.yml ps
# Tất cả phải ở trạng thái running/healthy
```

---

## Lệnh Vận hành Thường ngày

```bash
# Xem trạng thái containers
docker compose -f docker-compose.prod.yml ps

# Xem logs realtime
docker compose -f docker-compose.prod.yml logs -f --tail=50

# Xem logs của service cụ thể
docker compose -f docker-compose.prod.yml logs -f --tail=100 historytalk-java
docker compose -f docker-compose.prod.yml logs -f --tail=100 historytalk-ai
docker compose -f docker-compose.prod.yml logs -f --tail=100 caddy

# Restart một service
docker compose -f docker-compose.prod.yml restart historytalk-java

# Stop toàn bộ
docker compose -f docker-compose.prod.yml down

# Dọn image cũ
docker image prune -f

# Xem disk
df -h

# Xem RAM
free -h
```

---

## Rollback nếu có vấn đề

Nếu deployment mới gây lỗi, rollback về image cũ bằng cách chỉnh `image` tag trong `docker-compose.prod.yml` trên server:

```bash
cd /home/ec2-user/SWD392_FinalProject_HistoryTalk/

# Xem danh sách images đang có (bao gồm tags cũ)
docker images | grep historytalk

# Sửa docker-compose.prod.yml trực tiếp trên server để pin về SHA cụ thể
# Ví dụ: ghcr.io/dinhtqse/historytalk-java:<git-sha>
nano docker-compose.prod.yml

# Restart
docker compose -f docker-compose.prod.yml up -d --force-recreate
```

---

## Checklist Hoàn thành

- [ ] **Bước 1:** Security Group đã mở port 22/80/443. Port 8080 và 8001 KHÔNG mở ra public.
- [ ] **Bước 2:** Elastic IP đã được cấp phát và gán vào EC2.
- [ ] **Bước 3:** SSH vào EC2 thành công với user `ec2-user`.
- [ ] **Bước 4:** Kiểm tra môi trường: disk 30GB ✓, Docker chưa cài ✓.
- [ ] **Bước 5:** Swap 4GB đã cấu hình, `free -h` thấy `Swap: 4.0G`, đã thêm vào `/etc/fstab`.
- [ ] **Bước 6:** Docker và Docker Compose V2 đã cài, `docker run hello-world` thành công.
- [ ] **Bước 7:** Repo đã clone về `/home/ec2-user/SWD392_FinalProject_HistoryTalk/`, đang ở nhánh `main`.
- [ ] **Bước 8:** `.env.java.prod` và `.env.ai.prod` đã tạo, `chmod 600`, `OLLAMA_BASE_URL` trỏ đúng sang Ollama server bên ngoài.
- [ ] **Bước 9:** GitHub Secrets đã cập nhật: `SERVER_IP`, `SERVER_USER=ec2-user`, `SERVER_SSH_KEY`.
- [ ] **Bước 10:** `deploy.yml` dòng 51 đã sửa path thành `/home/ec2-user/...`, đã commit và push.
- [ ] **Bước 11:** DNS A record `historytalk.app` trỏ về Elastic IP, `nslookup` trả về đúng IP.
- [ ] **Bước 12:** Deploy thủ công thành công, `docker compose ps` thấy tất cả containers `healthy`.
- [ ] **Bước 13:** HTTPS hoạt động, Caddy tự cấp SSL từ Let's Encrypt, Swagger UI load được.
- [ ] **Bước 14:** GitHub Actions pipeline chạy end-to-end thành công.
