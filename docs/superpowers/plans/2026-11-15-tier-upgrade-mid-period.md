# Nâng gói giữa kỳ (Plus → Pro) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho phép user đang có gói trả phí còn hạn nâng lên gói cao hơn ngay giữa kỳ. Phần chưa dùng của gói cũ được quy đổi thành tiền giảm trừ (voucher) vào giá gói mới.

**Architecture:** Thêm một `TierUpgradeCalculator` thuần (không I/O) để xếp hạng gói và tính tiền quy đổi. `PaymentService` dùng nó thay cho đoạn guard đang chặn mọi lần mua chồng, rồi ghi số tiền đã giảm và gói bị thay thế vào `PaymentOrder`. `PaymentFulfillmentService` tắt các gói trả phí đang active trước khi tạo gói mới, nên tại mỗi thời điểm user chỉ có tối đa một gói trả phí active.

**Tech Stack:** Spring Boot 3.2, Java 21, JPA/Hibernate, Flyway, PayOS SDK, JUnit 5 + Mockito + AssertJ.

**Spec:** Không có design doc riêng. Các quyết định lấy từ trao đổi với user ngày 2026-11-15 và được ghi ở mục "Quyết định thiết kế" dưới đây.

## Quyết định thiết kế

**User đã quyết:**
- Dùng cách A: quy đổi phần gói cũ chưa dùng thành tiền giảm trừ. Gói mới bắt đầu từ lúc thanh toán thành công và chạy đủ `noMonth` tháng.
- Token ngày nâng gói: user **giữ token gói cũ đã nhận hôm đó và được cộng đủ `limitedToken` của gói mới**. Đây là phúc lợi nâng cấp để thu hút user. Nghĩa là giữ nguyên dòng cộng token hiện tại trong fulfillment, không trừ phần chênh lệch.
- **Việc cộng cả hai gói chỉ xảy ra đúng ngày nâng.** Từ ngày hôm sau, top-up hằng ngày chỉ tính theo `limitedToken` của gói mới. Điều này có được nhờ fulfillment tắt gói cũ (Task 3): `UserServiceImpl.getMyProfile` chỉ cộng các gói còn active, nên gói cũ không còn được tính.
- Không hạ gói. Nâng lên gói năm chỉ được chọn gói **cùng hạng hoặc cao hơn** (Plus tháng → Plus năm hoặc Pro năm; Pro tháng → Pro năm; Pro tháng → Plus năm bị từ chối).

**Claude tự quyết (user có thể sửa):**
- Thứ hạng gói xét theo `title` (không phân biệt hoa thường): `free = 0`, `plus = 1`, `pro = 2`. Title khác thì hạng `-1`, không cho nâng từ hoặc lên gói đó. Không xếp theo giá, vì Plus năm (499.000đ) đắt hơn Pro tháng (99.000đ).
- Cụ thể hoá quy tắc của user thành điều kiện: hạng gói mới **cao hơn**, hoặc **cùng hạng nhưng `noMonth` dài hơn**. Cùng hạng mà thời hạn bằng hoặc ngắn hơn (Plus năm → Plus tháng) thì từ chối.
- Khi tắt gói cũ thì đặt `endTime = now`, để lịch sử và số liệu "sắp hết hạn" trên dashboard phản ánh đúng.
- Thêm endpoint xem trước giá (`GET /api/v1/payments/upgrade-quote`), để frontend hiển thị số tiền phải trả trước khi tạo link PayOS.

## Công thức quy đổi

Gọi `sub` là gói trả phí đang active của user (`tier.amount > 0`), `target` là gói muốn mua, `now` là thời điểm checkout.

```
// 1. Giá trị gói đang dùng = số tiền user thực trả + tiền quy đổi đã dùng khi mua gói đó
value      = sub.paymentOrder != null
               ? sub.paymentOrder.amount + (sub.paymentOrder.upgradeCreditAmount ?? 0)
               : sub.tier.amount

// 2. Tỉ lệ thời gian còn lại, tính chính xác tới giây
total      = seconds(sub.startTime → sub.endTime)
remaining  = clamp(seconds(now → sub.endTime), 0, total)

// 3. Tiền quy đổi, làm tròn xuống tới đồng, tính bằng long để không tràn số
credit     = total == 0 ? 0 : floor(value × remaining / total)

// 4. Số tiền phải trả
amountToPay = target.amount − credit
```

Vì sao `value` cộng thêm `upgradeCreditAmount`: một gói Pro mua bằng cách nâng từ Plus có `amount = 74.500` nhưng thực chất trị giá 99.000. Nếu sau đó user nâng tiếp Pro tháng → Pro năm mà chỉ tính 74.500 thì user bị thiệt.

**Quy tắc chấp nhận hoặc từ chối:**

| Trường hợp | Kết quả |
|---|---|
| Không có gói trả phí active | Mua bình thường: `credit = 0`, `amountToPay = target.amount`, không có gói bị thay thế |
| `rank(target) > rank(sub.tier)` | Nâng gói |
| `rank` bằng nhau và `target.noMonth > sub.tier.noMonth` | Nâng gói |
| Các trường hợp còn lại (hạ gói, cùng gói, title lạ) | Từ chối: `InvalidRequestException` |
| `credit >= target.amount` | Từ chối: `InvalidRequestException`, gợi ý chọn gói dài hơn |

**Ví dụ (giá trị dùng trong test):**

| Ca | Gói cũ | Thời điểm | credit | amountToPay |
|---|---|---|---|---|
| Plus tháng → Pro tháng | 49.000, 2026-11-01 00:00 → 2026-12-01 00:00 (30 ngày) | 2026-11-16 00:00 (còn 15 ngày) | 24.500 | 74.500 |
| Plus năm → Pro năm | 499.000, 2026-01-01 → 2027-01-01 (365 ngày) | 2026-07-02 00:00 (còn 183 ngày) | 250.183 | 748.817 |
| Plus năm → Pro tháng | như trên | như trên | 250.183 ≥ 99.000 | từ chối |
| Pro đã nâng từ Plus → Pro năm | order.amount 74.500 + upgradeCredit 24.500 = 99.000; 2026-11-16 → 2026-12-16 (30 ngày) | 2026-12-01 00:00 (còn 15 ngày) | 49.500 | 949.500 |

## Global Constraints

- Lệnh Java (`mvn`) chạy từ `Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/`. Lệnh `git add` trong các bước commit viết theo thư mục `Source-code/SWD392_FinalProject_HistoryTalk/`.
- Schema đổi qua Flyway (`spring.jpa.hibernate.ddl-auto=none`). Tên migration theo kiểu timestamp: `V20261115100000__add_payment_order_upgrade_fields.sql`.
- Không thêm `@Version`, `@DynamicUpdate` hay query update từng cột. Dùng load-modify-save như code hiện tại.
- Thông báo lỗi của module payment viết bằng tiếng Anh, giống các message có sẵn trong `PaymentService`.
- Không sửa logic top-up hằng ngày trong `UserServiceImpl.getMyProfile` và không sửa `UserTierRepository.findCurrentActiveByUid`. Khi chỉ còn một gói trả phí active, `ORDER BY t.amount DESC` vẫn chọn gói trả phí trước gói free.
- RBAC theo `historytalk-overall-business-spec.md` §3: chỉ `CUSTOMER` và `SCHOOL_ADMIN` được mua tier qua PayOS, nên endpoint mới `GET /upgrade-quote` dùng `@PreAuthorize("hasAnyRole('CUSTOMER','SCHOOL_ADMIN')")`. Không sửa `/checkout`, để giữ phạm vi thay đổi.
- Soft-delete theo `historytalk-overall-technical-guide.md` §2.3: chỉ tính và chỉ tắt các `UserTier` có `deletedAt IS NULL`. Các query `findCurrentActiveByUid`/`findAllActiveByUid` đã lọc sẵn điều kiện này. Tier đích phải `isActive = true` và `deletedAt == null`.
- Kích hoạt gói và cộng token chỉ chạy trong luồng webhook đã xác thực HMAC (technical guide §4.1) và phải giữ idempotent: fulfill lại một order đã có `UserTier` thì không được tắt gói hay cộng token thêm lần nữa.
- Giữ nguyên việc top-up hằng ngày cộng cả dòng gói free đang active. User đã quyết giữ như vậy ngày 2026-11-15.
- `PaymentOrder.amount` là số tiền **thực trả** (`amountToPay`). Webhook đang so `data.amount` với giá trị này ([PaymentWebhookService.validatePayOSData](../../../Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentWebhookService.java)), nên không cần sửa webhook.

## Review Focus

1. **Hai đơn nâng gói cùng chờ thanh toán.** User mở hai link (ví dụ Pro tháng và Pro năm) rồi trả cả hai. Fulfillment phải tắt **mọi** gói trả phí active khác chứ không chỉ gói ghi trong `upgradeFromUserTier`, để sau cùng user chỉ còn một gói trả phí active. Test: `upgradeDeactivatesEveryOtherActivePaidSubscription` (Task 3).
2. **Gói cũ hết hạn trong 15 phút chờ thanh toán.** Scheduler có thể đã tắt gói cũ trước khi webhook về. Fulfillment vẫn phải thành công, không được ném lỗi. Test: `upgradeStillFulfillsWhenReplacedSubscriptionAlreadyExpired` (Task 3).
3. **Gói free không được bị tắt.** Fulfillment chỉ tắt các dòng có `tier.amount > 0`. Test: kiểm tra trong `upgradeDeactivatesEveryOtherActivePaidSubscription` (Task 3).
4. **Tier do admin tạo với title lạ** (ví dụ `"vip"`). Phải từ chối nâng, không được coi là hạng 0. Test: `quoteRejectsUnknownTierTitle` (Task 1).
5. **Số tiền rất nhỏ sau quy đổi.** Ví dụ còn vài phút là hết Plus năm mà mua Pro năm thì vẫn ổn. Nhưng nếu `amountToPay` chỉ vài nghìn đồng, PayOS có thể từ chối vì dưới mức tối thiểu. Người làm cần tra tài liệu PayOS về số tiền tối thiểu. Nếu có giới hạn thì thêm hằng `MIN_PAYOS_AMOUNT` vào `TierUpgradeCalculator` và từ chối khi `amountToPay` nhỏ hơn mức đó. Nếu không xác minh được thì ghi chú lại khi báo cáo.

---

### Task 1: TierUpgradeCalculator — xếp hạng gói và tính tiền quy đổi

**Files:**
- Create: `history-talk-backend-Java/src/main/java/com/historytalk/service/payment/TierUpgradeCalculator.java`
- Create: `history-talk-backend-Java/src/main/java/com/historytalk/service/payment/UpgradeQuote.java`
- Test: `history-talk-backend-Java/src/test/java/com/historytalk/service/payment/TierUpgradeCalculatorTest.java`

(Đường dẫn trong các task tính từ `Source-code/SWD392_FinalProject_HistoryTalk/`.)

**Interfaces:**
- Consumes: `Tier`, `UserTier`, `PaymentOrder` (trường `upgradeCreditAmount` được thêm ở Task 2, nên Task 1 phải thêm sẵn trường này vào entity: `@Column(name = "upgrade_credit_amount") private Integer upgradeCreditAmount;`. Task 2 sẽ thêm migration và trường còn lại).
- Produces:
  - `public record UpgradeQuote(UserTier replacedSubscription /* null nếu mua mới */, int originalAmount, int creditAmount, int amountToPay)`
  - `@Component public class TierUpgradeCalculator`
    - `int rank(Tier tier)` trả về `0` cho free, `1` cho plus, `2` cho pro, `-1` cho title khác hoặc null.
    - `boolean isUpgrade(Tier current, Tier target)`
    - `int subscriptionValue(UserTier sub)`
    - `int credit(UserTier sub, LocalDateTime now)`
    - `UpgradeQuote quote(UserTier currentPaidSub /* nullable */, Tier target, LocalDateTime now)`. Ném `InvalidRequestException` khi không được nâng hoặc khi `credit >= target.amount`.

- [ ] **Step 1: Viết test (sẽ fail)**

Dùng helper tạo `Tier`/`UserTier`/`PaymentOrder` bằng builder. Các test và assertion:

```java
@Test void rankFollowsTitleIgnoringCase() {
    assertThat(calc.rank(tier("FREE", 0, 1))).isEqualTo(0);
    assertThat(calc.rank(tier("Plus", 49_000, 1))).isEqualTo(1);
    assertThat(calc.rank(tier("pro", 99_000, 12))).isEqualTo(2);
    assertThat(calc.rank(tier("vip", 10_000, 1))).isEqualTo(-1);
}

@Test void plusMonthlyToProMonthlyHalfwayCredits24500() {
    UserTier sub = sub(tier("plus", 49_000, 1), order(49_000, null),
            at(2026, 11, 1), at(2026, 12, 1));
    UpgradeQuote q = calc.quote(sub, tier("pro", 99_000, 1), at(2026, 11, 16));
    assertThat(q.originalAmount()).isEqualTo(99_000);
    assertThat(q.creditAmount()).isEqualTo(24_500);
    assertThat(q.amountToPay()).isEqualTo(74_500);
    assertThat(q.replacedSubscription()).isSameAs(sub);
}

@Test void plusYearlyToProYearlyCredits250183() {
    UserTier sub = sub(tier("plus", 499_000, 12), order(499_000, null),
            at(2026, 1, 1), at(2027, 1, 1));
    UpgradeQuote q = calc.quote(sub, tier("pro", 999_000, 12), at(2026, 7, 2));
    assertThat(q.creditAmount()).isEqualTo(250_183);
    assertThat(q.amountToPay()).isEqualTo(748_817);
}

@Test void plusYearlyToProMonthlyRejectedWhenCreditCoversPrice() {
    UserTier sub = sub(tier("plus", 499_000, 12), order(499_000, null),
            at(2026, 1, 1), at(2027, 1, 1));
    assertThatThrownBy(() -> calc.quote(sub, tier("pro", 99_000, 1), at(2026, 7, 2)))
            .isInstanceOf(InvalidRequestException.class);
}

@Test void previouslyUpgradedSubscriptionValuedAtPaidPlusCredit() {
    UserTier sub = sub(tier("pro", 99_000, 1), order(74_500, 24_500),
            at(2026, 11, 16), at(2026, 12, 16));
    assertThat(calc.subscriptionValue(sub)).isEqualTo(99_000);
    UpgradeQuote q = calc.quote(sub, tier("pro", 999_000, 12), at(2026, 12, 1));
    assertThat(q.creditAmount()).isEqualTo(49_500);
    assertThat(q.amountToPay()).isEqualTo(949_500);
}

@Test void sameRankLongerDurationIsUpgrade() {
    assertThat(calc.isUpgrade(tier("plus", 49_000, 1), tier("plus", 499_000, 12))).isTrue();
}

@Test void downgradeAndSamePlanRejected() {
    UserTier pro = sub(tier("pro", 99_000, 1), order(99_000, null), at(2026, 11, 1), at(2026, 12, 1));
    assertThatThrownBy(() -> calc.quote(pro, tier("plus", 49_000, 1), at(2026, 11, 16)))
            .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> calc.quote(pro, tier("pro", 99_000, 1), at(2026, 11, 16)))
            .isInstanceOf(InvalidRequestException.class);
    // Pro tháng → Plus năm: lên gói năm nhưng hạ hạng → từ chối
    assertThatThrownBy(() -> calc.quote(pro, tier("plus", 499_000, 12), at(2026, 11, 16)))
            .isInstanceOf(InvalidRequestException.class);
    // Plus năm → Plus tháng: cùng hạng nhưng ngắn hơn → từ chối
    assertThat(calc.isUpgrade(tier("plus", 499_000, 12), tier("plus", 49_000, 1))).isFalse();
}

@Test void quoteRejectsUnknownTierTitle() {
    UserTier plus = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 12, 1));
    assertThatThrownBy(() -> calc.quote(plus, tier("vip", 200_000, 1), at(2026, 11, 16)))
            .isInstanceOf(InvalidRequestException.class);
}

@Test void noCurrentPaidSubscriptionMeansFullPrice() {
    UpgradeQuote q = calc.quote(null, tier("pro", 99_000, 1), at(2026, 11, 16));
    assertThat(q.creditAmount()).isZero();
    assertThat(q.amountToPay()).isEqualTo(99_000);
    assertThat(q.replacedSubscription()).isNull();
}

@Test void creditIsZeroAfterEndTimeAndWhenPeriodIsEmpty() {
    UserTier ended = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 12, 1));
    assertThat(calc.credit(ended, at(2026, 12, 5))).isZero();
    UserTier empty = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 11, 1));
    assertThat(calc.credit(empty, at(2026, 11, 1))).isZero();
}

@Test void subscriptionWithoutOrderFallsBackToTierAmount() {
    UserTier sub = sub(tier("plus", 49_000, 1), null, at(2026, 11, 1), at(2026, 12, 1));
    assertThat(calc.subscriptionValue(sub)).isEqualTo(49_000);
}
```

`at(y, m, d)` = `LocalDateTime.of(y, m, d, 0, 0)`.

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -Dtest=TierUpgradeCalculatorTest`
Expected: lỗi biên dịch, chưa có `TierUpgradeCalculator` / `UpgradeQuote`.

- [ ] **Step 3: Cài đặt `UpgradeQuote`, `TierUpgradeCalculator` và thêm trường `upgradeCreditAmount` vào `PaymentOrder`**

Làm theo đúng mục "Công thức quy đổi": dùng `Duration.between(...).getSeconds()`, và nhân/chia bằng `long` trước khi ép về `int`. Message từ chối:
- Không được nâng: `"You can only upgrade to a higher plan (or a longer plan of the same level). Current plan: '<title>'."`
- Tiền quy đổi đủ trả gói mới: `"Your remaining credit of <credit> VND already covers '<title>'. Please choose a longer plan."`

- [ ] **Step 4: Chạy test, xác nhận pass**

Run: `mvn -q test -Dtest=TierUpgradeCalculatorTest`
Expected: PASS, 11 test.

- [ ] **Step 5: Commit**

```bash
git add history-talk-backend-Java/src/main/java/com/historytalk/service/payment/TierUpgradeCalculator.java \
        history-talk-backend-Java/src/main/java/com/historytalk/service/payment/UpgradeQuote.java \
        history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/PaymentOrder.java \
        history-talk-backend-Java/src/test/java/com/historytalk/service/payment/TierUpgradeCalculatorTest.java
git commit -m "feat(java): add tier upgrade calculator with prorated credit"
```

---

### Task 2: Checkout nâng gói, endpoint xem giá và migration

**Files:**
- Create: `history-talk-backend-Java/src/main/resources/db/migration/V20261115100000__add_payment_order_upgrade_fields.sql`
- Create: `history-talk-backend-Java/src/main/java/com/historytalk/dto/payment/UpgradeQuoteResponse.java`
- Create: `docs/services/history-talk-backend/tier-upgrade-api-contract.md`
- Modify: `history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/PaymentOrder.java`
- Modify: `history-talk-backend-Java/src/main/java/com/historytalk/dto/payment/CreatePaymentResponse.java`
- Modify: `history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java:52-128`
- Modify: `history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java`
- Test: `history-talk-backend-Java/src/test/java/com/historytalk/service/payment/PaymentServiceUpgradeTest.java`

**Interfaces:**
- Consumes: `TierUpgradeCalculator.quote(...)` và `UpgradeQuote` (Task 1).
- Produces:
  - `PaymentOrder.upgradeFromUserTier`: `@ManyToOne(fetch = LAZY) @JoinColumn(name = "upgrade_from_user_tier_id") UserTier`, nullable.
  - `PaymentOrder.upgradeCreditAmount`: `Integer`, nullable (đã thêm ở Task 1).
  - `CreatePaymentResponse` thêm `Integer originalAmount` và `Integer upgradeCreditAmount`. `amount` vẫn là số tiền thực trả.
  - `UpgradeQuoteResponse` (`@Data @Builder`): `String currentTierId, String currentTierTitle, Integer currentTierNoMonth, String currentEndTime, String targetTierId, String targetTierTitle, Integer targetTierNoMonth, Integer originalAmount, Integer creditAmount, Integer amountToPay, Boolean upgrade`.
  - `PaymentService.getUpgradeQuote(UUID uid, String tierId): UpgradeQuoteResponse`
  - `GET /api/v1/payments/upgrade-quote?tierId=<uuid>` → `ApiResponse<UpgradeQuoteResponse>`, message `"Upgrade quote calculated successfully"`.

- [ ] **Step 1: Viết test (sẽ fail)**

`PaymentServiceUpgradeTest`: dùng `@ExtendWith(MockitoExtension.class)`, `@Mock(answer = Answers.RETURNS_DEEP_STUBS) PayOS payOS`, các repository và `PayOSConfig` để mock, còn `TierUpgradeCalculator` là instance thật (`@Spy`). Stub `userTierRepository.findCurrentActiveByUid(eq(uid), any())` để trả gói Plus tháng: 49.000, `startTime = now − 15 ngày`, `endTime = now + 15 ngày`, có `paymentOrder.amount = 49_000`.

```java
@Test void checkoutForHigherTierCreatesDiscountedOrderLinkedToReplacedSubscription() {
    // target = pro 99_000, noMonth 1
    CreatePaymentResponse res = service.createPayOSCheckout(uid, proTierId.toString());
    ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
    verify(paymentOrderRepository, atLeastOnce()).save(saved.capture());
    PaymentOrder order = saved.getValue();
    assertThat(order.getAmount()).isEqualTo(74_500);
    assertThat(order.getUpgradeCreditAmount()).isEqualTo(24_500);
    assertThat(order.getUpgradeFromUserTier()).isSameAs(plusSub);
    assertThat(res.getAmount()).isEqualTo(74_500);
    assertThat(res.getOriginalAmount()).isEqualTo(99_000);
    assertThat(res.getUpgradeCreditAmount()).isEqualTo(24_500);
}

@Test void checkoutForLowerTierIsRejectedAndCreatesNoOrder() {
    // current = pro, target = plus
    assertThatThrownBy(() -> service.createPayOSCheckout(uid, plusTierId.toString()))
            .isInstanceOf(InvalidRequestException.class);
    verify(paymentOrderRepository, never()).save(any());
}

@Test void checkoutWithoutPaidSubscriptionChargesFullPrice() {
    // findCurrentActiveByUid trả về gói free (amount 0)
    CreatePaymentResponse res = service.createPayOSCheckout(uid, proTierId.toString());
    assertThat(res.getAmount()).isEqualTo(99_000);
    assertThat(res.getUpgradeCreditAmount()).isZero();
    // order.getUpgradeFromUserTier() == null
}

@Test void upgradeQuoteReturnsSameNumbersWithoutSavingAnything() {
    UpgradeQuoteResponse q = service.getUpgradeQuote(uid, proTierId.toString());
    assertThat(q.getUpgrade()).isTrue();
    assertThat(q.getCreditAmount()).isEqualTo(24_500);
    assertThat(q.getAmountToPay()).isEqualTo(74_500);
    assertThat(q.getCurrentTierTitle()).isEqualTo("plus");
    verify(paymentOrderRepository, never()).save(any());
}
```

`createPayOSCheckout` khai báo `throws Exception`, nên các test này khai báo `throws Exception`. Để kết quả không phụ thuộc thời điểm chạy, stub `startTime`/`endTime` lệch nhau đúng ±15 ngày quanh `LocalDateTime.now()`. Nếu credit lệch 1 đồng do vài mili giây chạy test, so bằng `isCloseTo(24_500, within(1))` cho `creditAmount`/`amountToPay`. Đây là chỗ duy nhất được nới.

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -Dtest=PaymentServiceUpgradeTest`
Expected: lỗi biên dịch (chưa có `getUpgradeFromUserTier`, `getOriginalAmount`, `getUpgradeQuote`).

- [ ] **Step 3: Viết migration**

```sql
ALTER TABLE payment_order
    ADD COLUMN IF NOT EXISTS upgrade_from_user_tier_id uuid NULL
        REFERENCES user_tier(id),
    ADD COLUMN IF NOT EXISTS upgrade_credit_amount integer NULL;
```

Trước khi viết, xem một migration gần đây (ví dụ `V20261106150000__b2b_quote_request_and_updated_pricing.sql`) để biết các file khác có ghi rõ schema hay dựa vào `spring.flyway.schemas`, rồi làm theo y vậy.

- [ ] **Step 4: Thêm `upgradeFromUserTier` vào `PaymentOrder` và thêm 2 trường vào `CreatePaymentResponse`; tạo `UpgradeQuoteResponse`**

- [ ] **Step 5: Sửa `PaymentService`**

- Inject `TierUpgradeCalculator`.
- Thêm helper private `UpgradeQuote resolveQuote(UUID uid, Tier target, LocalDateTime now)`. Helper gọi `findCurrentActiveByUid`; chỉ truyền gói vào `calculator.quote` khi `tier.amount > 0`, ngược lại truyền `null`.
- Trong `createPayOSCheckout`, thay khối guard ở dòng 68-75 bằng `resolveQuote`. Builder của `PaymentOrder` dùng `.amount(quote.amountToPay())`, `.upgradeFromUserTier(quote.replacedSubscription())`, `.upgradeCreditAmount(quote.creditAmount())`. `CreatePaymentLinkRequest.amount` dùng `quote.amountToPay()`. Response điền thêm `originalAmount` và `upgradeCreditAmount`.
- Thêm `getUpgradeQuote(uid, tierId)` (`@Transactional(readOnly = true)`). Validate tier giống checkout (tồn tại, active, chưa xoá, `amount > 0`), rồi map `UpgradeQuote` sang `UpgradeQuoteResponse`. `upgrade = replacedSubscription != null`.

- [ ] **Step 6: Thêm endpoint vào `PaymentController`**

`@GetMapping("/upgrade-quote")` kèm `@PreAuthorize("hasAnyRole('CUSTOMER','SCHOOL_ADMIN')")`, nhận `@RequestParam String tierId`, lấy `uid` qua `SecurityUtils.getUserId()` giống `/checkout`.

- [ ] **Step 7: Chạy test, xác nhận pass**

Run: `mvn -q test -Dtest=PaymentServiceUpgradeTest,TierUpgradeCalculatorTest`
Expected: PASS.

- [ ] **Step 8: Viết tài liệu API cho frontend**

File `docs/services/history-talk-backend/tier-upgrade-api-contract.md` gồm:
- Request/response của `GET /upgrade-quote` và các trường mới của `POST /checkout`.
- Bảng công thức và ví dụ (chép từ mục "Công thức quy đổi" của plan này).
- Ghi chú: ngày nâng gói user giữ token gói cũ và nhận đủ token gói mới.
- Danh sách lỗi `400`.

- [ ] **Step 9: Commit**

```bash
git add history-talk-backend-Java/src/main/resources/db/migration/V20261115100000__add_payment_order_upgrade_fields.sql \
        history-talk-backend-Java/src/main/java/com/historytalk/entity/payment/PaymentOrder.java \
        history-talk-backend-Java/src/main/java/com/historytalk/dto/payment/CreatePaymentResponse.java \
        history-talk-backend-Java/src/main/java/com/historytalk/dto/payment/UpgradeQuoteResponse.java \
        history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentService.java \
        history-talk-backend-Java/src/main/java/com/historytalk/controller/payment/PaymentController.java \
        history-talk-backend-Java/src/test/java/com/historytalk/service/payment/PaymentServiceUpgradeTest.java \
        ../../docs/services/history-talk-backend/tier-upgrade-api-contract.md
git commit -m "feat(java): allow mid-period tier upgrade checkout with prorated credit"
```

---

### Task 3: Fulfillment tắt gói cũ khi kích hoạt gói mới

**Files:**
- Modify: `history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentFulfillmentService.java:67-92`
- Test: `history-talk-backend-Java/src/test/java/com/historytalk/service/payment/PaymentFulfillmentServiceTest.java`
- Test: `history-talk-backend-Java/src/test/java/com/historytalk/service/user/UserServiceImplTest.java` (file đã có, thêm test vào)

**Interfaces:**
- Consumes: `UserTierRepository.findAllActiveByUid(UUID uid, LocalDateTime now)` (có sẵn), `PaymentOrder.upgradeFromUserTier` (Task 2).
- Produces: bất biến "sau khi fulfill, user chỉ còn đúng một `UserTier` trả phí active, là gói vừa mua".

- [ ] **Step 1: Viết test (sẽ fail)**

Thêm vào `PaymentFulfillmentServiceTest`:

```java
@Test void upgradeDeactivatesEveryOtherActivePaidSubscription() {
    // user.token = 3_000; active: freeSub (amount 0), plusSub (49_000), proMonthSub (99_000, từ đơn khác)
    // order: tier = pro năm (999_000, noMonth 12, limitedToken 10_000), upgradeFromUserTier = plusSub
    when(userTierRepository.findAllActiveByUid(eq(userId), any())).thenReturn(List.of(freeSub, plusSub, proMonthSub));
    boolean ok = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());
    assertThat(ok).isTrue();
    assertThat(plusSub.getIsActive()).isFalse();
    assertThat(proMonthSub.getIsActive()).isFalse();
    assertThat(plusSub.getEndTime()).isBeforeOrEqualTo(LocalDateTime.now());
    assertThat(freeSub.getIsActive()).isTrue();
    assertThat(user.getToken()).isEqualTo(13_000);   // giữ 3_000 cũ + đủ 10_000 của gói mới
    // UserTier mới được save: tier = pro năm, isActive = true, endTime ≈ now + 12 tháng
}

@Test void upgradeStillFulfillsWhenReplacedSubscriptionAlreadyExpired() {
    // order.upgradeFromUserTier = plusSub đã có isActive = false (scheduler đã tắt)
    when(userTierRepository.findAllActiveByUid(eq(userId), any())).thenReturn(List.of(freeSub));
    assertThat(paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode())).isTrue();
    assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FULFILLED);
}
```

Để chốt quy tắc "chỉ cộng cả hai gói vào ngày nâng", thêm test vào `UserServiceImplTest.java` có sẵn, theo cách mock đang dùng trong file đó:

```java
@Test void dailyTopUpAfterUpgradeDayCountsOnlyTheNewPaidPlan() {
    // user.token = 13_000, lastTokenResetAt = hôm qua
    // findAllActiveByUid trả về [proSub (limitedToken 10_000)] — gói Plus đã bị fulfillment tắt
    userService.getMyProfile(userId.toString());
    assertThat(user.getToken()).isEqualTo(23_000);   // +10_000, không phải +15_000
}
```

Test cũ `fulfillPaidOrderCreatesSubscriptionAndResetsTokens` vẫn phải pass. Nếu Mockito strict stubs báo thiếu stub cho `findAllActiveByUid` thì thêm stub trả `List.of()`.

- [ ] **Step 2: Chạy test, xác nhận fail**

Run: `mvn -q test -Dtest=PaymentFulfillmentServiceTest`
Expected: FAIL ở `upgradeDeactivatesEveryOtherActivePaidSubscription` (gói Plus vẫn active).

- [ ] **Step 3: Sửa `fulfillLockedPaidOrder`**

Ngay trước khi build `newSub`, gọi `findAllActiveByUid(user.getUid(), now)` rồi lọc các dòng có `tier.amount > 0`. Với mỗi dòng: `setIsActive(false)`, `setEndTime(now)`, sau đó `saveAll`. Sửa comment ở dòng 77 thành: `// Only one paid subscription may be active: the new one replaces any active paid plan (mid-period upgrade).` Giữ nguyên phần cộng token ở dòng 89-91 (đây là phúc lợi nâng cấp user đã chọn).

- [ ] **Step 4: Chạy toàn bộ test payment, xác nhận pass**

Run: `mvn -q test -Dtest='Payment*Test,TierUpgradeCalculatorTest'`
Expected: PASS, gồm cả `PaymentWebhookReplayTest`.

- [ ] **Step 5: Kiểm tra build toàn module**

Run: `mvn test`
Expected: BUILD SUCCESS. Nếu có test không liên quan fail sẵn từ trước thì ghi rõ tên test trong báo cáo, không sửa.

- [ ] **Step 6: Commit**

```bash
git add history-talk-backend-Java/src/main/java/com/historytalk/service/payment/PaymentFulfillmentService.java \
        history-talk-backend-Java/src/test/java/com/historytalk/service/payment/PaymentFulfillmentServiceTest.java \
        history-talk-backend-Java/src/test/java/com/historytalk/service/user/UserServiceImplTest.java
git commit -m "feat(java): deactivate previous paid plan when fulfilling an upgrade order"
```

---

## Kiểm tra thủ công sau khi xong (nếu chạy được service)

Mở Swagger `http://localhost:8080/Historical-tell/api/v1/swagger-ui` với user đang có Plus:
1. `GET /payments/upgrade-quote?tierId=<pro>` → trả `creditAmount` > 0 và `amountToPay` = 99.000 − credit.
2. `POST /payments/checkout` với Pro → `amount` khớp `amountToPay`.
3. `GET /payments/upgrade-quote?tierId=<plus tháng>` khi đang có Pro → lỗi 400.
