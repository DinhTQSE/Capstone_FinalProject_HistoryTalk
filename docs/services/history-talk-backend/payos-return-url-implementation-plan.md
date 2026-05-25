# PayOS Return-URL Callback API

## Background

When a user finishes (or cancels) a PayOS checkout, PayOS redirects the browser to
either the `returnUrl` or `cancelUrl` configured in the payment link, appending these
query parameters:

> [!NOTE]
> All three open questions from the initial review have been resolved by the user.
> Decisions are recorded under each section. The plan is now ready for phased execution.

| Param | Example | Meaning |
|---|---|---|
| `code` | `00` | `00` = success, `01` = invalid params |
| `id` | `2e4acf10…` | Payment Link Id |
| `cancel` | `true` | `true` = user cancelled, `false` = paid/pending |
| `status` | `CANCELLED` | PayOS status string |
| `orderCode` | `803347` | Our order code (Long) |

The frontend receives these as query params on its page load, then needs to **notify the
backend** so the order status is updated synchronously (without waiting for the async
webhook).

---

## Status Enum Analysis

### PayOS statuses (from the image)

| PayOS `status` | Meaning |
|---|---|
| `PAID` | Payment completed successfully |
| `PENDING` | Awaiting payment |
| `PROCESSING` | Payment is being processed by the bank |
| `CANCELLED` | User cancelled |

### Our `PaymentOrderStatus` (current)

```java
PENDING, PAID, CANCELLED, EXPIRED, FAILED
```

**Verdict: ✅ Sufficient — no enum changes needed.**

| PayOS status | Our `PaymentOrderStatus` | Action |
|---|---|---|
| `PAID` | `PAID` | Mark as PAID (UI only — tier upgrade stays in webhook) |
| `CANCELLED` | `CANCELLED` | Mark as CANCELLED |
| `PENDING` | `PENDING` | No state change — return current status |
| `PROCESSING` | `PENDING` | **Treat as PENDING** ✅ (user decision: Option A) |
| *(link timeout)* | `EXPIRED` | Handled by scheduler + webhook |

---

## Resolved Decisions

| # | Question | Decision |
|---|---|---|
| Q1 | Authentication | ✅ **Require JWT** — frontend sends Bearer token |
| Q2 | `PROCESSING` status | ✅ **Treat as `PENDING`** — no state change, let webhook finalize |
| Q3 | Idempotency guard | ✅ **Yes** — same pattern as `PaymentWebhookService` |

---

## Proposed Changes

---

## Implementation Phases

---

### Phase 1 — DTO Layer

**Goal:** Define the request/response contract.

#### [NEW] `PayOSReturnRequest.java`
`src/main/java/com/historytalk/dto/payment/PayOSReturnRequest.java`

Fields matching the PayOS redirect params:
```java
String code;        // "00" or "01"
String id;          // paymentLinkId (maps to order.paymentLinkId)
Boolean cancel;     // true = cancelled
String status;      // "PAID" | "PENDING" | "PROCESSING" | "CANCELLED"
Long orderCode;
```

#### [NEW] `PayOSReturnResponse.java`
`src/main/java/com/historytalk/dto/payment/PayOSReturnResponse.java`

```java
Long orderCode;
String resolvedStatus;   // our PaymentOrderStatus name
String message;
```

**`message` values by resolved status:**

| Resolved `PaymentOrderStatus` | `message` sent to frontend |
|---|---|
| `CANCELLED` | `"Payment has been cancelled."` |
| `PAID` | `"Payment has already been confirmed."` |
| `PENDING` | `"Payment is pending. Please wait for confirmation."` |
| `EXPIRED` | `"Payment link has expired. Please create a new order."` |
| `FAILED` | `"Payment failed. Please try again."` |

> [!NOTE]
> `PROCESSING` from PayOS is mapped to `PENDING` on our side, so it returns the PENDING message.
> The terminal idempotency case (order already `CANCELLED`/`PAID` when the call arrives)
> returns the message for the existing status — no error is thrown.

---

---

### Phase 2 — Service Layer

**Goal:** Implement the core return-handling logic in `PaymentService`.

#### [MODIFY] [PaymentService.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java)

Add a new `@Transactional` method `handlePayOSReturn(UUID uid, PayOSReturnRequest request)`:

**Step-by-step logic:**

```
1. Find PaymentOrder by orderCode
   → throw ResourceNotFoundException if not found

2. Ownership check: order.user.uid must equal uid
   → throw InvalidRequestException ("Order does not belong to this user") if mismatch

3. Idempotency guard — if order is already terminal:
   → CANCELLED, PAID, EXPIRED, FAILED → return response immediately with current status + matching message
   (no DB write, no error)

4. Resolve new status from PayOS params:
   cancel == true  OR  status == "CANCELLED"  →  CANCELLED
   status == "PAID"                           →  PAID  (UI sync only)
   status == "PENDING" or "PROCESSING"        →  no change (stay PENDING)

5. If new status is CANCELLED:
   order.setStatus(CANCELLED)
   paymentOrderRepository.save(order)

6. If new status is PAID (UI-only sync):
   order.setStatus(PAID)
   order.setPaidAt(LocalDateTime.now())
   paymentOrderRepository.save(order)
   ⚠️  Do NOT call upgradeUserTier() here.

7. Build and return PayOSReturnResponse with resolvedStatus + message
```

> [!WARNING]
> **Never call `upgradeUserTier()` from this endpoint.** Tier upgrade must only happen
> through the HMAC-verified webhook path in `PaymentWebhookService.handlePaid()`,
> which validates amount and paymentLinkId. This endpoint is UI-driven and untrusted
> for financial decisions.

---

### Phase 3 — Controller Layer

**Goal:** Expose the new endpoint via REST.

#### [MODIFY] [PaymentController.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java)

Add one new `@PostMapping` inside the existing controller:

```
POST /api/v1/payments/payos/return
Authorization: Bearer <jwt>   (required)
Content-Type: application/json
Body: PayOSReturnRequest
Response: ApiResponse<PayOSReturnResponse>
```

The controller reads `uid` from `SecurityUtils.getUserId()` (same pattern as `/checkout`)
and delegates to `paymentService.handlePayOSReturn(uid, request)`.

---

### Phase 4 — Security Config

**Goal:** Confirm routing is correctly protected.

#### [SecurityConfig.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java)

**No code change required.** `POST /api/v1/payments/payos/return` falls under the
existing `.anyRequest().authenticated()` rule — JWT is automatically enforced. ✅

> [!NOTE]
> Double-check: the new path `/api/v1/payments/payos/return` does **not** accidentally
> match the existing permit-all patterns for `/api/v1/payments/payos/webhook`. They are
> different path segments so no conflict exists.

---

## Verification Plan

### Automated
- `mvn -q -DskipTests compile` — must compile clean.

### Manual (Swagger)
1. Create a checkout → get `orderCode`.
2. On PayOS sandbox, cancel the payment.
3. Call `POST /api/v1/payments/payos/return` with `cancel=true`, `status=CANCELLED`,
   `orderCode=<code>`.
4. Verify the order in DB is now `CANCELLED`.
5. Repeat the call — verify it is idempotent (returns same result, no error).
6. Verify calling with a different user's JWT is rejected (403 / order not found).
