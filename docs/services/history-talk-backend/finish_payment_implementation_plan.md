# Payment System — Bug Fixes & Feature Completions

## Background

The payment module integrates with PayOS to allow users to purchase tier subscriptions. Several bugs and missing features were identified in the current code. This plan tracks all required fixes before the payment flow can be considered production-ready.

> [!IMPORTANT]
> **Scope boundary — no tier-based feature gating.** This plan only fixes the payment infrastructure (checkout, webhook, expiry, DB records, token update). It does **NOT** introduce any logic that blocks users from using features based on their tier (e.g. "free users cannot do X"). All existing features remain fully accessible regardless of what tier a user holds. Tier data is stored, but nothing enforces it yet.

---

## Issues Being Fixed

| # | Problem | Severity |
|---|---------|----------|
| 1 | `findByUser_UidOrderByCreateAtDesc` — typo in field name (`createAt` vs `createdAt`) causes runtime error | 🔴 Critical |
| 2 | `findExpiredPendingOrders` — parameter is `OffsetDateTime` but `PaymentOrder.expiredAt` is `LocalDateTime`; type mismatch | 🔴 Critical |
| 3 | `UserTierRepository` uses `LIMIT 1` in JPQL — invalid in standard Spring Data JPA / Hibernate 6 | 🔴 Critical |
| 4 | No scheduler to expire PENDING orders when `expiredAt` is reached | 🔴 Critical |
| 5 | Webhook handler only handles PAID; cancelled/failed/return-URL callbacks are not handled | 🟠 High |
| 6 | User `token` allowance is not updated after tier upgrade (`Tier.limitedToken → User.token`) | 🟠 High |
| 7 | `order_code` is only indexed but not `UNIQUE` in migration — duplicate order codes are possible | 🟠 High |
| 8 | Webhook signature is handled by `payOS.webhooks().verify()` in the controller but the comment says "not implemented"; this is actually OK — verify this works correctly | 🟡 Medium |
| 9 | `PaymentController` references `CustomUserDetails` without an import — needs SecurityUtils pattern | 🟡 Medium |
| 10 | Webhook controller path `/api/payments/payos/webhook` is not allowed in SecurityConfig | 🟡 Medium |

---

## Implementation Phases

| Phase | Area | Files Touched | Priority |
|---|---|---|---|
| **1** | Critical bug fixes — enum + repositories | `PaymentTransactionStatus`, `PaymentOrderRepository`, `UserTierRepository` | 🔴 First — fixes compile errors |
| **2** | Database migration | `V11__payment_order_unique_order_code.sql` [NEW] | 🔴 Before app start |
| **3** | Webhook service improvements | `PaymentWebhookService` | 🟠 Core business logic |
| **4** | Expiry scheduler | `PaymentExpiryScheduler` [NEW], `HistoryTalkApplication` | 🟡 New component |
| **5** | Service & controller polish + tiers endpoint | `PaymentService`, `PaymentController`, `PayOSWebhookController` | 🟡 API layer |
| **6** | SecurityConfig permit rules | `SecurityConfig` | 🟡 Auth rules last |

---

## Proposed Changes

### 1 — Repository: `PaymentOrderRepository`

#### [MODIFY] [PaymentOrderRepository.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/payment/PaymentOrderRepository.java)

- **Fix typo**: rename `findByUser_UidOrderByCreateAtDesc` → `findByUser_UidOrderByCreatedAtDesc`
- **Fix type mismatch**: change `@Param("now") OffsetDateTime now` → `LocalDateTime now` in `findExpiredPendingOrders`

---

### 2 — Repository: `UserTierRepository`

#### [MODIFY] [UserTierRepository.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/payment/UserTierRepository.java)

- **Fix JPQL LIMIT**: replace `LIMIT 1` with Spring Data's `findFirst` naming convention or use `Pageable.ofSize(1)` parameter.
  - **Preferred solution**: rename to `findFirstByUser_UidAndIsActiveTrueAndDeletedAtIsNullOrderByEndTimeDesc` and use `@Lock` via a custom `@Query` without `LIMIT`.

  Actually, the cleanest approach is a plain JPQL query without LIMIT and use Spring Data's `findFirst*` derived query:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT ut FROM UserTier ut WHERE ut.user.uid = :uid AND ut.isActive = true AND ut.deletedAt IS NULL ORDER BY ut.endTime DESC")
  List<UserTier> findActiveByUidForUpdateList(@Param("uid") UUID uid, Pageable pageable);
  ```
  Then call it with `PageRequest.of(0, 1)`. Service picks `get(0)` if not empty.

---

### 3 — Scheduler: `PaymentExpiryScheduler` [NEW]

#### [NEW] `PaymentExpiryScheduler.java` in `com.historytalk.service.payment`

A Spring `@Scheduled` component that runs every minute (configurable) to:
1. Query all PENDING orders where `expiredAt <= now`.
2. For each order, set status to `EXPIRED` and save.
3. Optionally call PayOS API to cancel the payment link (nice-to-have; flag-guarded).

```
@Scheduled(fixedDelayString = "${payment.expiry-check-delay-ms:60000}")
public void expirePendingOrders() { ... }
```

Requires `@EnableScheduling` on a config class or the main application class.

---

### 4 — Service: `PaymentWebhookService`

#### [MODIFY] [PaymentWebhookService.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentWebhookService.java)

**a) Route all webhook event codes** — PayOS sends a `code` field with each webhook:
- `"00"` → PAID
- `"01"` → CANCELLED (user cancelled)
- `"02"` → CANCELLED (payment link expired on PayOS side)
- Any other non-00 → treat as FAILED / no-op

Add a dispatcher method `handlePayOSWebhook(WebhookData data)` that routes by `data.getCode()`.

**b) Handle CANCELLED webhook**:
```
handlePayOSCancelledWebhook(WebhookData data)
  → lock order by orderCode
  → if already PAID, return (do not cancel)
  → set status = CANCELLED
  → save transaction with status = FAILED
  → save order
```

**c) Handle EXPIRED webhook** (PayOS may also fire one):
```
handlePayOSExpiredWebhook(WebhookData data)
  → same as cancelled but set status = EXPIRED
```

**d) Fix `upgradeUserTier`** — uncomment and activate `user.setToken(newTier.getLimitedToken())` so that `User.token` is replenished when a tier upgrade succeeds.

---

### 5 — Controller: `PayOSWebhookController`

#### [MODIFY] [PayOSWebhookController.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PayOSWebhookController.java)

- Change the webhook call from `paymentWebhookService.handlePayOSPaidWebhook(data)` to the new `handlePayOSWebhook(data)` dispatcher.
- The `payOS.webhooks().verify(webhook)` call already validates the HMAC signature using `checksumKey`, so no additional signature layer is needed — confirm this is correct and add a comment.

---

### 6 — Controller: `PaymentController`

#### [MODIFY] [PaymentController.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java)

- Replace `@AuthenticationPrincipal CustomUserDetails` with `SecurityUtils.getUserId()` to match the project's security pattern (per copilot-instructions: _do not add X-Staff-Id headers; use SecurityUtils_).
- Wrap response in `ApiResponse.success(...)` per project convention.
- Add a `GET /history` endpoint that calls `paymentService.getPaymentHistory(uid)`.

---

### 7 — Service: `PaymentService`

#### [MODIFY] [PaymentService.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java)

- Add `getPaymentHistory(UUID uid)` method that calls the fixed `findByUser_UidOrderByCreatedAtDesc` and maps results to `PaymentHistoryResponse`.
- Replace bare `RuntimeException` throws with project `BaseException` subclasses (`ResourceNotFoundException`, `InvalidRequestException`).

---

### 8 — Security: `SecurityConfig`

#### [MODIFY] [SecurityConfig.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/SecurityConfig.java)

The existing `SecurityConfig` already ends with `.anyRequest().authenticated()`, which means **all payment endpoints are protected by default** — no additional rules are needed for checkout or history.

Explicit rules to add:
- `POST /api/payments/payos/webhook` → **`permitAll()`** — PayOS's servers call this with no JWT token; without this, all webhook deliveries are rejected with 401.
- `GET /api/payments/tiers` → **`permitAll()`** — read-only metadata (name, price, token limit). Safe to expose publicly; it does **not** check or enforce any user's tier on other APIs.

All other payment endpoints (`POST /api/payments/checkout`, `GET /api/payments/history`) inherit the catch-all `.authenticated()` rule — callers must send a valid `Authorization: Bearer <token>` header.

---

### 9 — Migration: `V11__payment_order_unique_order_code.sql` [NEW]

#### [NEW] `V11__payment_order_unique_order_code.sql`

```sql
-- V11: Add UNIQUE constraint on payment_order.order_code
ALTER TABLE historical_schema.payment_order
    ADD CONSTRAINT uq_payment_order_code UNIQUE (order_code);
```

> [!WARNING]
> If there are duplicate `order_code` values in the DB from earlier testing, the migration will fail. Drop or fix them first.

---

### 10 — Application: Enable `@EnableScheduling`

#### [MODIFY] `HistoryTalkApplication.java` or a new `SchedulingConfig.java`

Add `@EnableScheduling` to activate `@Scheduled` for the expiry scheduler.

---

## Open Questions

> [!IMPORTANT]
> **Return URL & Cancel URL handling** — PayOS redirects the user's browser to `returnUrl` / `cancelUrl` after payment. These are frontend URLs and do **not** need a backend handler. The assumption is the frontend reads the query params (`?orderCode=...&status=...`) and shows a result page. The actual status update happens via the **webhook**, not the return URL. If a backend return-URL endpoint is ever needed, it will be added separately.

## Resolved Decisions

| Question | Decision |
|---|---|
| `User.token` reset vs. top-up | **Replace** — set to `newTier.getLimitedToken()` on tier upgrade |
| Scheduler interval | **60 seconds** fixed delay |

---

## Verification Plan

### After Implementation
1. Start the application — confirm no startup errors (`mvn spring-boot:run`).
2. Create a checkout → verify PENDING order saved in DB with correct `expiredAt`.
3. Wait for expiry window or manually set `expiredAt` to past → confirm scheduler sets status to EXPIRED.
4. Simulate PayOS PAID webhook → verify order status = PAID, `UserTier` record created, `User.tier_id` and `User.token` updated.
5. Simulate PayOS CANCELLED webhook → verify order status = CANCELLED.
6. Confirm `payOS.webhooks().verify()` rejects webhooks with bad signatures.
7. Confirm `GET /api/payments/history` returns paginated order list for authenticated user.
