# HƯỚNG DẪN PHÁT TRIỂN DỰ ÁN HISTORYTALK

Chào mừng bạn tham gia dự án! Tài liệu này nhằm mục đích thống nhất quy trình làm việc, đảm bảo tính ổn định của hệ thống Production và hỗ trợ anh em Dev phát triển local một cách nhanh nhất.

## 1. Quy tắc làm việc (Code of Conduct)
* **KHÔNG push thẳng vào `main`**: Mọi tính năng mới hoặc sửa lỗi phải thực hiện trên nhánh riêng (`feature/` hoặc `fix/`).
* **Pull Request (PR) là bắt buộc**: Chỉ merge vào `main` thông qua PR sau khi được ít nhất một thành viên khác review.
* **Bảo mật**: Tuyệt đối không commit các file `.env` chứa API Keys, mật khẩu Database lên kho lưu trữ. Sử dụng `.env.example` làm mẫu.
* **Cấu hình Production**: Không tự ý sửa trực tiếp cấu hình trên server. Mọi thay đổi phải xuất phát từ code trên nhánh `main`.
* Không được sửa code docker compose, docker file nếu không cần thiết. Nếu sửa thì phải test trước khi merge vào nhánh `main` hoặc phải có sự đồng ý của các thành viên khác. Không được sửa code deploy.yml hoặc workflow .github/workflow/, nếu sửa cần thông báo trước vs mng



## 2. Setup môi trường Local
Để code trên máy cá nhân mà không cần login vào Registry, hãy sử dụng quy trình **Local Build**.

### Bước 1: Chuẩn bị biến môi trường

> **Quan trọng:** Tạo file env **tại thư mục gốc của repo** (cùng cấp với `docker-compose.prod.yml`), **không phải** bên trong `Source-code/`.

Tạo 2 file sau tại thư mục gốc:

**`.env.java.dev`** — tham khảo `Source-code/SWD392_FinalProject_HistoryTalk/.env.java.prod.example`:

**`.env.ai.dev`** — tham khảo `Source-code/SWD392_FinalProject_HistoryTalk/.env.ai.prod.example`.

Docker Compose sẽ tự động tải `.env.java.dev` / `.env.ai.dev` khi chạy local, và `.env.java.prod` / `.env.ai.prod` khi chạy trên server. Không cần thay đổi thêm gì.

### Bước 2: Sử dụng Docker Compose phối hợp
Dự án sử dụng 2 file phối hợp để chạy:
- `docker-compose.prod.yml`: Định nghĩa cấu hình chính (Network, Volumes, Caddy, env files).
- `docker-compose.override.yml`: Ghi đè cấu hình để build code trực tiếp tại máy local thay vì tải image từ GHCR.



## 3. Lệnh chạy dự án (Local Development)
Sử dụng lệnh sau tại thư mục gốc để build và khởi chạy hệ thống:

```bash
docker compose -f docker-compose.prod.yml -f docker-compose.override.yml up --build