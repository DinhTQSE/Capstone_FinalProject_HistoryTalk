# Payment System — Implementation Tasks

## Phase 1: Critical Bug Fixes (Enum + Repositories)
- [ ] `PaymentTransactionStatus.java` — add `PENDING` value (compile error fix)
- [ ] `PaymentOrderRepository.java` — fix typo `createAt` → `createdAt`; fix `OffsetDateTime` → `LocalDateTime`
- [ ] `UserTierRepository.java` — remove invalid `LIMIT 1` from JPQL; use `Pageable`

## Phase 2: Database Migration
- [ ] `V11__payment_order_unique_order_code.sql` — add UNIQUE constraint on `order_code`

## Phase 3: Webhook Service Improvements
- [ ] `PaymentWebhookService.java` — add dispatcher `handlePayOSWebhook`, handle CANCELLED/EXPIRED, fix token update

## Phase 4: Expiry Scheduler
- [ ] `PaymentExpiryScheduler.java` — new scheduler component
- [ ] `HistoryTalkApplication.java` — add `@EnableScheduling`

## Phase 5: Service & Controller Polish + Tiers Endpoint
- [ ] `PaymentService.java` — add `getPaymentHistory`, fix exceptions, add `listTiers`
- [ ] `PaymentController.java` — fix SecurityUtils pattern, ApiResponse, add history + tiers endpoints
- [ ] `PayOSWebhookController.java` — call new dispatcher method

## Phase 6: SecurityConfig
- [ ] `SecurityConfig.java` — add permitAll for webhook + tiers
