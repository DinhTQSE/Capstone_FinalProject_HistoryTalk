# Kế Hoạch Chi Tiết: Chuyển Đổi Hệ Thống Lưu Trữ Từ Supabase Storage Sang Cloudflare R2

> **Mục tiêu:** Chuyển đổi toàn bộ giải pháp lưu trữ tệp (PDF tài liệu, ảnh đại diện người dùng, hình ảnh 2D, mô hình 3D) từ **Supabase Storage** sang **Cloudflare R2** (S3-compatible Object Storage), nhằm tối ưu chi phí (0đ phí băng thông tải ra - Zero Egress Fees), tăng tốc độ tải file qua Cloudflare CDN Edge, chuẩn hóa kiến trúc với AWS S3 SDK v2 và loại bỏ vendor lock-in.

---

## 1. Bối Cảnh & Động Lực Chuyển Đổi

### 1.1. So sánh Supabase Storage vs Cloudflare R2

| Tiêu chí | Supabase Storage (Hiện tại) | Cloudflare R2 (Đích đến) | Lợi ích khi chuyển đổi |
| :--- | :--- | :--- | :--- |
| **Phí băng thông ra (Egress)** | Tính phí sau hạn mức free tier ($0.09/GB) | **$0.00 / GB (Hoàn toàn miễn phí Egress)** | Tiết kiệm chi phí vận hành lâu dài khi tài liệu PDF và mô hình 3D dung lượng lớn tăng cao. |
| **Giao thức chuẩn** | REST API độc quyền của Supabase | **AWS S3 API v2 Chuẩn công nghiệp** | Tránh vendor lock-in, dễ dàng chuyển sang AWS S3, MinIO, GCP nếu cần. |
| **Mạng phân phối CDN** | Phụ thuộc proxy Supabase | **Tích hợp sâu mạng lưới Anycast Edge Cloudflare toàn cầu** | Tốc độ phân phối tệp và ảnh công khai (Avatar, Media) cực nhanh, độ trễ thấp. |
| **Giới hạn & Quản lý** | Bị ràng buộc chung trong gói Supabase Project | Độc lập, khả năng mở rộng không giới hạn | Tách biệt rủi ro: Supabase chỉ tập trung lưu trữ Database & Vector Chunks (pgvector). |

> [!NOTE]
> **Phạm vi tác động:**
> - **Java Backend (`history-talk-backend-Java`)**: Là nơi duy nhất xử lý lưu trữ tệp tin nhị phân (PDF, hình ảnh, 3D model, avatar). Cần refactor để hỗ trợ Cloudflare R2.
> - **AI Backend (`history-talk-backend-AI`)**: Chỉ sử dụng Supabase PostgreSQL (bảng `vector_chunk` và RPC vector search), **hoàn toàn KHÔNG** gọi Supabase Object Storage. Do đó service AI không bị ảnh hưởng.
> - **PostgreSQL Database**: Các cột `document.file_url`, `users.avatar_url`, và `document_media_metadata.storage_path` hiện tại **chỉ lưu đường dẫn tương đối (relative object key)** (ví dụ: `context/{id}/{docId}.pdf`), **không lưu domain Supabase**. Do đó **không cần migrate dữ liệu bảng trong Database**!

---

## 2. Hiện Trạng Hệ Thống & Phân Tích Kỹ Thuật

### 2.1. Các điểm tương tác Storage trong Java Backend

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HIỆN TRẠNG (BEFORE)                             │
└────────────────────────────────────────────────────────────────────────┘

    [UserAvatarServiceImpl]          [DocumentFileServiceImpl]        [DocumentMediaServiceImpl]
              │                                 │                                 │
              ├─────────────────────────────────┼─────────────────────────────────┤
              ▼                                 ▼                                 ▼
    [DirectBinaryUploadStrategy] ──────► [SupabaseDocumentStorageService]
                                                        │
                                                        ▼ (REST Client)
                                            ┌─────────────────────────┐
                                            │ Supabase Storage API    │
                                            │ (https://xxx.supabase.co│
                                            │ /storage/v1/object/...) │
                                            └─────────────────────────┘
```

Các tính năng đang phụ thuộc trực tiếp vào `SupabaseDocumentStorageService`:
1. **Upload tài liệu PDF**: `uploadPdf(...)` (MultipartFile -> Byte Array -> Supabase Storage).
2. **Download tài liệu PDF**: `downloadPdf(...)` (Đọc byte array từ storage để server stream về client).
3. **Ký URL tải PDF**: `createSignedPdfUrl(...)` (Tạo signed URL có thời hạn kèm query param download name).
4. **Presigned Upload URL**: `generatePresignedUploadUrl(...)` (Tạo URL cho phép client upload trực tiếp).
5. **Signed View URL**: `createSignedUrl(...)` (Tạo URL có chữ ký xem ảnh / media).
6. **Upload file trực tiếp qua Stream**: `uploadFile(InputStream, ...)` (Dùng cho ảnh 2D avatar, media và 3D model).
7. **Xóa file**: `deleteFile(...)`.
8. **Public URL media**: Đang được ghép thủ công trong `DocumentMediaServiceImpl.mapToResponse()` qua `${supabase.url}/storage/v1/object/public/...`.

---

## 3. Kiến Trúc Đích (Target Architecture)

Áp dụng nguyên lý **Clean Architecture** (Dependency Inversion & Interface Segregation): Không phụ thuộc trực tiếp vào bất kỳ nhà cung cấp storage cụ thể nào.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        KIẾN TRÚC MỚI (AFTER)                           │
└────────────────────────────────────────────────────────────────────────┘

    [UserAvatarServiceImpl]          [DocumentFileServiceImpl]        [DocumentMediaServiceImpl]
              │                                 │                                 │
              ├─────────────────────────────────┴─────────────────────────────────┤
              ▼                                                                   ▼
    [DirectBinaryUploadStrategy]                                                  │
              │                                                                   │
              └─────────────────────────────────┬─────────────────────────────────┘
                                                ▼
                                    «interface» StorageService
                                                ▲
                       ┌────────────────────────┴────────────────────────┐
                       │                                                 │
            [CloudflareR2StorageService]                     [SupabaseDocumentStorageService]
            (@ConditionalOnProperty                          (@ConditionalOnProperty
             app.storage.provider=r2)                         app.storage.provider=supabase)
                       │                                                 │
                       ▼ (AWS SDK v2 S3Client)                           ▼ (RestClient)
            ┌─────────────────────────┐                      ┌─────────────────────────┐
            │   Cloudflare R2 Bucket  │                      │    Supabase Storage     │
            │   (S3-Compatible API)   │                      │     (Fallback/Backup)   │
            └─────────────────────────┘                      └─────────────────────────┘
```

### 3.1. Thiết kế `StorageService` Interface

```java
package com.historytalk.service.storage;

import com.historytalk.entity.enums.EntityType;
import com.historytalk.service.document.DownloadedDocumentFile;
import com.historytalk.service.document.DocumentPdfUrl;
import com.historytalk.service.document.UploadedDocumentFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

public interface StorageService {
    // 1. Upload nghiệp vụ PDF
    UploadedDocumentFile uploadPdf(EntityType entityType, UUID entityId, UUID docId, MultipartFile file);

    // 2. Upload file nhị phân tổng quát
    void uploadFile(String storagePath, InputStream inputStream, String contentType, long fileSizeBytes);

    // 3. Tải nội dung file nhị phân
    DownloadedDocumentFile downloadPdf(String objectPath);

    // 4. Sinh Presigned URL để download / view file bảo mật (PDF, media nội bộ)
    DocumentPdfUrl createSignedPdfUrl(String objectPath, Duration duration, String downloadFileName);
    String createSignedUrl(String storagePath, Duration duration);

    // 5. Sinh Presigned URL để client tự upload trực tiếp
    String generatePresignedUploadUrl(String storagePath, String contentType, Duration duration);

    // 6. Xóa file
    void deleteFile(String storagePath);

    // 7. Lấy URL công khai (cho Avatar, Media 2D/3D public qua Custom Domain CDN)
    String getPublicUrl(String storagePath);
}
```

---

## 4. Kế Hoạch Triển Khai Chi Tiết (5 Giai Đoạn)

### Giai Đoạn 1: Thiết Lập Hạ Tầng Cloudflare R2 (Dev & Prod)

- [ ] **Bước 1.1: Tạo R2 Bucket trên Cloudflare Dashboard**
  - Đặt tên bucket: `historytalk-documents-dev` (môi trường dev) và `historytalk-documents-prod` (môi trường production).
  - Vị trí (Location): `APAC` (Châu Á - Thái Bình Dương) hoặc `Automatic` để tối ưu latency về Việt Nam.
- [ ] **Bước 1.2: Cấu hình CORS Rules cho Bucket**
  - Đảm bảo Web Frontend có thể gọi trực tiếp PUT (khi dùng presigned upload) và GET:
  ```json
  [
    {
      "AllowedOrigins": ["http://localhost:5173", "https://historytalk.vn", "https://*.historytalk.vn"],
      "AllowedMethods": ["GET", "PUT", "HEAD", "DELETE"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag", "Content-Type", "Content-Length"],
      "MaxAgeSeconds": 3600
    }
  ]
  ```
- [ ] **Bước 1.3: Tạo R2 API Tokens**
  - Quyền hạn: `Object Read & Write`.
  - Giới hạn quyền theo từng bucket tương ứng.
  - Lưu lại:
    - `Account ID`
    - `Access Key ID`
    - `Secret Access Key`
    - `Endpoint`: `https://<account_id>.r2.cloudflarestorage.com`
- [ ] **Bước 1.4: Cấu hình Custom Domain hoặc Public Access**
  - Gắn Custom Domain cho bucket phục vụ public assets (như ảnh avatar, mô hình 3D công khai): ví dụ `https://media.historytalk.vn` hoặc dùng R2 `pub-xxx.r2.dev`.
  - Cấu hình Cloudflare Cache Rules (Edge Cache TTL: 7 - 30 ngày cho static images).

---

### Giai Đoạn 2: Phát Triển & Nâng Cấp Mã Nguồn Java Backend

- [ ] **Bước 2.1: Thêm AWS S3 SDK v2 vào `pom.xml`**
  - Sử dụng AWS SDK v2 Bill of Materials (BOM) và module `s3`:
  ```xml
  <dependencyManagement>
      <dependencies>
          <dependency>
              <groupId>software.amazon.awssdk</groupId>
              <artifactId>bom</artifactId>
              <version>2.25.16</version>
              <type>pom</type>
              <scope>import</scope>
          </dependency>
      </dependencies>
  </dependencyManagement>

  <dependencies>
      <!-- AWS S3 SDK for Cloudflare R2 -->
      <dependency>
          <groupId>software.amazon.awssdk</groupId>
          <artifactId>s3</artifactId>
      </dependency>
  </dependencies>
  ```
- [ ] **Bước 2.2: Cấu hình `application.properties` & Environment Variables**
  ```properties
  # =============================================
  # Storage Configuration (Cloudflare R2 / Supabase)
  # =============================================
  app.storage.provider=${STORAGE_PROVIDER:r2}

  # Cloudflare R2 Config
  cloudflare.r2.account-id=${R2_ACCOUNT_ID:}
  cloudflare.r2.access-key-id=${R2_ACCESS_KEY_ID:}
  cloudflare.r2.secret-access-key=${R2_SECRET_ACCESS_KEY:}
  cloudflare.r2.bucket=${R2_BUCKET:historytalk-documents}
  cloudflare.r2.endpoint=https://${cloudflare.r2.account-id}.r2.cloudflarestorage.com
  cloudflare.r2.public-url=${R2_PUBLIC_URL:https://media.historytalk.vn}

  # Supabase Legacy Fallback Config
  supabase.url=${SUPABASE_URL:}
  supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY:}
  supabase.storage.bucket=${SUPABASE_STORAGE_BUCKET:documents}
  ```
- [ ] **Bước 2.3: Tạo R2 Client Bean Configuration (`R2Config.java`)**
  - Tạo `S3Client` và `S3Presigner` với:
    - `endpointOverride(URI.create(endpoint))`
    - `region(Region.of("auto"))`
    - `credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))`
    - `serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())` (bắt buộc với R2).
- [ ] **Bước 2.4: Hiện thực `CloudflareR2StorageService.java`**
  - Implement đầy đủ các method trong `StorageService`.
  - Sử dụng `S3Client.putObject`, `getObject`, `deleteObject`.
  - Sử dụng `S3Presigner.presignGetObject` (kèm override `ResponseContentDisposition` cho download name) và `presignPutObject`.
  - Xử lý chuẩn hóa path key (bỏ prefix bucket name nếu có).
- [ ] **Bước 2.5: Refactor `SupabaseDocumentStorageService.java`**
  - Cho implement `StorageService`.
  - Gắn `@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")`.
- [ ] **Bước 2.6: Cập nhật các service tiêu thụ**
  - Đổi dependency injection từ cụ thể `SupabaseDocumentStorageService` sang interface `StorageService`:
    - [DocumentFileServiceImpl.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/document/DocumentFileServiceImpl.java)
    - [DocumentMediaServiceImpl.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/document/DocumentMediaServiceImpl.java)
    - [DirectBinaryUploadStrategy.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/media/DirectBinaryUploadStrategy.java)
    - [UserAvatarServiceImpl.java](file:///c:/Users/KHAI/Documents/Historical-talk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/user/UserAvatarServiceImpl.java)
  - Trong `DocumentMediaServiceImpl.mapToResponse()`, thay thế việc ghép cứng chuỗi `${supabase.url}` bằng phương thức `storageService.getPublicUrl(metadata.getStoragePath())`.
- [ ] **Bước 2.7: Cập nhật Unit Tests & Mock Tests**
  - Viết `CloudflareR2StorageServiceTest` kiểm thử upload, download, presign, delete.
  - Cập nhật các test case hiện tại trong `DocumentFileServiceImplTest` để mock `StorageService`.

---

### Giai Đoạn 3: Di Chuyển Dữ Liệu Cũ (Data Migration: Supabase -> R2)

Do Supabase Storage cung cấp API S3 tương thích (hoặc thông qua REST API), ta có 2 phương án di chuyển dữ liệu:

#### Phương án Khuyến Nghị: Dùng `rclone` (Nhanh, an toàn, có checksum)
- [ ] **Bước 3.1: Cài đặt và cấu hình `rclone`**
  - Cấu hình remote `supabase`: dùng S3-compatible credentials của Supabase Storage (`https://<project-id>.supabase.co/storage/v1/s3`).
  - Cấu hình remote `r2`: dùng S3 credentials của Cloudflare R2.
- [ ] **Bước 3.2: Chạy đồng bộ đợt 1 (Initial Sync)**
  ```bash
  # Đồng bộ toàn bộ dữ liệu từ bucket documents của Supabase sang bucket R2
  rclone sync supabase:documents r2:historytalk-documents-prod \
      --transfers=16 \
      --checkers=32 \
      --fast-list \
      --progress
  ```
- [ ] **Bước 3.3: Kiểm tra tính toàn vẹn (Verification Script)**
  - So sánh tổng số lượng files (`rclone size`).
  - So sánh hash MD5 / ETag giữa hai bên để đảm bảo không bị corrupt file.

#### Phương án Dự Phòng: Python / Java CLI Migration Tool
- Nếu kết nối S3 của Supabase gặp hạn chế mạng, chạy script CLI đọc danh sách file từ bảng `document`, `document_media_metadata`, `users` trong database, tải từ Supabase qua HTTP GET và đẩy lên R2 qua S3 PutObject.

---

### Giai Đoạn 4: Kiểm Thử Toàn Diện (Staging & UAT)

Thực hiện kiểm thử theo checklist ma trận:

| Nghiệp vụ | Thao tác kiểm thử | Tiêu chí đạt |
| :--- | :--- | :--- |
| **PDF Document Upload** | Upload file PDF 20MB qua `/api/v1/documents/{docId}/upload-pdf` | File lưu đúng path `context/{id}/{docId}.pdf` trên R2; DB lưu path; OCR/Text extraction chạy tốt. |
| **PDF Document Download** | Tải file PDF từ `/api/v1/documents/{docId}/download-pdf` | File tải về nguyên vẹn byte, mở đọc bình thường. |
| **PDF Signed URL** | Sinh URL tải PDF từ `/api/v1/documents/{docId}/pdf-url` | URL S3 presigned hợp lệ, tải file tự động đặt tên file đúng tên tiếng Việt/ASCII đã mã hóa. |
| **User Avatar Upload** | Thay avatar qua `/api/v1/users/avatar` | Ảnh được ghi lên R2 đường dẫn `users/{userId}/avatar/...`, URL view hiển thị avatar sắc nét. |
| **Media 2D Upload** | Tải ảnh 2D minh họa nhân vật / context | File lưu đúng path, link public CDN hiển thị tức thì. |
| **Media 3D Upload** | Tải file mô hình 3D dung lượng lớn (50MB - 100MB) | Không bị timeout; file được lưu và stream tốt sang Three.js frontend. |
| **Media Deletion** | Xóa media / xóa avatar | Tệp trên R2 bị xóa sạch, metadata trong DB được gỡ bỏ. |
| **CORS Test** | Gọi GET / PUT từ trình duyệt Web local và production domain | Không bị lỗi `Access-Control-Allow-Origin`. |

---

### Giai Đoạn 5: Production Cutover & Dọn Dẹp (Decommissioning)

- [ ] **Bước 5.1: Đồng bộ lần cuối (Delta Sync)**
  - Trước giờ G (cutover window), chạy `rclone sync` để đồng bộ các file mới sinh ra trong vài giờ qua.
- [ ] **Bước 5.2: Triển khai cấu hình mới**
  - Cập nhật biến môi trường trên server production:
    - `STORAGE_PROVIDER=r2`
    - `R2_ACCOUNT_ID=...`
    - `R2_ACCESS_KEY_ID=...`
    - `R2_SECRET_ACCESS_KEY=...`
    - `R2_BUCKET=historytalk-documents-prod`
    - `R2_PUBLIC_URL=https://media.historytalk.vn`
  - Restart Java service (`mvn spring-boot:run` hoặc Docker container restart).
- [ ] **Bước 5.3: Giám sát 72 giờ**
  - Theo dõi log Java backend: Đảm bảo không có exception `S3Exception` hoặc `NoSuchKey`.
  - Theo dõi Cloudflare R2 Dashboard: Lưu lượng Request Class A (Write), Class B (Read), Bandwidth.
- [ ] **Bước 5.4: Dọn dẹp Supabase Storage**
  - Đặt bucket Supabase sang chế độ Read-Only trong 14 ngày làm backup lạnh.
  - Sau 14 ngày không có sự cố, tiến hành xóa dữ liệu trên bucket Supabase để giải phóng dung lượng quota.

---

## 5. Kế Hoạch Quản Trị Rủi Ro & Rollback Plan

### 5.1. Ma trận rủi ro & Giải pháp phòng ngừa

| Rủi ro tiềm ẩn | Mức độ | Nguyên nhân | Giải pháp phòng ngừa |
| :--- | :---: | :--- | :--- |
| **Lỗi CORS khi xem/tải file trên web** | Cao | Thiếu cấu hình AllowedOrigins trên R2 | Cấu hình sẵn CORS rules trên R2 Bucket Dashboard bao gồm cả localhost và production domains. |
| **Presigned URL bị sai domain hoặc quá hạn** | Trung bình | `S3Presigner` mặc định gắn domain S3 raw thay vì Custom Domain | Tách biệt: URL cần bảo mật (PDF, tài liệu mật) dùng S3Presigner domain R2 chuẩn; URL ảnh công cộng (avatar, illustration) dùng CDN Custom Domain trực tiếp. |
| **Lỗi timeout khi upload file 3D 100MB** | Trung bình | Socket timeout của HTTP client thấp | Cấu hình `ClientOverrideConfiguration` của AWS SDK với `apiCallTimeout = Duration.ofMinutes(5)`. |
| **Mất file phát sinh trong lúc cutover** | Thấp | User upload đúng thời điểm đang sync dữ liệu | Chạy delta sync sát giờ cutover; thời gian deploy container backend chỉ mất < 30 giây. |

### 5.2. Kế hoạch Rollback tức thì (Instant Rollback Plan)

Nhờ kiến trúc `@ConditionalOnProperty(name = "app.storage.provider")` và việc vẫn giữ nguyên code `SupabaseDocumentStorageService`:
- Nếu phát hiện bất kỳ lỗi nghiêm trọng nào trên R2:
  1. Thay đổi biến môi trường: `STORAGE_PROVIDER=supabase`.
  2. Restart lại container backend (< 1 phút).
  3. Hệ thống ngay lập tức quay lại sử dụng Supabase Storage cũ mà **không cần sửa hay build lại mã nguồn**.

---

## 6. Kế Hoạch Phân Bổ Thời Gian Dự Kiến

```text
Tuần 1:
├── Ngày 1: Tạo Cloudflare R2 Bucket, Token, CORS, Custom Domain.
├── Ngày 2: Thêm AWS SDK v2, tạo StorageService Interface & CloudflareR2StorageService.
├── Ngày 3: Refactor DocumentFileService, UserAvatarService, DocumentMediaService.
├── Ngày 4: Chạy Unit Test, Mock Test và tích hợp kiểm thử cục bộ.
└── Ngày 5: Kiểm thử Staging và đo lường hiệu năng.

Tuần 2:
├── Ngày 6: Chạy Initial Sync dữ liệu cũ qua rclone sang Cloudflare R2.
├── Ngày 7: Chạy Delta Sync + Kiểm thử đối soát dữ liệu 100%.
├── Ngày 8: Production Cutover (Chuyển STORAGE_PROVIDER=r2).
├── Ngày 9 - 11: Giám sát hệ thống 72h.
└── Ngày 12: Đóng bucket Supabase, hoàn tất bàn giao tài liệu.
```
