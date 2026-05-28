# Payment System - Implementation Status

Last verified: 2026-05-28

This document records the current source status of the PayOS payment module. The original task checklist has mostly been implemented in source; one runtime activation item remains.

## Phase 1: Critical Bug Fixes

- [x] `PaymentTransactionStatus.java` includes the transaction states required by webhook handling.
- [x] `PaymentOrderRepository.java` uses `createdAt` and `LocalDateTime` query signatures.
- [x] `UserTierRepository.java` uses repository query methods without invalid JPQL `LIMIT`.

## Phase 2: Database Migration

- [x] `V11__payment_order_unique_order_code.sql` adds uniqueness for `order_code`.

## Phase 3: Webhook Service Improvements

- [x] `PaymentWebhookService.java` exposes `handlePayOSWebhook`.
- [x] PayOS code `00` marks orders as `PAID`.
- [x] PayOS code `01` marks orders as `CANCELLED`.
- [x] PayOS code `02` marks orders as `EXPIRED`.
- [x] Paid webhook upgrades the user's tier and replaces token balance with the tier allowance.

## Phase 4: Expiry Scheduler

- [x] `PaymentExpiryScheduler.java` exists and marks expired pending orders as `EXPIRED`.
- [ ] Scheduling activation remains incomplete: `HistoryTalkApplication.java` has `@EnableAsync` but does not currently have `@EnableScheduling`.

## Phase 5: Service And Controller

- [x] `PaymentService.java` supports checkout creation.
- [x] `PaymentService.java` supports payment history.
- [x] `PaymentService.java` supports active tier listing.
- [x] `PaymentController.java` exposes checkout, history, and tiers endpoints.
- [x] `PayOSWebhookController.java` delegates verified webhook data to `PaymentWebhookService.handlePayOSWebhook`.

## Phase 6: Security

- [x] `SecurityConfig.java` allows public access to payment tiers and PayOS webhook endpoints.

## Validation

Verified from `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`:

```powershell
mvn -q -DskipTests compile
mvn -q test
```

Both commands exited with code 0 on 2026-05-28.
