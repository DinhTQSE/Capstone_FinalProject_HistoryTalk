# Admin Payment History — Implementation Plan

## Decisions (confirmed by user)

| # | Decision | Confirmed |
|---|----------|-----------|
| 1 | Admin access: **`SYSTEM_ADMIN` only** | ✅ |
| 2 | Location: add to **existing `PaymentController`** (no new controller file) | ✅ |
| 3 | Filters: **optional** `status` + `userId` query params (server-side, easier for frontend) | ✅ |
| 4 | `SecurityConfig.java`: **not modified** | ✅ |
| 5 | Rename customer endpoint from `/history` → `/me` and admin gets `/history` | ✅ |

---

## Why server-side filters (not client-side)

Server-side `?status=PAID&userId=xxx` means the frontend sends one targeted request and gets back exactly the rows it needs.
Without filters, the frontend would have to fetch all pages and filter in memory — expensive as history grows.
Both `status` and `userId` are **optional**: omitting them returns all orders (the default admin view).

---

## Endpoint summary after changes

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/v1/payments/me` | `CUSTOMER` JWT | Own payment history (renamed from `/history`) |
| `GET` | `/api/v1/payments/history` | `SYSTEM_ADMIN` JWT | All customers' payment history, paginated |

> [!NOTE]
> The response shape follows the same `PaginatedResponse<T>` pattern used by `GET /api/v1/quizzes/results/me`
> with `page` / `size` query params (0-indexed). Only the admin response adds `userId`, `userName`, `userEmail`.

---

## Proposed Changes

### 1 — DTO layer

#### [NEW] `AdminPaymentHistoryResponse.java`
`src/main/java/com/historytalk/dto/payment/AdminPaymentHistoryResponse.java`

Same fields as `PaymentHistoryResponse` **plus** the customer's identity:

```
orderId, orderCode, tierId, tierTitle, amount, status,
paymentLinkId, createdAt, paidAt, expiredAt,
userId, userName, userEmail          ← admin-only fields
```

Existing `PaymentHistoryResponse` is **not modified**.

---

### 2 — Repository layer

#### [MODIFY] [PaymentOrderRepository.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/repository/payment/PaymentOrderRepository.java)

Add one new JPQL method with optional `status` / `userId` filters and `JOIN FETCH` to avoid N+1 on the lazy `user` and `tier` associations:

```java
@Query("""
    SELECT o FROM PaymentOrder o
    JOIN FETCH o.user u
    JOIN FETCH o.tier t
    WHERE o.deletedAt IS NULL
      AND (:status IS NULL OR o.status = :status)
      AND (:userId IS NULL OR u.uid = :userId)
    ORDER BY o.createdAt DESC
""")
Page<PaymentOrder> findAllForAdmin(
        @Param("status") PaymentOrderStatus status,
        @Param("userId") UUID userId,
        Pageable pageable);
```

> [!NOTE]
> Passing `null` for either param makes it behave as "no filter" — so no extra method is needed for the unfiltered case.

---

### 3 — Service layer

#### [MODIFY] [PaymentService.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java)

Two changes:

**a)** Rename `getPaymentHistory(UUID uid)` → `getMyPaymentHistory(UUID uid)` to match the new `/me` endpoint naming. Internal logic unchanged.

**b)** Add a new `@Transactional(readOnly = true)` method:

```java
public PaginatedResponse<AdminPaymentHistoryResponse> getAllPaymentHistory(
        PaymentOrderStatus status,
        UUID userId,
        Pageable pageable)
```

Internally:
1. Calls `paymentOrderRepository.findAllForAdmin(status, userId, pageable)`.
2. Maps each `PaymentOrder` → `AdminPaymentHistoryResponse` (includes `user.uid`, `user.userName`, `user.email`).
3. Wraps the `Page` in the existing `PaginatedResponse<T>` builder.

---

### 4 — Controller layer

#### [MODIFY] [PaymentController.java](file:///d:/Class/HistoryTalk/SWD392_FinalProject_HistoryTalk/Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java)

Three changes in the **same file** (no new controller created):

**a)** Rename existing `GET /history` to `GET /me` and add `@PreAuthorize("hasRole('CUSTOMER')")`:

```java
// BEFORE
@GetMapping("/history")
public ResponseEntity<...> getPaymentHistory() { ... }

// AFTER
@GetMapping("/me")
@PreAuthorize("hasRole('CUSTOMER')")
public ResponseEntity<...> getMyPaymentHistory() { ... }
```

**b)** Add new `GET /history` admin endpoint with optional filters:

```java
@GetMapping("/history")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public ResponseEntity<ApiResponse<PaginatedResponse<AdminPaymentHistoryResponse>>> getAllPaymentHistory(
        @RequestParam(required = false) PaymentOrderStatus status,
        @RequestParam(required = false) UUID userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    var data = paymentService.getAllPaymentHistory(status, userId, pageable);
    return ResponseEntity.ok(ApiResponse.success(data, "Payment history retrieved successfully"));
}
```

Example frontend calls:
- All orders: `GET /api/v1/payments/history?page=0&size=20`
- Filter by status: `GET /api/v1/payments/history?status=PAID`
- Filter by customer: `GET /api/v1/payments/history?userId=<uuid>`
- Both: `GET /api/v1/payments/history?status=PAID&userId=<uuid>`

**c)** The call to `paymentService.getPaymentHistory(uid)` is updated to `paymentService.getMyPaymentHistory(uid)` to match the service rename.

---

### 5 — SecurityConfig.java — **NOT modified** ✅

`@PreAuthorize` annotations on the controller methods are sufficient. Method-level security is already enabled via `@EnableMethodSecurity(prePostEnabled = true)` in `SecurityConfig`.

---

## What is NOT changing

| Item | Reason |
|------|--------|
| `PaymentHistoryResponse` DTO | Customer DTO stays as-is |
| Webhook / checkout paths | Unrelated |
| `PaymentOrderStatus` enum | Already has all required values |
| `SecurityConfig.java` | Not touched |

---

## Verification Plan

### Automated build check
```
mvn -q -DskipTests compile
```

### Manual verification (Swagger at `:8080/Historical-tell/api/v1/swagger-ui`)
1. Login as `CUSTOMER` → `GET /api/v1/payments/me` → expect 200 with own orders only.
2. Login as `CUSTOMER` → `GET /api/v1/payments/history` → expect 403.
3. Login as `SYSTEM_ADMIN` → `GET /api/v1/payments/history` → expect 200 with all customers' orders paginated.
4. Login as `CONTENT_ADMIN` → `GET /api/v1/payments/history` → expect 403.
5. `GET /api/v1/payments/history?page=0&size=5` → verify pagination fields (`totalElements`, `totalPages`, `hasNext`, etc.) are correct.
