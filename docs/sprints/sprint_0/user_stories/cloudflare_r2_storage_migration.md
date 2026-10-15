# 📋 USER STORIES: CHUYỂN ĐỔI HỆ THỐNG LƯU TRỮ CLOUDFLARE R2 (STORAGE MIGRATION)
> **Sprint:** Sprint 0 (13/10/2026 – 19/10/2026)  
> **Phân hệ:** Hạ tầng Lưu trữ Tệp tin & Phân phối Tốc độ cao (File Storage & Edge CDN Infrastructure)  

---

## 📌 TỔNG HỢP USER STORIES

| Story ID | Tên User Story | Actor | Người phụ trách | API Endpoint chính |
| :--- | :--- | :--- | :--- | :--- |
| **`US-SP0-06`** | Chuẩn hóa Kiến trúc Storage Đa nền tảng & Tích hợp Cloudflare R2 | System Admin | 🟢 Khải (KhaiVDD) | Cấu hình Bean `StorageService` & AWS SDK v2 |
| **`US-SP0-07`** | Quản lý Tài liệu PDF Học thuật & Ký Presigned S3 URL bảo mật | Content Admin, System Admin | 🟢 Khải (KhaiVDD) | `POST /api/v1/documents/{docId}/upload-pdf`, `GET /api/v1/documents/{docId}/pdf-url` |
| **`US-SP0-08`** | Tải lên Trực tiếp Đa phương tiện 2D/3D & Phân phối qua Edge CDN | All Actors (User, Teacher, Content Admin) | 🟢 Khải (KhaiVDD) | `POST /api/v1/users/{userId}/avatar`, `POST /api/v1/characters/{id}/media` |
| **`US-SP0-09`** | Quy trình Đồng bộ Dữ liệu Không gián đoạn & Cơ chế Dự phòng Nóng | System Admin | 🟢 Khải (KhaiVDD) | `rclone sync`, Config Flag `STORAGE_PROVIDER` |

---

## 📝 CHI TIẾT USER STORIES

### 🆔 US-SP0-06: Chuẩn hóa Kiến trúc Storage Đa nền tảng & Tích hợp Cloudflare R2 (Multi-Provider Storage Abstraction & Cloudflare R2 Integration)
* **Actor:** `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 13/10/2026 – 16/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** Hệ thống nền tảng / System Admin cấu hình hạ tầng
  - **Phạm vi bảo vệ dữ liệu (Data Isolation):** Cấu hình tầng ứng dụng (Application Level), môi trường Dev & Production
* **User Story:**  
  Là System Administrator, tôi muốn hệ thống trừu tượng hóa tầng lưu trữ tệp qua giao diện `StorageService` chuẩn S3 API và triển khai adapter Cloudflare R2, để hệ thống loại bỏ hoàn toàn chi phí băng thông tải ra (Zero Egress Fees), mở rộng không giới hạn và không bị trói buộc vào một nhà cung cấp duy nhất (Vendor Lock-in).

* **Acceptance Criteria (AC):**
  1. Tích hợp thư viện AWS SDK v2 (`software.amazon.awssdk:s3:2.25.16`) vào [pom.xml](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/pom.xml).
  2. Định nghĩa giao diện trừu tượng `StorageService` bao gồm các chức năng cốt lõi:
     - `uploadPdf(...)`, `uploadFile(...)`
     - `downloadPdf(...)`
     - `createSignedPdfUrl(...)`, `createSignedUrl(...)`
     - `generatePresignedUploadUrl(...)`
     - `deleteFile(...)`, `getPublicUrl(...)`
  3. Cài đặt lớp `CloudflareR2StorageService` hiện thực `StorageService`:
     - Kết nối tới Cloudflare R2 qua `S3Client` và `S3Presigner` với cấu hình `pathStyleAccessEnabled(true)`, `region("auto")`, endpoint tùy biến.
     - Được nạp tự động khi cấu hình `app.storage.provider=r2`.
  4. Cải tổ `SupabaseDocumentStorageService` hiện tại để implements `StorageService`:
     - Gắn annotation `@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")`.
     - Giữ vai trò Fallback/Backup provider sẵn sàng kích hoạt tức thì.
  5. Cơ chế chuyển đổi không gián đoạn: Hệ thống có thể chuyển đổi giữa Cloudflare R2 và Supabase Storage chỉ bằng việc thay đổi biến môi trường `STORAGE_PROVIDER` mà không cần sửa code.

---

### 🆔 US-SP0-07: Quản lý Tài liệu PDF Học thuật & Ký Presigned S3 URL bảo mật (PDF Document Lifecycle & Secure Presigned S3 URLs)
* **Actor:** `CONTENT_ADMIN`, `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 14/10/2026 – 17/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
  - **Phạm vi bảo vệ dữ liệu (Data Isolation):** Tài liệu học thuật hệ thống (`document_id`, `historical_context_id`, `character_id`)
* **User Story:**  
  Là Content Admin, tôi muốn tải lên các tệp tài liệu lịch sử định dạng PDF lên Cloudflare R2 và tạo đường dẫn tải có chữ ký điện tử có thời hạn (Presigned S3 URL), để bảo vệ tài liệu bản quyền không bị truy cập trái phép và tối ưu hóa tốc độ tải tệp cho người học.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/documents/{docId}/upload-pdf`:
     - Nhận tệp qua `multipart/form-data` (tham số `file`).
     - Validation: Chỉ chấp nhận định dạng MIME `application/pdf`, dung lượng tối đa 20MB.
     - Lưu trữ tệp lên Cloudflare R2 theo cấu trúc key: `context/{contextId}/{docId}.pdf` hoặc `character/{characterId}/{docId}.pdf`.
     - Database lưu đường dẫn tương đối (relative path) trong cột `document.file_url`.
     - Kích hoạt pipeline trích xuất văn bản (Text Extraction / OCR) phục vụ tạo Vector Chunks cho AI Backend.
  2. API `GET /api/v1/documents/{docId}/pdf-url`:
     - Kiểm tra quyền truy cập của người dùng đối với tài liệu `docId`.
     - Sử dụng `S3Presigner` của AWS SDK để tạo URL tải tệp có thời hạn (mặc định 60 phút).
     - Thiết lập header `responseContentDisposition` chứa tên tệp nguyên bản kèm đuôi `.pdf` để khi trình duyệt mở hoặc tải về sẽ giữ đúng tên file.
     - Trả về payload: `{ url: "https://<account_id>.r2.cloudflarestorage.com/...", expiresIn: 3600 }`.
  3. Kết quả đầu ra:
     - `HTTP 200 OK`: Upload tệp thành công hoặc sinh URL tải thành công.
     - `HTTP 400 Bad Request`: Tệp không phải PDF hoặc dung lượng vượt quá giới hạn.
     - `HTTP 401 Unauthorized`: Chưa đăng nhập hoặc token không hợp lệ.
     - `HTTP 403 Forbidden`: Người dùng không có vai trò `CONTENT_ADMIN` hoặc `SYSTEM_ADMIN`.
     - `HTTP 404 Not Found`: Không tìm thấy tài liệu theo `docId`.

---

### 🆔 US-SP0-08: Tải lên Trực tiếp Đa phương tiện 2D/3D & Phân phối qua Edge CDN (Direct Media 2D/3D Upload & Global Edge CDN Distribution)
* **Actor:** `ALL_ACTORS` (Khách hàng, Học sinh, Giáo viên, Content Admin, System Admin)
* **Thời gian thực hiện:** 15/10/2026 – 18/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:**
    - Avatar người dùng: Chính chủ tài khoản (`userId == authenticatedUserId`) hoặc `SYSTEM_ADMIN`.
    - Media nhân vật / bối cảnh / bản đồ: `CONTENT_ADMIN`, `SYSTEM_ADMIN`, hoặc `TEACHER` (đối với Local Content).
  - **Phạm vi bảo vệ dữ liệu (Data Isolation):** Toàn hệ thống (Public Media CDN)
* **User Story:**  
  Là người dùng hệ thống, tôi muốn tải lên ảnh đại diện, ảnh minh họa 2D và mô hình 3D lịch sử với tốc độ cao, đồng thời hiển thị ngay lập tức thông qua mạng lưới CDN toàn cầu của Cloudflare, để trải nghiệm giao diện trực quan mượt mà và không gặp hiện tượng giật lag hay giới hạn băng thông.

* **Acceptance Criteria (AC):**
  1. API `POST /api/v1/users/{userId}/avatar`:
     - Nhận tệp hình ảnh qua `multipart/form-data` (ảnh PNG, JPG, WEBP, tối đa 5MB).
     - Lưu lên R2 theo đường dẫn: `users/{userId}/avatar/{timestamp}_{filename}`.
     - Cập nhật cột `users.avatar_url` trong cơ sở dữ liệu.
     - Trả về URL hiển thị thông qua Cloudflare Custom Domain CDN (ví dụ: `https://media.historytalk.vn/users/...`).
  2. API Upload Media 2D & Mô hình 3D (`POST /api/v1/characters/{id}/media`, `POST /api/v1/historical-contexts/{id}/media`):
     - Hỗ trợ ảnh 2D (`IMAGE_2D`: JPEG, PNG, WEBP tối đa 10MB) và mô hình 3D (`MODEL_3D`: GLB, GLTF tối đa 100MB).
     - Đối với file lớn 3D: Thiết lập timeout HTTP Client của AWS S3 SDK tối thiểu 5 phút để tránh lỗi đứt kết nối (`SocketTimeoutException`).
     - Tự động sinh `public_url` dựa trên Custom Domain cấu hình sẵn `R2_PUBLIC_URL`.
  3. Cấu hình Cloudflare Cache & Edge TTL:
     - Tích hợp CDN Cache TTL từ 7 - 30 ngày đối với tài nguyên tĩnh công khai (ảnh avatar, texture 3D).
     - Hỗ trợ đầy đủ CORS Headers (`GET`, `HEAD`, `OPTIONS`) cho Web Frontend và Three.js Engine tải mô hình 3D mượt mà.
  4. Kết quả đầu ra:
     - `HTTP 200 OK`: Tải lên và trả về metadata media thành công.
     - `HTTP 400 Bad Request`: Định dạng tệp không được hỗ trợ hoặc file rỗng.
     - `HTTP 403 Forbidden`: Thao tác sửa avatar của người dùng khác mà không có quyền Admin.
     - `HTTP 413 Payload Too Large`: Dung lượng tệp vượt ngưỡng cho phép (5MB avatar, 100MB model 3D).

---

### 🆔 US-SP0-09: Quy trình Đồng bộ Dữ liệu Không gián đoạn & Cơ chế Dự phòng Nóng (Zero-Downtime Data Migration & Instant Rollback)
* **Actor:** `SYSTEM_ADMIN`
* **Thời gian thực hiện:** 17/10/2026 – 19/10/2026
* **Phụ trách:** 🟢 Khải (KhaiVDD)
* **Quyền hạn (RBAC) & Phạm vi (Scope):**
  - **Vai trò được phép:** `SYSTEM_ADMIN` (DevOps & Database Administrator)
  - **Phạm vi bảo vệ dữ liệu (Data Isolation):** Toàn bộ kho lưu trữ nhị phân hệ thống (Object Storage Buckets)
* **User Story:**  
  Là System Administrator, tôi muốn di chuyển toàn bộ tệp tin hiện có từ Supabase Storage sang Cloudflare R2 an toàn mà không làm gián đoạn dịch vụ của người dùng, kèm theo cơ chế kích hoạt dự phòng nóng (Rollback) trong vòng dưới 1 phút nếu có sự cố, để đảm bảo tính liên tục tuyệt đối cho nền tảng.

* **Acceptance Criteria (AC):**
  1. Đồng bộ dữ liệu cũ an toàn qua công cụ `rclone` (S3-to-S3 Protocol):
     - Cấu hình remote `supabase` (endpoint S3 của Supabase) và remote `r2` (Cloudflare R2 endpoint).
     - Thực thi lệnh đồng bộ nền (Initial Sync) với cờ kiểm tra hash toàn vẹn MD5/ETag:
       `rclone sync supabase:documents r2:historytalk-documents-prod --transfers=16 --checkers=32 --fast-list`
     - Không can thiệp sửa đổi bảng cơ sở dữ liệu vì các cột đường dẫn (`document.file_url`, `users.avatar_url`) đã được lưu dạng relative key.
  2. Thực hiện Delta Sync trước giờ G chuyển đổi (Cutover Window):
     - Đồng bộ các tệp mới phát sinh trong quá trình vận hành trước khi tráo đổi cấu hình backend.
  3. Kế hoạch Chuyển đổi Nóng (Production Cutover):
     - Cập nhật biến môi trường trên server: `STORAGE_PROVIDER=r2`.
     - Khởi động lại dịch vụ backend (thời gian khởi động < 30 giây).
  4. Cơ chế Hoàn nguyên Tức thì (Instant Rollback Plan):
     - Trong trường hợp R2 gặp sự cố mạng hoặc lỗi truy cập: Đổi lại `STORAGE_PROVIDER=supabase` và khởi động lại backend.
     - Hệ thống tự động kích hoạt `SupabaseDocumentStorageService` mà không cần rebuild mã nguồn.
  5. Giám sát & Hoàn tất (Post-migration Decommission):
     - Theo dõi giám sát log hệ thống và tỉ lệ lỗi trong 72 giờ đầu.
     - Khóa quyền ghi bucket Supabase (Read-only) trong 14 ngày làm bản sao lưu an toàn trước khi dọn dẹp giải phóng dung lượng.
