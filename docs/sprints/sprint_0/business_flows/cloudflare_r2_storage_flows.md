# 🔄 SPRINT 0 — BUSINESS FLOWS (CLOUDFLARE R2 STORAGE)
> **Chủ đề:** Quy trình Tải lên Tệp tin, Cấp phát Presigned S3 URL, Phân phối Edge CDN & Đồng bộ Dữ liệu Không Gián Đoạn  
> **Áp dụng cho:** Sprint 0 (13/10/2026 – 19/10/2026)  

---

## 1. LUỒNG TẢI LÊN & PHÂN PHỐI TỆP TIN (PDF & MEDIA 2D/3D)

```mermaid
sequenceDiagram
    autonumber
    actor Client as Người dùng / Admin / Browser
    participant API as Java Backend (Controllers)
    participant Storage as StorageService (CloudflareR2StorageService)
    participant DB as PostgreSQL Database
    participant R2 as Cloudflare R2 Bucket
    participant CDN as Cloudflare Edge CDN

    Client->>API: 1. POST Multipart File (PDF, Avatar, Media 2D/3D)
    Note over API: Kiểm tra Định dạng MIME, Kích thước tệp & Quyền hạn RBAC

    alt Tệp không hợp lệ hoặc Quá dung lượng
        API-->>Client: HTTP 400 Bad Request / HTTP 413 Payload Too Large
    else Tệp hợp lệ
        API->>Storage: 2. uploadPdf(...) hoặc uploadFile(InputStream, ...)
        Storage->>R2: 3. AWS SDK v2 S3Client.putObject(Bucket, Key, Stream)
        R2-->>Storage: 4. Phản hồi ETag xác nhận ghi tệp thành công

        Storage-->>API: 5. Trả về Relative Storage Path (vd: context/{id}/{docId}.pdf)
        API->>DB: 6. Cập nhật đường dẫn tệp vào Database (document / users / media)
        DB-->>API: 7. Ghi nhận dữ liệu thành công

        alt Tài nguyên công khai (Avatar, Media 2D, Model 3D)
            API->>Storage: 8. getPublicUrl(storagePath)
            Storage-->>API: Trả về CDN URL (https://media.historytalk.vn/...)
            API-->>Client: 9. HTTP 200 OK kèm Metadata & Link hiển thị CDN
            Client->>CDN: 10. GET Media qua CDN Edge Caching (Độ trễ thấp, 0đ Egress)
        else Tài liệu học thuật cần bảo vệ (PDF Document)
            API-->>Client: 11. HTTP 200 OK kèm Document Metadata (Chưa kèm URL trực tiếp)
        end
    end
```

---

## 2. LUỒNG SINH PRESIGNED S3 URL XEM / TẢI TÀI LIỆU BẢO MẬT

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Content Admin / System Admin
    participant DocCtrl as DocumentFileController
    participant DocSvc as DocumentFileServiceImpl
    participant Storage as StorageService (CloudflareR2StorageService)
    participant Presigner as AWS SDK S3Presigner
    participant R2 as Cloudflare R2 Storage

    Admin->>DocCtrl: 1. GET /api/v1/documents/{docId}/pdf-url kèm Bearer Token
    DocCtrl->>DocSvc: 2. createPdfUrl(docId, staffId, staffRole)
    Note over DocSvc: Kiểm tra quyền truy cập tài liệu trong DB

    DocSvc->>Storage: 3. createSignedPdfUrl(storagePath, duration, fileName)
    Storage->>Presigner: 4. presignGetObject (Bucket, Key, responseContentDisposition, exp: 60p)
    Presigner-->>Storage: 5. Sinh Signed Presigned URL (chữ ký HMAC-SHA256)

    Storage-->>DocSvc: 6. Trả về DocumentPdfUrl {url, expiresIn}
    DocSvc-->>DocCtrl: 7. Đóng gói DocumentPdfUrlResponse
    DocCtrl-->>Admin: 8. HTTP 200 OK kèm Presigned URL có chữ ký bảo mật

    Admin->>R2: 9. Truy cập URL tải file trực tiếp từ Cloudflare R2
    Note over R2: R2 kiểm tra chữ ký & thời hạn URL (Zero Egress Bandwidth)
    R2-->>Admin: 10. Trả về luồng byte PDF kèm tên file đã mã hóa đúng chuẩn
```

---

## 3. QUY TRÌNH ĐỒNG BỘ DỮ LIỆU CŨ & CƠ CHẾ DỰ PHÒNG CHUYỂN ĐỔI (CUTOVER & ROLLBACK)

```mermaid
sequenceDiagram
    autonumber
    actor DevOps as System Admin (DevOps)
    participant Rclone as rclone Migration Engine
    participant Supabase as Supabase Storage (Nguồn)
    participant R2 as Cloudflare R2 (Đích)
    participant App as Java Backend Container

    Note over DevOps,R2: Giai đoạn 1: Đồng bộ nền dữ liệu lịch sử
    DevOps->>Rclone: 1. Chạy rclone sync supabase:documents r2:historytalk-documents-prod
    Rclone->>Supabase: 2. Quét danh sách đối tượng & đọc stream qua S3 API
    Rclone->>R2: 3. Ghi đối tượng sang R2 (16 transfers song song)
    Rclone->>DevOps: 4. Báo cáo hoàn tất Initial Sync & Khớp mã Hash Checksum MD5/ETag

    Note over DevOps,App: Giai đoạn 2: Giờ G (Production Cutover Window)
    DevOps->>Rclone: 5. Chạy Delta Sync (Đồng bộ các tệp mới phát sinh trong vài giờ qua)
    DevOps->>App: 6. Cập nhật biến môi trường STORAGE_PROVIDER=r2 & Restart App (< 30s)
    Note over App: Spring Boot kích hoạt Bean CloudflareR2StorageService
    App-->>DevOps: 7. Hệ thống trực tuyến, toàn bộ đọc/ghi chuyển sang Cloudflare R2

    alt Hệ thống hoạt động ổn định
        DevOps->>Supabase: 8. Khóa bucket Supabase thành Read-only trong 14 ngày trước khi xóa
    else Phát hiện sự cố khẩn cấp trên Cloudflare R2
        Note over DevOps,App: Kế hoạch Rollback tức thì (Instant Rollback < 1 phút)
        DevOps->>App: 9. Đổi lại STORAGE_PROVIDER=supabase & Restart App
        Note over App: Tự động kích hoạt SupabaseDocumentStorageService không cần rebuild mã nguồn
        App-->>DevOps: 10. Hoàn nguyên an toàn về Supabase Storage cũ
    end
```

---

## 4. QUY TRÌNH ĐIỀU HƯỚNG NHÀ CUNG CẤP & XỬ LÝ NGOẠI LỆ TỆP TIN

```mermaid
flowchart TD
    Start["Bắt đầu: Khởi động Ứng dụng Spring Boot"] --> CheckProvider{"Kiểm tra cấu hình: 'app.storage.provider'"}
    
    CheckProvider -- "Giá trị 'r2' (Mặc định)" --> LoadR2["Khởi tạo CloudflareR2StorageService\n(Inject S3Client & S3Presigner)"]
    CheckProvider -- "Giá trị 'supabase'" --> LoadSupabase["Khởi tạo SupabaseDocumentStorageService\n(Inject RestClient Supabase)"]
    
    LoadR2 --> AppReady["Ứng dụng sẵn sàng phục vụ yêu cầu tệp"]
    LoadSupabase --> AppReady
    
    AppReady --> Req["Tiếp nhận yêu cầu tải tệp (Upload / Presign / Delete)"]
    Req --> Exec["Thực thi phương thức tương ứng qua StorageService Interface"]
    
    Exec --> TryOp{"Thực thi tác vụ đối với Object Storage?"}
    TryOp -- "Thành công" --> RetSuccess["Ghi nhận đường dẫn tương đối vào DB & Trả kết quả HTTP 200/201"]
    
    TryOp -- "Lỗi kết nối / Socket Timeout" --> HandleTimeout["Log Cảnh báo: S3Exception / SocketTimeoutException\nThiết lập retry tự động hoặc trả HTTP 500 lỗi lưu trữ"]
    
    TryOp -- "Không tìm thấy tệp (NoSuchKey)" --> HandleNotFound["Log Cảnh báo: NoSuchKeyException\nTrả về HTTP 404 Không tìm thấy tài nguyên tệp"]

    RetSuccess --> End["Hoàn tất chu trình"]
    HandleTimeout --> End
    HandleNotFound --> End
```
