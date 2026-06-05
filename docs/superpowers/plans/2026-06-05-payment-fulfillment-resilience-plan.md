# Payment Fulfillment Resilience Implementation Plan

## Objective

Make paid tier/token activation reliable when the payment gateway confirms that money has been collected, even if the application crashes, callbacks arrive out of order, webhook retries happen, or token/subscription update fails temporarily.

The key rule is:

Payment status `PAID` must not be treated as proof that user entitlement has already been granted.

## Implementation Tracking

Status as of 2026-06-05:

- [x] Added payment fulfillment DB migration `V21__payment_fulfillment_tracking.sql`.
- [x] Added `PaymentFulfillmentStatus` enum.
- [x] Added fulfillment tracking fields to `PaymentOrder`.
- [x] Linked `UserTier` to `PaymentOrder` with `payment_order_id` for idempotency.
- [x] Added repository methods for retry discovery and duplicate fulfillment detection.
- [x] Added `PaymentFulfillmentService` to centralize tier/token granting.
- [x] Updated PayOS webhook flow so duplicate or already-paid webhooks still fulfill unfulfilled orders.
- [x] Updated PayOS return flow so frontend return payload cannot mark an order `PAID`.
- [x] Added `PaymentFulfillmentReconciliationScheduler` with `fixedDelay = 60_000` ms.
- [x] Added admin payment history fulfillment fields: `fulfillmentStatus`, `fulfilledAt`, `fulfillmentAttempts`, `fulfillmentError`.
- [x] Added focused unit tests for payment fulfillment.

Validation:

- `mvn -q -DskipTests compile`: passed.
- `mvn -q -Dtest=PaymentFulfillmentServiceTest test`: passed.
- `mvn -q test`: failed in pre-existing/non-payment test suites because several tests assert old English error messages while source currently returns Vietnamese localized messages. Payment fulfillment tests passed.

Open follow-up:

- Decide whether to update existing legacy tests to assert exception type/behavior instead of exact localized message, or standardize backend error messages.
- If frontend needs fulfillment visibility, document the added admin payment history fields in the FE contract once `/api/v1/payments/history` is represented there.

## Current Risk

Current flow has a real race condition:

1. User pays successfully on PayOS.
2. PayOS redirects browser to frontend.
3. Frontend calls `POST /api/v1/payments/payos/return`.
4. Backend may set `payment_order.status = PAID`.
5. PayOS verified webhook arrives later.
6. `PaymentWebhookService.handlePaid()` sees status already `PAID` and skips.
7. `user_tier` is not created and `user.token` is not reset.

Impact:

- User was charged but did not receive tokens/tier.
- Dashboard may show revenue as paid even though fulfillment failed.
- Manual support intervention would be required.

Kafka is not required for this stage. The correct first fix is idempotent fulfillment backed by database state and a retry/reconciliation job.

## Target Design

Split payment confirmation from fulfillment.

Payment confirmation:

- Gateway has verified that money was collected.
- Stored on `payment_order.status = PAID`.

Fulfillment:

- The system has granted the paid tier and token balance.
- Stored independently on `payment_order.fulfillment_status` and `payment_order.fulfilled_at`.

Recommended states:

- `PENDING`: payment exists but has not been confirmed.
- `PAID`: payment confirmed by verified gateway source.
- `CANCELLED`: user/gateway cancelled.
- `EXPIRED`: payment link expired.
- `FAILED`: gateway failure.

Fulfillment states:

- `PENDING`: not fulfilled yet.
- `PROCESSING`: currently being attempted.
- `FULFILLED`: user tier/token successfully granted.
- `FAILED`: latest attempt failed; retry allowed.

## Phase 1: Database Migration

Add fulfillment tracking columns to `payment_order`:

```sql
ALTER TABLE historical_schema.payment_order
    ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS fulfilled_at TIMESTAMP NULL,
    ADD COLUMN IF NOT EXISTS fulfillment_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fulfillment_error TEXT NULL,
    ADD COLUMN IF NOT EXISTS fulfillment_locked_at TIMESTAMP NULL;
```

Add indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_payment_order_fulfillment_retry
ON historical_schema.payment_order (status, fulfillment_status, updated_at)
WHERE deleted_at IS NULL;
```

Add transaction idempotency at DB level:

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_transaction_reference
ON historical_schema.payment_transaction (reference)
WHERE reference IS NOT NULL;
```

Backfill existing orders:

```sql
UPDATE historical_schema.payment_order
SET fulfillment_status = CASE
    WHEN status = 'PAID' THEN 'FULFILLED'
    ELSE 'PENDING'
END,
fulfilled_at = CASE
    WHEN status = 'PAID' THEN COALESCE(paid_at, updated_at, created_at)
    ELSE NULL
END
WHERE fulfillment_status IS NULL;
```

Important: If production may already contain `PAID` orders that did not grant tokens, do not blindly mark all old `PAID` rows as `FULFILLED`. First run an audit query to compare paid orders with matching active paid `user_tier`.

Audit query:

```sql
SELECT
    po.order_id,
    po.order_code,
    po.uid,
    po.tier_id,
    po.status,
    po.paid_at,
    po.fulfillment_status
FROM historical_schema.payment_order po
LEFT JOIN historical_schema.user_tier ut
  ON ut.uid = po.uid
 AND ut.tier_id = po.tier_id
 AND ut.created_at >= COALESCE(po.paid_at, po.created_at) - INTERVAL '5 minutes'
 AND ut.deleted_at IS NULL
WHERE po.status = 'PAID'
  AND po.deleted_at IS NULL
  AND ut.id IS NULL;
```

## Phase 2: Entity And Enum Updates

Add enum:

```java
public enum PaymentFulfillmentStatus {
    PENDING,
    PROCESSING,
    FULFILLED,
    FAILED
}
```

Update `PaymentOrder`:

- `fulfillmentStatus`
- `fulfilledAt`
- `fulfillmentAttempts`
- `fulfillmentError`
- `fulfillmentLockedAt`

Keep defaults defensive:

- `fulfillmentStatus = PENDING`
- `fulfillmentAttempts = 0`

## Phase 3: Centralize Fulfillment Logic

Create a dedicated service:

```java
PaymentFulfillmentService
```

Responsibilities:

- Grant user tier.
- Reset user token to tier allowance.
- Set `lastTokenResetAt`.
- Mark order `FULFILLED`.
- Be idempotent.
- Be safe under duplicate webhook/retry calls.

Method shape:

```java
@Transactional
public void fulfillPaidOrder(Long orderCode)
```

Expected algorithm:

1. Lock order by `orderCode`.
2. Require `order.status == PAID`.
3. If `fulfillmentStatus == FULFILLED`, return success.
4. Set `fulfillmentStatus = PROCESSING`, increment attempts.
5. Lock current active subscription for user.
6. If matching paid subscription for the same order/tier already exists, mark fulfilled.
7. Otherwise create `UserTier`.
8. Reset `user.token = tier.limitedToken`.
9. Set `user.lastTokenResetAt = now`.
10. Set `fulfilledAt = now`.
11. Set `fulfillmentStatus = FULFILLED`.
12. Clear `fulfillmentError`.

Failure handling:

- Catch expected fulfillment exceptions at the caller boundary.
- Persist `fulfillmentStatus = FAILED`, increment attempts, store error.
- Let reconciliation retry.

Important: avoid duplicate paid subscriptions. Current code checks if user already has any active paid tier and returns. That is too broad for recovery because it can hide partial or duplicate-order issues. The new logic should distinguish:

- Same order/tier already fulfilled: OK, mark fulfilled.
- Different active paid subscription exists: mark fulfillment failed with clear reason, requires support decision.

## Phase 4: Fix Verified Webhook Flow

Update `PaymentWebhookService.handlePaid()`:

Current risky behavior:

```java
if (PaymentOrderStatus.PAID == order.getStatus()) {
    return;
}
```

Target behavior:

- If order is `PAID` but not fulfilled, continue fulfillment.
- Duplicate webhook should be safe because fulfillment is idempotent.

Target algorithm:

1. Lock order.
2. Validate amount/paymentLinkId.
3. Save transaction idempotently.
4. If order status is not `PAID`, set `PAID` and `paidAt`.
5. Call fulfillment service if `fulfillmentStatus != FULFILLED`.
6. Return `200 OK` only when the webhook was accepted and DB work completed or safely skipped.

Webhook duplicate cases:

- Same reference arrives again: transaction insert skipped.
- Order already `PAID` and `FULFILLED`: return OK.
- Order already `PAID` but `FAILED/PENDING` fulfillment: retry fulfillment.

## Phase 5: Fix Return URL Flow

Current `handlePayOSReturn()` can mark order as `PAID` based on frontend-provided params. That is unsafe.

Change behavior:

- Do not set `PAID` from frontend return payload alone.
- Return URL endpoint is UI synchronization only.
- For `status=PAID`, respond with `PENDING_VERIFICATION` unless backend verifies with PayOS API.

Recommended minimum:

```text
Frontend return status PAID -> backend keeps order PENDING and returns:
"Payment is being verified. Please wait for confirmation."
```

Better option:

- Backend calls PayOS get-payment-link/order-status API using `orderCode` or `paymentLinkId`.
- If PayOS confirms paid, call same internal verified path:
  - `confirmPaidAndFulfill(orderCode, verifiedPayOSData)`
- If PayOS API cannot confirm, keep `PENDING`.

Do not allow frontend request body to become the authority for paid state.

## Phase 6: Reconciliation Scheduler

Add scheduled job:

```java
PaymentFulfillmentReconciliationScheduler
```

Runs every 1-5 minutes.

Find orders:

```sql
status = 'PAID'
AND fulfillment_status IN ('PENDING', 'FAILED')
AND deleted_at IS NULL
AND fulfillment_attempts < max_attempts
```

For each order:

- Call `PaymentFulfillmentService.fulfillPaidOrder(orderCode)`.
- Log success/failure.
- Keep retrying transient failures.

Add stale processing recovery:

```sql
fulfillment_status = 'PROCESSING'
AND fulfillment_locked_at < now() - interval '10 minutes'
```

This handles app crash after setting `PROCESSING`.

## Phase 7: Admin Visibility

Update payment admin response with:

- `fulfillmentStatus`
- `fulfilledAt`
- `fulfillmentAttempts`
- `fulfillmentError`

Add optional admin filter later:

- `fulfillmentStatus`

This lets support detect:

- Paid but not fulfilled.
- Fulfillment failed after retries.
- Orders needing manual review.

## Phase 8: Tests

Add focused service tests.

Webhook tests:

1. `PENDING -> PAID -> FULFILLED`
   - Creates transaction.
   - Creates `UserTier`.
   - Resets `User.token`.
   - Sets `fulfilledAt`.

2. Duplicate webhook after fulfilled:
   - Does not create duplicate `UserTier`.
   - Does not double-credit token.
   - Returns safely.

3. Order already `PAID` but `fulfillmentStatus=PENDING`:
   - Webhook still fulfills.

4. Invalid amount:
   - Rejects webhook.
   - Does not fulfill.

5. Existing transaction reference:
   - Does not fail due duplicate transaction.
   - Still fulfills if needed.

Return URL tests:

1. `status=PAID` from frontend does not mark order `PAID` without backend verification.
2. Cancel return marks `CANCELLED` only if order not already `PAID/FULFILLED`.
3. Return URL cannot update another user's order.

Reconciliation tests:

1. `PAID + PENDING fulfillment` gets fulfilled.
2. `PAID + FAILED fulfillment` gets retried.
3. `PROCESSING` older than lock timeout gets retried.
4. Max attempts prevents infinite tight-loop retry.

DB migration tests/manual validation:

- Migration applies cleanly on existing DB.
- Unique reference index does not fail on existing duplicate references; if duplicates exist, clean first.

## Phase 9: Rollout

Pre-deploy checks:

1. Audit existing `PAID` orders without matching active paid `user_tier`.
2. Check duplicate `payment_transaction.reference`.
3. Decide backfill policy for historical paid orders.

Deploy order:

1. Deploy DB migration.
2. Deploy backend with fulfillment fields/entities.
3. Deploy webhook/return logic fix.
4. Enable reconciliation scheduler.
5. Monitor logs for fulfillment failures.

Post-deploy validation:

1. Create test checkout.
2. Complete PayOS sandbox payment.
3. Confirm:
   - `payment_order.status = PAID`
   - `payment_order.fulfillment_status = FULFILLED`
   - `payment_order.fulfilled_at IS NOT NULL`
   - `user_tier` row exists
   - `user.token = tier.limited_token`
   - `payment_transaction` row exists
4. Replay webhook payload if possible and confirm no duplicate tier/token grant.

## Decision On Kafka

Do not introduce Kafka now.

Use Kafka only if later requirements include:

- Multiple independent services consuming payment events.
- High payment volume.
- Analytics/event stream beyond database reporting.
- Need for cross-service async integration.

For the current monolith-style Java backend, database-backed idempotent fulfillment plus reconciliation is simpler, safer, and easier to operate.

## Acceptance Criteria

- A verified paid webhook always results in either `FULFILLED` or a visible retryable `FAILED` state.
- `PAID` orders cannot be silently left without tier/token after callback order race.
- Frontend return URL cannot fake paid state.
- Duplicate webhooks do not double-credit tokens.
- Reconciliation can repair `PAID` but unfulfilled orders.
- Admin/payment history can show fulfillment status for support.
