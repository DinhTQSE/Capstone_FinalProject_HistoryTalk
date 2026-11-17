package com.historytalk.service.payment;

import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TierUpgradeCalculatorTest {

    private final TierUpgradeCalculator calc = new TierUpgradeCalculator();

    @Test
    void rankFollowsTitleIgnoringCase() {
        assertThat(calc.rank(tier("FREE", 0, 1))).isEqualTo(0);
        assertThat(calc.rank(tier("Plus", 49_000, 1))).isEqualTo(1);
        assertThat(calc.rank(tier("pro", 99_000, 12))).isEqualTo(2);
        assertThat(calc.rank(tier("vip", 10_000, 1))).isEqualTo(-1);
    }

    @Test
    void plusMonthlyToProMonthlyHalfwayCredits24500() {
        UserTier sub = sub(tier("plus", 49_000, 1), order(49_000, null),
                at(2026, 11, 1), at(2026, 12, 1));
        UpgradeQuote q = calc.quote(sub, tier("pro", 99_000, 1), at(2026, 11, 16));
        assertThat(q.originalAmount()).isEqualTo(99_000);
        assertThat(q.creditAmount()).isEqualTo(24_500);
        assertThat(q.amountToPay()).isEqualTo(74_500);
        assertThat(q.replacedSubscription()).isSameAs(sub);
    }

    @Test
    void plusYearlyToProYearlyCredits250183() {
        UserTier sub = sub(tier("plus", 499_000, 12), order(499_000, null),
                at(2026, 1, 1), at(2027, 1, 1));
        UpgradeQuote q = calc.quote(sub, tier("pro", 999_000, 12), at(2026, 7, 2));
        assertThat(q.creditAmount()).isEqualTo(250_183);
        assertThat(q.amountToPay()).isEqualTo(748_817);
    }

    @Test
    void plusYearlyToProMonthlyRejectedWhenCreditCoversPrice() {
        UserTier sub = sub(tier("plus", 499_000, 12), order(499_000, null),
                at(2026, 1, 1), at(2027, 1, 1));
        assertThatThrownBy(() -> calc.quote(sub, tier("pro", 99_000, 1), at(2026, 7, 2)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void previouslyUpgradedSubscriptionValuedAtPaidPlusCredit() {
        UserTier sub = sub(tier("pro", 99_000, 1), order(74_500, 24_500),
                at(2026, 11, 16), at(2026, 12, 16));
        assertThat(calc.subscriptionValue(sub)).isEqualTo(99_000);
        UpgradeQuote q = calc.quote(sub, tier("pro", 999_000, 12), at(2026, 12, 1));
        assertThat(q.creditAmount()).isEqualTo(49_500);
        assertThat(q.amountToPay()).isEqualTo(949_500);
    }

    @Test
    void sameRankLongerDurationIsUpgrade() {
        assertThat(calc.isUpgrade(tier("plus", 49_000, 1), tier("plus", 499_000, 12))).isTrue();
    }

    @Test
    void downgradeAndSamePlanRejected() {
        UserTier pro = sub(tier("pro", 99_000, 1), order(99_000, null), at(2026, 11, 1), at(2026, 12, 1));
        assertThatThrownBy(() -> calc.quote(pro, tier("plus", 49_000, 1), at(2026, 11, 16)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> calc.quote(pro, tier("pro", 99_000, 1), at(2026, 11, 16)))
                .isInstanceOf(InvalidRequestException.class);
        // Pro monthly -> Plus yearly: longer but lower level -> rejected
        assertThatThrownBy(() -> calc.quote(pro, tier("plus", 499_000, 12), at(2026, 11, 16)))
                .isInstanceOf(InvalidRequestException.class);
        // Plus yearly -> Plus monthly: same level but shorter -> not an upgrade
        assertThat(calc.isUpgrade(tier("plus", 499_000, 12), tier("plus", 49_000, 1))).isFalse();
    }

    @Test
    void quoteRejectsUnknownTierTitle() {
        UserTier plus = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 12, 1));
        assertThatThrownBy(() -> calc.quote(plus, tier("vip", 200_000, 1), at(2026, 11, 16)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void noCurrentPaidSubscriptionMeansFullPrice() {
        UpgradeQuote q = calc.quote(null, tier("pro", 99_000, 1), at(2026, 11, 16));
        assertThat(q.creditAmount()).isZero();
        assertThat(q.amountToPay()).isEqualTo(99_000);
        assertThat(q.replacedSubscription()).isNull();
    }

    @Test
    void creditIsZeroAfterEndTimeAndWhenPeriodIsEmpty() {
        UserTier ended = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 12, 1));
        assertThat(calc.credit(ended, at(2026, 12, 5))).isZero();
        UserTier empty = sub(tier("plus", 49_000, 1), order(49_000, null), at(2026, 11, 1), at(2026, 11, 1));
        assertThat(calc.credit(empty, at(2026, 11, 1))).isZero();
    }

    @Test
    void subscriptionWithoutOrderFallsBackToTierAmount() {
        UserTier sub = sub(tier("plus", 49_000, 1), null, at(2026, 11, 1), at(2026, 12, 1));
        assertThat(calc.subscriptionValue(sub)).isEqualTo(49_000);
    }

    private static LocalDateTime at(int y, int m, int d) {
        return LocalDateTime.of(y, m, d, 0, 0);
    }

    private static Tier tier(String title, int amount, int noMonth) {
        return Tier.builder()
                .tierId(UUID.randomUUID())
                .title(title)
                .amount(amount)
                .noMonth(noMonth)
                .limitedToken(1_000)
                .isActive(true)
                .build();
    }

    private static PaymentOrder order(int amount, Integer upgradeCredit) {
        return PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .amount(amount)
                .upgradeCreditAmount(upgradeCredit)
                .build();
    }

    private static UserTier sub(Tier tier, PaymentOrder order, LocalDateTime start, LocalDateTime end) {
        return UserTier.builder()
                .id(UUID.randomUUID())
                .tier(tier)
                .paymentOrder(order)
                .startTime(start)
                .endTime(end)
                .isActive(true)
                .build();
    }
}
