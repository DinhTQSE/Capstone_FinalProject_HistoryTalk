# 💳 HistoryTalk Java Backend - Payment & Billing Specification (PayOS)

This specification details the payment flow, PayOS checkout integration, webhook fulfillment, subscription tier management, and billing history tracking.

---

## 1. Overview & Subscription Model

HistoryTalk offers tier-based subscriptions (`free`, `plus`, `pro`) that grant monthly AI chat tokens:

| Tier Title | Price (VND) | Monthly Tokens | Duration |
|---|---|---|---|
| `free` | 0 VND | 20 tokens | 1 Month |
| `plus` | 49,000 VND | 100 tokens | 1 Month |
| `pro` | 99,000 VND | 999 tokens | 1 Month |

---

## 2. Payment Flow Architecture (PayOS Integration)

```mermaid
sequenceDiagram
    autonumber
    actor User as Customer Client
    participant Java as Java Backend
    participant PayOS as PayOS Gateway
    participant DB as PostgreSQL

    User->>Java: POST /api/v1/payments/create-order (tierId)
    Java->>DB: Create payment_order (status: PENDING, order_code: unique BIGINT)
    Java->>PayOS: Call PayOS SDK createPaymentLink()
    PayOS-->>Java: Return paymentLinkId, checkoutUrl, qrCode
    Java-->>User: Return Checkout URL & QR Code

    alt Customer pays on PayOS portal
        User->>PayOS: Scan QR / Complete Payment
        PayOS->>Java: Webhook POST /api/v1/payments/payos/webhook (code: "00")
        Java->>DB: Record payment_transaction & update payment_order (PAID, fulfilled: TRUE)
        Java->>DB: Upgrade user_tier & increment user token balance
        PayOS-->>User: Redirect to payos.return-url (/payment/success?orderCode=...)
    else Customer cancels or order expires
        User->>PayOS: Click Cancel
        PayOS-->>User: Redirect to payos.cancel-url (/payment/cancel?orderCode=...)
    end
```

---

## 3. Order Fulfillment & Double-Fulfillment Protection

To guarantee 100% reliability even if Webhook delivery is delayed or retried:
1. **Webhook Handler (`/payos/webhook`)**:
   - Validates PayOS HMAC signature key (`payos.checksum-key`).
   - Checks `payment_order.fulfilled == FALSE`.
   - Idempotently updates order status to `PAID`, sets `fulfilled = true`, `fulfilled_at = NOW()`.
   - Upgrades `user_tier` active record and adds `limited_token` to `user.token`.

2. **Frontend Return Callback (`/finish-payment`)**:
   - When client lands on return URL, client calls `POST /api/v1/payments/finish-payment?orderCode=...`.
   - Backend queries PayOS API directly to check actual payment status. If paid and `fulfilled == false`, executes fulfillment immediately.

---

## 4. Payment API Endpoints Reference

| Method | Endpoint | Access Level | Description |
|---|---|---|---|
| `GET` | `/api/v1/payments/tiers` | Public | List all active subscription pricing tiers (`free`, `plus`, `pro`). |
| `POST` | `/api/v1/payments/create-order` | Authenticated | Create a new PayOS payment order for a selected tier. |
| `POST` | `/api/v1/payments/finish-payment` | Authenticated | Process order completion & verify payment status on return URL redirect. |
| `GET` | `/api/v1/payments/history` | Authenticated | Get current user's payment order history. |
| `GET` | `/api/v1/payments/admin/history` | `SYSTEM_ADMIN` | List all system payment transactions with pagination & status filters. |
| `POST` | `/api/v1/payments/payos/webhook` | Public | Webhook listener endpoint for PayOS payment notifications. |
