# Implementation Summary – PayOS Return-URL Callback API

**Date Completed:** May 25, 2026  
**Build Status:** ✅ `mvn -q -DskipTests compile` – BUILD SUCCESS  

---

## Kết Quả

Thêm endpoint `POST /api/v1/payments/payos/return` để frontend gọi ngay sau khi PayOS redirect người dùng về `cancelUrl` hoặc `returnUrl`. Endpoint đồng bộ trạng thái đơn hàng vào DB ngay lập tức, không cần chờ async webhook đến sau. Compile sạch, không thay đổi `SecurityConfig` hay enum `PaymentOrderStatus`.

---

## Danh Sách File Đã Thay Đổi

### 🆕 File Mới Tạo

| File | Mô tả |
|------|-------|
| `dto/payment/PayOSReturnRequest.java` | Request DTO – 5 fields từ PayOS redirect params: `code`, `id`, `cancel`, `status`, `orderCode` |
| `dto/payment/PayOSReturnResponse.java` | Response DTO – `orderCode`, `resolvedStatus`, `message` trả về cho frontend |

### ✏️ File Sửa

| File | Thay đổi chính |
|------|---------------|
| `service/payment/PaymentService.java` | Thêm `handlePayOSReturn(UUID uid, PayOSReturnRequest)` + 3 private helpers: `isTerminal()`, `resolveStatus()`, `buildResponse()` |
| `controller/payment/PaymentController.java` | Thêm `POST /api/v1/payments/payos/return` mapping; đọc `uid` từ JWT qua `SecurityUtils.getUserId()` |

### ⛔ File Không Thay Đổi

| File | Lý do |
|------|-------|
| `config/SecurityConfig.java` | Endpoint mới nằm trong `.anyRequest().authenticated()` có sẵn – không cần thêm rule |
| `entity/enums/PaymentOrderStatus.java` | Enum hiện tại đủ dùng – `PROCESSING` của PayOS được map sang `PENDING` |

---

## API Endpoint

| Method | Path | Auth |
|--------|------|------|
| POST | `/api/v1/payments/payos/return` | JWT (authenticated) |

**Request body:**
```json
{
  "code": "00",
  "id": "2e4acf1083304877bf1a8c108b30cccd",
  "cancel": true,
  "status": "CANCELLED",
  "orderCode": 803347
}
```

**Response:**
```json
{
  "data": {
    "orderCode": 803347,
    "resolvedStatus": "CANCELLED",
    "message": "Payment has been cancelled."
  },
  "message": "Payment has been cancelled.",
  "success": true
}
```

---

## Luồng Xử Lý

```
[Frontend] nhận redirect từ PayOS với query params
   │
   └─ POST /api/v1/payments/payos/return  (Bearer token)
         │
         PaymentController.handlePayOSReturn()
         │
         PaymentService.handlePayOSReturn(uid, request)
              │
              ├─ 1. findByOrderCode(orderCode)
              │       └─ 404 nếu không tìm thấy
              │
              ├─ 2. Ownership guard
              │       └─ order.user.uid == jwt.uid
              │          (400 nếu không khớp)
              │
              ├─ 3. Idempotency guard
              │       └─ Nếu order đã là CANCELLED/PAID/EXPIRED/FAILED
              │          → return ngay, không ghi DB
              │
              ├─ 4. resolveStatus(request)
              │       cancel=true hoặc status="CANCELLED"  → CANCELLED
              │       status="PAID"                        → PAID (UI sync only)
              │       status="PENDING"/"PROCESSING"        → null (no change)
              │
              ├─ 5. Persist nếu status thay đổi
              │       CANCELLED → order.setStatus(CANCELLED) + save
              │       PAID      → order.setStatus(PAID) + setPaidAt(now) + save
              │                   ⚠️ KHÔNG gọi upgradeUserTier()
              │
              └─ 6. buildResponse(orderCode, finalStatus)
                      → PayOSReturnResponse { resolvedStatus, message }
```

---

## Mapping Trạng Thái PayOS → Nội Bộ

| PayOS `status` | `PaymentOrderStatus` nội bộ | Hành động |
|---|---|---|
| `PAID` | `PAID` | Sync UI – tier upgrade vẫn do webhook đảm nhiệm |
| `CANCELLED` | `CANCELLED` | Đặt order thành CANCELLED |
| `PENDING` | `PENDING` | Không thay đổi – trả về trạng thái hiện tại |
| `PROCESSING` | `PENDING` | Coi như PENDING – webhook sẽ gửi kết quả cuối |

---

## Nội Dung `message` Theo Trạng Thái

| `resolvedStatus` | `message` |
|---|---|
| `CANCELLED` | `"Payment has been cancelled."` |
| `PAID` | `"Payment has already been confirmed."` |
| `PENDING` | `"Payment is pending. Please wait for confirmation."` |
| `EXPIRED` | `"Payment link has expired. Please create a new order."` |
| `FAILED` | `"Payment failed. Please try again."` |

---

## Các Điểm Lưu Ý (Known Limitations)

| # | Vấn Đề | Ghi Chú |
|---|--------|--------|
| 1 | **Không nâng cấp tier từ endpoint này** | Tier upgrade chỉ xảy ra qua webhook đã xác thực HMAC trong `PaymentWebhookService.handlePaid()`. Endpoint này chỉ sync trạng thái UI |
| 2 | **Idempotent nhưng có race** | Nếu webhook đến trước, call này sẽ thấy order đã terminal và return đúng – không lỗi |
| 3 | **Không có `cancelledAt` timestamp** | `PaymentOrder` chỉ có `paidAt`; chưa có trường timestamp cho hủy đơn |
| 4 | **`orderCode` phải là `Long`** | Frontend cần gửi số nguyên (không phải string) trong JSON body |
