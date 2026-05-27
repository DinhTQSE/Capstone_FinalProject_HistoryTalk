# Implementation Summary – Payment History API (Admin + Customer)

**Date Completed:** May 27, 2026  
**Build Status:** ✅ `mvn -q -DskipTests compile` – BUILD SUCCESS  

---

## Kết Quả

Tách endpoint lịch sử thanh toán thành hai endpoint riêng biệt theo vai trò:

- `GET /api/v1/payments/me` – Customer xem lịch sử đơn hàng của chính mình (đổi tên từ `/history`).
- `GET /api/v1/payments/history` – System Admin xem lịch sử toàn bộ khách hàng, có phân trang và filter tùy chọn theo `status` / `userId`.

Compile sạch, không thay đổi `SecurityConfig`, không thay đổi `PaymentHistoryResponse` DTO hiện có, không ảnh hưởng các endpoint khác.

---

## Danh Sách File Đã Thay Đổi

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `dto/payment/AdminPaymentHistoryResponse.java` | Response DTO dành riêng cho admin – gồm toàn bộ fields của customer DTO cộng thêm `userId`, `userName`, `userEmail` để admin biết đơn hàng thuộc về khách hàng nào |

### ✏️ File Sửa

| File | Thay đổi chính |
|------|---------------|
| `repository/payment/PaymentOrderRepository.java` | Thêm method `findAllForAdmin(status, userId, pageable)` – JPQL với `JOIN FETCH` trên `user` và `tier` (tránh N+1), hai filter đều optional (truyền `null` = bỏ qua filter đó) |
| `service/payment/PaymentService.java` | Đổi tên `getPaymentHistory(uid)` → `getMyPaymentHistory(uid)` (logic không đổi); thêm method mới `getAllPaymentHistory(status, userId, pageable)` trả về `PaginatedResponse<AdminPaymentHistoryResponse>` |
| `controller/payment/PaymentController.java` | Đổi tên mapping `/history` → `/me`, thêm `@PreAuthorize("hasRole('CUSTOMER')")` cho endpoint customer; thêm `GET /history` mới với `@PreAuthorize("hasRole('SYSTEM_ADMIN')")` và các `@RequestParam` tùy chọn |

### ⛔ File Không Thay Đổi

| File | Lý do |
|------|-------|
| `config/SecurityConfig.java` | Method-level security qua `@PreAuthorize` đã đủ – `@EnableMethodSecurity(prePostEnabled = true)` đã bật từ trước |
| `entity/enums/PaymentOrderStatus.java` | Enum hiện tại đủ dùng cho cả filter lẫn response |
| `dto/payment/PaymentHistoryResponse.java` | Customer DTO giữ nguyên – admin có DTO riêng |

---

## API Endpoints

### Customer – Xem lịch sử của chính mình

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/payments/me` | JWT – `CUSTOMER` role |

**Response:**
```json
{
  "data": [
    {
      "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "orderCode": 1716800123456789,
      "tierId": "a1b2c3d4-...",
      "tierTitle": "Premium 1 Month",
      "amount": 99000,
      "status": "PAID",
      "paymentLinkId": "2e4acf1083304877bf1a8c108b30cccd",
      "createdAt": "2026-05-27T06:00:00",
      "paidAt": "2026-05-27T06:05:00",
      "expiredAt": "2026-05-27T06:15:00"
    }
  ],
  "message": "Payment history retrieved successfully",
  "success": true
}
```

---

### Admin – Xem lịch sử toàn bộ khách hàng

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/v1/payments/history` | JWT – `SYSTEM_ADMIN` role |

**Query params tùy chọn:**

| Param | Type | Mô tả |
|-------|------|-------|
| `status` | `PaymentOrderStatus` | Lọc theo trạng thái: `PENDING`, `PAID`, `CANCELLED`, `EXPIRED`, `FAILED` |
| `userId` | `UUID` | Lọc theo một khách hàng cụ thể |
| `page` | `int` | Số trang (0-indexed, mặc định `0`) |
| `size` | `int` | Kích thước trang (mặc định `20`) |

**Ví dụ gọi:**
```
GET /api/v1/payments/history                                     → tất cả đơn hàng
GET /api/v1/payments/history?status=PAID                         → chỉ đơn đã thanh toán
GET /api/v1/payments/history?userId=3fa85f64-5717-4562-b3fc-...  → 1 khách hàng cụ thể
GET /api/v1/payments/history?status=PAID&userId=...&page=0&size=10
```

**Response:**
```json
{
  "data": {
    "content": [
      {
        "orderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "orderCode": 1716800123456789,
        "tierId": "a1b2c3d4-...",
        "tierTitle": "Premium 1 Month",
        "amount": 99000,
        "status": "PAID",
        "paymentLinkId": "2e4acf1083304877bf1a8c108b30cccd",
        "createdAt": "2026-05-27T06:00:00",
        "paidAt": "2026-05-27T06:05:00",
        "expiredAt": "2026-05-27T06:15:00",
        "userId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
        "userName": "nguyen_van_a",
        "userEmail": "nguyenvana@example.com"
      }
    ],
    "totalElements": 42,
    "totalPages": 3,
    "currentPage": 0,
    "pageSize": 20,
    "hasNext": true,
    "hasPrevious": false
  },
  "message": "Payment history retrieved successfully",
  "success": true
}
```

---

## Luồng Xử Lý

### Customer (`GET /me`)

```
[Frontend] gọi với Bearer token của CUSTOMER
   │
   └─ GET /api/v1/payments/me
         │
         PaymentController.getMyPaymentHistory()
         │   └─ đọc uid từ JWT qua SecurityUtils.getUserId()
         │
         PaymentService.getMyPaymentHistory(uid)
              │
              └─ PaymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(uid)
                      → List<PaymentOrder> (chỉ orders của uid đó)
                      → map sang List<PaymentHistoryResponse>
```

### Admin (`GET /history`)

```
[Frontend / Admin Dashboard] gọi với Bearer token của SYSTEM_ADMIN
   │
   └─ GET /api/v1/payments/history?status=...&userId=...&page=...&size=...
         │
         PaymentController.getAllPaymentHistory(status, userId, page, size)
         │   └─ @PreAuthorize("hasRole('SYSTEM_ADMIN')") – 403 nếu không đủ quyền
         │
         PaymentService.getAllPaymentHistory(status, userId, pageable)
              │
              └─ PaymentOrderRepository.findAllForAdmin(status, userId, pageable)
                      │   JOIN FETCH user, JOIN FETCH tier
                      │   WHERE deletedAt IS NULL
                      │     AND (:status IS NULL OR o.status = :status)
                      │     AND (:userId IS NULL OR u.uid = :userId)
                      │   ORDER BY createdAt DESC
                      │
                      → Page<PaymentOrder>
                      → map sang List<AdminPaymentHistoryResponse>  (+ userId, userName, userEmail)
                      → wrap vào PaginatedResponse<AdminPaymentHistoryResponse>
```

---

## Phân Quyền

| Endpoint | CUSTOMER | CONTENT_ADMIN | SYSTEM_ADMIN |
|----------|----------|---------------|--------------|
| `GET /api/v1/payments/me` | ✅ | ❌ 403 | ❌ 403 |
| `GET /api/v1/payments/history` | ❌ 403 | ❌ 403 | ✅ |

---

## Điểm Kỹ Thuật Đáng Chú Ý

| # | Vấn Đề | Giải Pháp |
|---|--------|-----------|
| 1 | **N+1 query trên `user` và `tier`** | Dùng `JOIN FETCH` trong JPQL thay vì để Hibernate lazy-load từng record |
| 2 | **Filter optional** | Dùng pattern `(:param IS NULL OR field = :param)` trong JPQL – không cần nhiều method overload |
| 3 | **Không ảnh hưởng customer DTO** | Admin có `AdminPaymentHistoryResponse` riêng – `PaymentHistoryResponse` của customer không bị sờ đến |
| 4 | **SecurityConfig không đổi** | `@PreAuthorize` ở method level đã đủ vì `@EnableMethodSecurity(prePostEnabled = true)` đã được bật |
| 5 | **`status` nhận trực tiếp enum** | Spring tự convert `?status=PAID` → `PaymentOrderStatus.PAID` – không cần manual parse |
