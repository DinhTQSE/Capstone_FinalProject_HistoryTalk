# Port PayOS Payment Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the PayOS payment, tier subscription, and webhook-confirmation module from HistoryTalk Java backend into another Spring Boot source codebase.

**Architecture:** Keep PayOS checkout creation, browser return handling, and webhook confirmation separated. The browser return endpoint may sync UI state, but only the verified PayOS webhook may activate a paid tier and reset user tokens. Store payment orders, webhook transactions, tiers, and user tier subscriptions in database tables with idempotency guards.

**Tech Stack:** Spring Boot 3.x, Java 21, Maven, Spring Web, Spring Security, Spring Data JPA, Flyway, Lombok, Jackson, PayOS Java SDK `vn.payos:payos-java:2.0.1`, PostgreSQL-compatible SQL.

---

## Source Inventory

Read these source files from the original project before implementation:

- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/config/PayOSConfig.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PayOSWebhookController.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentWebhookService.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentExpiryScheduler.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/Tier.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/UserTier.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/PaymentOrder.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/PaymentTransaction.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/enums/PaymentOrderStatus.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/entity/enums/PaymentTransactionStatus.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/payment/*.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/dto/payment/*.java`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V10__add_payment_and_tier_tables.sql`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V11__payment_order_unique_order_code.sql`
- `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/resources/db/migration/V18__fix_user_tier_relationship.sql`

External PayOS references used for this plan:

- [PayOS overview and payment flow](https://payos.vn/docs/)
- [PayOS webhook payload and 2xx response rule](https://payos.vn/docs/du-lieu-tra-ve/webhook/)
- [PayOS return URL query params](https://payos.vn/docs/du-lieu-tra-ve/return-url/)
- [PayOS signature/checksum verification](https://payos.vn/docs/tich-hop-webhook/kiem-tra-du-lieu-voi-signature/)
- [PayOS test environment note: no separate sandbox](https://payos.vn/docs/moi-truong-test/)

## Core Behavioral Contract

The target implementation must preserve these rules:

- `GET /api/v1/payments/tiers` is public.
- `POST /api/v1/payments/checkout` requires an authenticated customer and creates a PayOS payment link.
- `POST /api/v1/payments/payos/return` requires authentication, verifies order ownership, and only syncs local order status for UI.
- `GET /api/v1/payments/payos/webhook` is public and returns `200 OK` for PayOS webhook registration checks.
- `POST /api/v1/payments/payos/webhook` is public, verifies PayOS signature with checksum key through the SDK, then updates payment and subscription state.
- Never activate a tier from browser return params. Only a verified webhook may activate a paid subscription.
- A user cannot buy a new paid subscription while an active paid subscription exists.
- Duplicate PayOS webhooks must be idempotent.
- Webhook PAID must validate amount and payment link id before upgrading the user.
- PayOS has no separate sandbox according to current PayOS docs. Test with small real payments on production PayOS credentials.

## Target Files

Use these paths if the target is a standard Spring Boot application. The original package root is `com.historytalk`; if the target package differs, perform one global package rename after copying.

- Create: `src/main/java/com/historytalk/config/PayOSConfig.java`
- Create: `src/main/java/com/historytalk/controller/payment/PaymentController.java`
- Create: `src/main/java/com/historytalk/controller/payment/PayOSWebhookController.java`
- Create: `src/main/java/com/historytalk/service/payment/PaymentService.java`
- Create: `src/main/java/com/historytalk/service/payment/PaymentWebhookService.java`
- Create: `src/main/java/com/historytalk/service/payment/PaymentExpiryScheduler.java`
- Create: `src/main/java/com/historytalk/entity/payment/Tier.java`
- Create: `src/main/java/com/historytalk/entity/payment/UserTier.java`
- Create: `src/main/java/com/historytalk/entity/payment/PaymentOrder.java`
- Create: `src/main/java/com/historytalk/entity/payment/PaymentTransaction.java`
- Create: `src/main/java/com/historytalk/entity/enums/PaymentOrderStatus.java`
- Create: `src/main/java/com/historytalk/entity/enums/PaymentTransactionStatus.java`
- Create: `src/main/java/com/historytalk/repository/payment/TierRepository.java`
- Create: `src/main/java/com/historytalk/repository/payment/UserTierRepository.java`
- Create: `src/main/java/com/historytalk/repository/payment/PaymentOrderRepository.java`
- Create: `src/main/java/com/historytalk/repository/payment/PaymentTransactionRepository.java`
- Create: `src/main/java/com/historytalk/dto/payment/CreatePaymentRequest.java`
- Create: `src/main/java/com/historytalk/dto/payment/CreatePaymentResponse.java`
- Create: `src/main/java/com/historytalk/dto/payment/PayOSReturnRequest.java`
- Create: `src/main/java/com/historytalk/dto/payment/PayOSReturnResponse.java`
- Create: `src/main/java/com/historytalk/dto/payment/TierResponse.java`
- Create: `src/main/java/com/historytalk/dto/payment/PaymentHistoryResponse.java`
- Create: `src/main/java/com/historytalk/dto/payment/AdminPaymentHistoryResponse.java`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties` or `application.yml`
- Modify: target security config to allow public PayOS webhook and tier listing.
- Modify: target user registration flow to create a free `UserTier` row.
- Modify: target user profile/token flow if the destination uses token quotas.
- Create: `src/main/resources/db/migration/Vxx__add_payos_payment_module.sql`
- Test: `src/test/java/com/historytalk/service/payment/PaymentServiceTest.java`
- Test: `src/test/java/com/historytalk/service/payment/PaymentWebhookServiceTest.java`
- Test: `src/test/java/com/historytalk/controller/payment/PayOSWebhookControllerTest.java`

## Database Contract

The source module uses PostgreSQL schema `historical_schema`. In the target, replace schema qualification with the target convention. If the target does not use schemas, remove `historical_schema.` from SQL and entity `schema` attributes.

Required tables:

- `tier`
  - `tier_id UUID PK`
  - `title VARCHAR(50) NOT NULL`
  - `amount INT NOT NULL`
  - `no_month INT NOT NULL`
  - `limited_token INT NOT NULL`
  - `is_active BOOLEAN NOT NULL DEFAULT TRUE`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP`
  - `deleted_at TIMESTAMP`
- `user_tier`
  - `id UUID PK`
  - `uid UUID NOT NULL` references the target user table
  - `tier_id UUID NOT NULL` references `tier`
  - `start_time TIMESTAMP NOT NULL`
  - `end_time TIMESTAMP NOT NULL`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `is_active BOOLEAN NOT NULL DEFAULT TRUE`
  - `deleted_at TIMESTAMP`
- `payment_order`
  - `order_id UUID PK`
  - `uid UUID NOT NULL` references the target user table
  - `tier_id UUID NOT NULL` references `tier`
  - `order_code BIGINT NOT NULL UNIQUE`
  - `amount INT NOT NULL`
  - `payment_link_id VARCHAR(255)`
  - `checkout_url VARCHAR(500)`
  - `qr_code TEXT`
  - `status VARCHAR(50) NOT NULL DEFAULT 'PENDING'`
  - `paid_at TIMESTAMP`
  - `expired_at TIMESTAMP`
  - `description TEXT`
  - `is_active BOOLEAN NOT NULL DEFAULT TRUE`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP`
  - `deleted_at TIMESTAMP`
- `payment_transaction`
  - `transaction_id UUID PK`
  - `order_id UUID NOT NULL` references `payment_order`
  - `amount INT NOT NULL`
  - `payment_link_id VARCHAR(255)`
  - `payload TEXT`
  - `status VARCHAR(50) NOT NULL DEFAULT 'PENDING'`
  - `transaction_date TIMESTAMP`
  - `reference VARCHAR(255)`
  - `is_active BOOLEAN NOT NULL DEFAULT TRUE`
  - `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `updated_at TIMESTAMP`
  - `deleted_at TIMESTAMP`

Seed these tiers unless the target product has different pricing:

```sql
INSERT INTO tier (tier_id, title, amount, no_month, limited_token, is_active, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'free', 0, 1, 20, TRUE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002', 'plus', 49000, 1, 100, TRUE, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003', 'pro', 99000, 1, 999, TRUE, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;
```

Required indexes:

```sql
CREATE INDEX IF NOT EXISTS idx_tier_is_active ON tier (is_active, deleted_at);
CREATE INDEX IF NOT EXISTS idx_user_tier_uid ON user_tier (uid, is_active);
CREATE INDEX IF NOT EXISTS idx_user_tier_tier_id ON user_tier (tier_id);
CREATE INDEX IF NOT EXISTS idx_payment_order_uid ON payment_order (uid, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_order_code ON payment_order (order_code);
CREATE INDEX IF NOT EXISTS idx_payment_order_status ON payment_order (status);
CREATE INDEX IF NOT EXISTS idx_payment_tx_order ON payment_transaction (order_id, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_tx_reference ON payment_transaction (reference);
```

## Environment And PayOS Setup

Add these environment variables to the target service:

```env
PAYOS_CLIENT_ID=payos_client_id_from_dashboard
PAYOS_API_KEY=payos_api_key_from_dashboard
PAYOS_CHECKSUM_KEY=payos_checksum_key_from_dashboard
PAYOS_RETURN_URL=https://app.example.com/payment/payos/return
PAYOS_CANCEL_URL=https://app.example.com/payment/payos/cancel
```

Important setup details:

- `PAYOS_RETURN_URL` and `PAYOS_CANCEL_URL` are browser redirect URLs. They should normally be frontend pages that read query params and then POST them to backend `POST /api/v1/payments/payos/return`.
- Register the webhook URL in `https://my.payos.vn`, for example `https://api.example.com/Historical-tell/api/v1/payments/payos/webhook` when the backend uses context path `/Historical-tell`, or `https://api.example.com/api/v1/payments/payos/webhook` when it does not.
- For local webhook testing, expose the backend with ngrok:

```powershell
ngrok http 8080
```

Use the generated HTTPS forwarding URL, for example:

```env
PAYOS_RETURN_URL=https://localhost-or-frontend-domain/payment/payos/return
PAYOS_CANCEL_URL=https://localhost-or-frontend-domain/payment/payos/cancel
PAYOS_WEBHOOK_URL_FOR_PAYOS_DASHBOARD=https://abc123.ngrok-free.app/api/v1/payments/payos/webhook
```

If the backend has servlet context path `/Historical-tell`, register:

```text
https://abc123.ngrok-free.app/Historical-tell/api/v1/payments/payos/webhook
```

Ngrok awareness:

- The free ngrok domain changes after restart unless a reserved domain is configured.
- After ngrok URL changes, update the PayOS webhook URL in the PayOS dashboard.
- PayOS must reach the webhook over public HTTPS. `localhost` will not work for webhooks.
- The webhook endpoint must be public, but the request body must be signature-verified with `PAYOS_CHECKSUM_KEY`.

## Task 1: Add Maven Dependency And Config

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Create: `src/main/java/com/historytalk/config/PayOSConfig.java`

- [ ] **Step 1: Add PayOS dependency**

Add to `pom.xml`:

```xml
<dependency>
    <groupId>vn.payos</groupId>
    <artifactId>payos-java</artifactId>
    <version>2.0.1</version>
</dependency>
```

Required supporting dependencies if the target does not already have them:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

- [ ] **Step 2: Add PayOS properties**

Add to `application.properties`:

```properties
payos.client-id=${PAYOS_CLIENT_ID}
payos.api-key=${PAYOS_API_KEY}
payos.checksum-key=${PAYOS_CHECKSUM_KEY}
payos.return-url=${PAYOS_RETURN_URL}
payos.cancel-url=${PAYOS_CANCEL_URL}
```

- [ ] **Step 3: Create PayOSConfig**

Create:

```java
package com.historytalk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "payos")
public class PayOSConfig {
    private String clientId;
    private String apiKey;
    private String checksumKey;
    private String returnUrl;
    private String cancelUrl;

    @Bean
    public PayOS payOS() {
        return new PayOS(clientId, apiKey, checksumKey);
    }
}
```

- [ ] **Step 4: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile succeeds. If the target package is not `com.historytalk`, first rename `package com.historytalk...` and imports consistently to the target package root.

## Task 2: Add Payment Schema

**Files:**
- Create: `src/main/resources/db/migration/Vxx__add_payos_payment_module.sql`

- [ ] **Step 1: Create migration**

Use the database contract above. For PostgreSQL with schema support, use concrete qualified names such as `CREATE TABLE IF NOT EXISTS historical_schema.tier`. For a no-schema target, use unqualified table names such as `CREATE TABLE IF NOT EXISTS tier`.

If the target does not already enable UUID generation, add one of these before creating tables:

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

or:

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

- [ ] **Step 2: Adapt user FK**

Map `uid UUID NOT NULL` to the target user table primary key. The original project uses `historical_schema."user"(uid)`. The target might use `users(id)`, `app_user(user_id)`, or another name. Keep the Java entity field named `user` and join column mapped to the actual target FK column.

- [ ] **Step 3: Run migration**

Run:

```bash
mvn -q -DskipTests flyway:migrate
```

Expected: migration succeeds and the four payment tables exist.

## Task 3: Add Entities And Enums

**Files:**
- Create: `entity/enums/PaymentOrderStatus.java`
- Create: `entity/enums/PaymentTransactionStatus.java`
- Create: `entity/payment/Tier.java`
- Create: `entity/payment/UserTier.java`
- Create: `entity/payment/PaymentOrder.java`
- Create: `entity/payment/PaymentTransaction.java`
- Modify: target `User` entity if it lacks `token` and `lastTokenResetAt`

- [ ] **Step 1: Create enums**

```java
public enum PaymentOrderStatus {
    PENDING,
    PAID,
    CANCELLED,
    EXPIRED,
    FAILED
}
```

```java
public enum PaymentTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
```

- [ ] **Step 2: Port entities**

Copy entity field shapes from the source inventory. Replace package names and schema names. Keep these relationships:

- `PaymentOrder` has `ManyToOne User user`
- `PaymentOrder` has `ManyToOne Tier tier`
- `PaymentTransaction` has `ManyToOne PaymentOrder paymentOrder`
- `UserTier` has `ManyToOne User user`
- `UserTier` has `ManyToOne Tier tier`

- [ ] **Step 3: Add token fields if needed**

If the target app has token quota behavior, target `User` needs:

```java
@Column(name = "token", nullable = false)
private Integer token = 0;

@Column(name = "last_token_reset_at")
private LocalDateTime lastTokenResetAt;
```

If the target app does not use tokens, remove token reset from `PaymentWebhookService.upgradeUserTier()` and remove `limited_token` from user-facing behavior while keeping it in `tier` for future compatibility.

- [ ] **Step 4: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: entity mapping compiles.

## Task 4: Add Repositories

**Files:**
- Create: `repository/payment/TierRepository.java`
- Create: `repository/payment/UserTierRepository.java`
- Create: `repository/payment/PaymentOrderRepository.java`
- Create: `repository/payment/PaymentTransactionRepository.java`

- [ ] **Step 1: Port repository methods**

Required methods:

```java
Optional<PaymentOrder> findByOrderCode(Long orderCode);
List<PaymentOrder> findByUser_UidOrderByCreatedAtDesc(UUID uid);
boolean existsByReference(String reference);
List<Tier> findByIsActiveTrueAndDeletedAtIsNull();
Optional<Tier> findByTitleIgnoreCaseAndIsActiveTrueAndDeletedAtIsNull(String title);
```

Required locking queries:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM PaymentOrder o WHERE o.orderCode = :orderCode")
Optional<PaymentOrder> findByOrderCodeForUpdate(@Param("orderCode") Long orderCode);
```

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT ut FROM UserTier ut
    JOIN FETCH ut.tier t
    WHERE ut.user.uid = :uid
      AND ut.isActive = true
      AND ut.deletedAt IS NULL
      AND ut.endTime > :now
    ORDER BY t.amount DESC
""")
List<UserTier> findCurrentActiveByUidForUpdateRows(@Param("uid") UUID uid, @Param("now") LocalDateTime now);
```

If the target JPA provider rejects `LIMIT 1` inside JPQL, return `List<UserTier>` ordered by amount descending and select the first row in the service.

- [ ] **Step 2: Add expired order query**

```java
@Query("""
    SELECT o
    FROM PaymentOrder o
    WHERE o.status = :status
      AND o.expiredAt <= :now
      AND o.deletedAt IS NULL
""")
List<PaymentOrder> findExpiredPendingOrders(
        @Param("status") PaymentOrderStatus status,
        @Param("now") LocalDateTime now
);
```

- [ ] **Step 3: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: repository query validation passes at context startup. If compile succeeds but startup fails on JPQL, replace `LIMIT 1` with `PageRequest.of(0, 1)` or list-first logic.

## Task 5: Add DTOs

**Files:**
- Create: payment DTOs listed in Target Files.

- [ ] **Step 1: Create request DTOs**

Fields:

```text
CreatePaymentRequest: tierId String
PayOSReturnRequest: code String, id String, cancel Boolean, status String, orderCode Long
```

- [ ] **Step 2: Create response DTOs**

Fields:

```text
CreatePaymentResponse: orderId String, orderCode Long, paymentLinkId String, checkoutUrl String, qrCode String, amount Integer, status String, expiredAt String
PayOSReturnResponse: orderCode Long, resolvedStatus String, message String
TierResponse: tierId String, title String, amount Integer, noMonth Integer, limitedToken Integer, isActive Boolean
PaymentHistoryResponse: orderId String, orderCode Long, tierId String, tierTitle String, amount Integer, status String, paymentLinkId String, createdAt String, paidAt String, expiredAt String
AdminPaymentHistoryResponse: same as PaymentHistoryResponse plus userId String, userName String, userEmail String
```

- [ ] **Step 3: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: DTOs compile.

## Task 6: Add PaymentService

**Files:**
- Create: `service/payment/PaymentService.java`

- [ ] **Step 1: Implement checkout creation**

Required algorithm:

1. Load authenticated user by UUID.
2. Load tier by `tierId`.
3. Reject inactive/deleted tier.
4. Reject free tier with amount `<= 0`.
5. Reject checkout if user already has active paid subscription.
6. Create `PaymentOrder` with status `PENDING`, 15 minute expiry, generated `orderCode`, short description `HISTALK` plus last 6 digits.
7. Call PayOS SDK `payOS.paymentRequests().create(CreatePaymentLinkRequest)`.
8. Persist `paymentLinkId`, `checkoutUrl`, and `qrCode`.
9. Return `CreatePaymentResponse`.

Use this PayOS request shape:

```java
CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
        .orderCode(orderCode)
        .amount(tier.getAmount().longValue())
        .description(description)
        .returnUrl(payOSConfig.getReturnUrl())
        .cancelUrl(payOSConfig.getCancelUrl())
        .expiredAt(Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond())
        .build();
```

- [ ] **Step 2: Implement history and tier listing**

Add:

```text
getMyPaymentHistory(UUID uid)
getAllPaymentHistory(PaymentOrderStatus status, UUID userId, Pageable pageable)
listActiveTiers()
```

If the target app does not need admin history in the first port, still keep the endpoint out of public routes and add it later behind admin authorization.

- [ ] **Step 3: Implement PayOS return handling**

Rules:

- Look up by `orderCode`.
- Reject if authenticated uid is not the order owner.
- If current status is `CANCELLED`, `PAID`, `EXPIRED`, or `FAILED`, return current status without changes.
- `cancel=true` or status `CANCELLED` maps to `CANCELLED`.
- status `PAID` maps to `PAID`, sets `paidAt`, but does not activate tier.
- `PENDING` and `PROCESSING` cause no state change.

- [ ] **Step 4: Add unit tests**

Test names:

```text
createPayOSCheckout_rejectsFreeTier
createPayOSCheckout_rejectsWhenUserAlreadyHasPaidSubscription
handlePayOSReturn_rejectsOrderOwnedByDifferentUser
handlePayOSReturn_marksCancelledForCancelTrue
handlePayOSReturn_doesNotUpgradeTierWhenStatusPaid
```

Run:

```bash
mvn -q -Dtest=PaymentServiceTest test
```

Expected: all `PaymentServiceTest` tests pass.

## Task 7: Add PayOS Webhook Service

**Files:**
- Create: `service/payment/PaymentWebhookService.java`

- [ ] **Step 1: Implement dispatcher**

PayOS webhook event code handling:

```text
data.code == "00" -> paid
data.code == "01" -> cancelled
data.code == "02" -> expired
other -> log and ignore
```

- [ ] **Step 2: Implement paid flow**

Required paid flow:

1. Lock order by `orderCode`.
2. If order already `PAID`, return.
3. Validate `data.amount` equals order amount.
4. If both are present, validate `data.paymentLinkId` equals order payment link id.
5. Save transaction if `reference` does not already exist.
6. Mark order `PAID` and set `paidAt`.
7. Create paid `UserTier` from now until `now.plusMonths(tier.noMonth)`.
8. Reset user token to `tier.limitedToken` and `lastTokenResetAt` if target app uses tokens.

- [ ] **Step 3: Implement cancellation and expiry flow**

Rules:

- If order already `PAID`, ignore cancellation/expiry.
- If order already `CANCELLED` or `EXPIRED`, return.
- code `01` sets order `CANCELLED`.
- code `02` sets order `EXPIRED`.
- Save transaction with `FAILED` status unless reference already exists.

- [ ] **Step 4: Add unit tests**

Test names:

```text
handlePayOSWebhook_paidCreatesSubscriptionAndResetsTokens
handlePayOSWebhook_paidIsIdempotentForDuplicatePaidWebhook
handlePayOSWebhook_rejectsAmountMismatch
handlePayOSWebhook_cancelDoesNotOverridePaidOrder
handlePayOSWebhook_expiredMarksPendingOrderExpired
```

Run:

```bash
mvn -q -Dtest=PaymentWebhookServiceTest test
```

Expected: all `PaymentWebhookServiceTest` tests pass.

## Task 8: Add Controllers And Security Rules

**Files:**
- Create: `controller/payment/PaymentController.java`
- Create: `controller/payment/PayOSWebhookController.java`
- Modify: target security config

- [ ] **Step 1: Add PaymentController endpoints**

Implement these routes:

```text
POST /api/v1/payments/checkout
GET  /api/v1/payments/me
GET  /api/v1/payments/history
GET  /api/v1/payments/tiers
POST /api/v1/payments/payos/return
```

Authorization:

```text
checkout: authenticated
me: CUSTOMER
history: SYSTEM_ADMIN
tiers: public
payos/return: authenticated
```

- [ ] **Step 2: Add PayOSWebhookController**

Implementation requirements:

- `GET /api/v1/payments/payos/webhook` returns `200 OK` body `OK`.
- `POST /api/v1/payments/payos/webhook` accepts `vn.payos.model.webhooks.Webhook`.
- Call `payOS.webhooks().verify(webhook)` before service logic.
- Return `200 OK` body `OK` on success.
- Return `400 Bad Request` body `Invalid webhook` on signature or processing failure.

- [ ] **Step 3: Add security permits**

Add public matchers:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/payments/tiers").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/payments/payos/webhook").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/payments/payos/webhook").permitAll()
```

If the target app uses a servlet context path, do not duplicate the context path in Spring Security matchers unless the existing target config already does that.

- [ ] **Step 4: Controller tests**

Test names:

```text
webhookGetReturnsOk
webhookPostRejectsInvalidSignature
tiersEndpointIsPublic
checkoutRequiresAuthentication
```

Run:

```bash
mvn -q -Dtest=PayOSWebhookControllerTest test
```

Expected: controller tests pass.

## Task 9: Add Expiry Scheduler

**Files:**
- Create: `service/payment/PaymentExpiryScheduler.java`
- Modify: application class if `@EnableScheduling` is not already enabled.

- [ ] **Step 1: Enable scheduling**

If missing, add:

```java
@EnableScheduling
@SpringBootApplication
public class Application {
}
```

- [ ] **Step 2: Implement pending order expiry**

Every 60 seconds:

- find `PENDING` orders with `expiredAt <= now`
- set status to `EXPIRED`
- save all

- [ ] **Step 3: Implement paid subscription expiry**

Every 5 minutes:

- find active paid `UserTier` rows with `endTime <= now`
- set `isActive=false`
- do not expire free tier rows where `tier.amount = 0`

- [ ] **Step 4: Add scheduler tests**

Test names:

```text
expirePendingOrders_marksExpiredPendingOrders
expireUserTierSubscriptions_expiresOnlyPaidRows
```

Run:

```bash
mvn -q -Dtest=PaymentExpirySchedulerTest test
```

Expected: scheduler tests pass.

## Task 10: Integrate Registration And Profile

**Files:**
- Modify: target auth registration service
- Modify: target profile service or mapper if user profile shows tier/token

- [ ] **Step 1: Create free UserTier on registration**

After creating a new customer user:

1. Find active tier with title `free`.
2. Set user token to `free.limitedToken` if token quota exists.
3. Set `lastTokenResetAt=now` if token quota exists.
4. Save user.
5. Create `UserTier` with `startTime=now`, `endTime=now.plusMonths(free.noMonth > 0 ? free.noMonth : 120)`, `isActive=true`.

- [ ] **Step 2: Resolve active tier for profile**

When showing profile:

1. Query active non-expired `UserTier` rows ordered by tier amount descending.
2. Prefer paid tier over free tier.
3. If token quota exists, top up daily by active tier `limitedToken`.

- [ ] **Step 3: Add tests**

Test names:

```text
registerCustomerCreatesFreeUserTier
getMyProfilePrefersPaidTierOverFreeTier
getMyProfileAddsDailyTokenAllowanceOncePerDay
```

Run:

```bash
mvn -q -Dtest=AuthServiceImplTest,UserServiceImplTest test
```

Expected: registration/profile tests pass.

## Task 11: Manual End-To-End PayOS Verification

**Files:**
- No source files unless issues are found.

- [ ] **Step 1: Start target backend**

Run:

```bash
mvn spring-boot:run
```

Expected: backend starts on the configured port.

- [ ] **Step 2: Expose local backend for webhook**

Run:

```powershell
ngrok http 8080
```

Expected: ngrok prints an HTTPS forwarding URL.

- [ ] **Step 3: Register webhook in PayOS dashboard**

Use:

```text
https://abc123.ngrok-free.app/api/v1/payments/payos/webhook
```

If using context path:

```text
https://abc123.ngrok-free.app/Historical-tell/api/v1/payments/payos/webhook
```

PayOS may send `GET` to verify reachability. Expected backend response: `200 OK`.

- [ ] **Step 4: Create checkout**

Request:

```http
POST /api/v1/payments/checkout
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.customer_test_token_signature
Content-Type: application/json

{
  "tierId": "00000000-0000-0000-0000-000000000002"
}
```

Expected response includes:

```json
{
  "orderCode": 123456789,
  "checkoutUrl": "https://...",
  "qrCode": "...",
  "status": "PENDING"
}
```

- [ ] **Step 5: Complete a small real payment**

PayOS currently has no separate sandbox. Use a low amount tier in a controlled test account. After payment:

- frontend receives return/cancel query params
- frontend posts query params to `POST /api/v1/payments/payos/return`
- PayOS sends webhook to backend
- order becomes `PAID`
- transaction row is saved
- paid `UserTier` row is created
- user token resets to tier allowance if token quota exists

- [ ] **Step 6: Verify database**

Run SQL:

```sql
SELECT order_code, status, amount, payment_link_id, paid_at
FROM payment_order
ORDER BY created_at DESC
LIMIT 5;

SELECT status, reference, amount, transaction_date
FROM payment_transaction
ORDER BY created_at DESC
LIMIT 5;

SELECT uid, tier_id, start_time, end_time, is_active
FROM user_tier
ORDER BY created_at DESC
LIMIT 5;
```

Expected: latest order is `PAID`, latest transaction is `SUCCESS`, and latest paid `user_tier` is active.

## Risk Checklist

- [ ] Do not trust return URL params to activate subscriptions.
- [ ] Do not protect webhook with JWT. PayOS servers cannot send user JWT.
- [ ] Always verify webhook signature using checksum key before updating order/subscription.
- [ ] Keep webhook idempotent by checking current order status and transaction reference.
- [ ] Validate amount mismatch and payment link mismatch.
- [ ] Keep paid subscription stacking blocked unless target product explicitly supports upgrades.
- [ ] Keep PayOS description short. Source uses `HISTALK` plus last 6 order-code digits.
- [ ] Ensure `order_code` is unique in the database.
- [ ] Ensure public HTTPS webhook URL is registered in PayOS dashboard.
- [ ] Remember PayOS has production-only testing. Use small real transaction values.

## Final Validation Commands

Run these before marking the port complete:

```bash
mvn -q -DskipTests compile
mvn test
```

Manual validation:

```text
GET  /api/v1/payments/tiers -> 200 public
POST /api/v1/payments/checkout -> 401 without JWT, 200 with customer JWT and paid tier
GET  /api/v1/payments/payos/webhook -> 200 public
POST /api/v1/payments/payos/webhook with invalid signature -> 400
Real PayOS payment -> order PAID, transaction SUCCESS, paid UserTier active
```

## Suggested Commit Sequence

- `feat(payment): add payos config and dependency`
- `feat(payment): add payment schema and entities`
- `feat(payment): add payment repositories and dto contracts`
- `feat(payment): add payos checkout and return handling`
- `feat(payment): add verified payos webhook processing`
- `feat(payment): add payment expiry scheduler`
- `test(payment): cover checkout webhook and expiry flows`
