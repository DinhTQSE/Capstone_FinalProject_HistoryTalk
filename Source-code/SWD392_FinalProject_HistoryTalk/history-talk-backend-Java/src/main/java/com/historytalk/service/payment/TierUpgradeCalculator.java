package com.historytalk.service.payment;

import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Ranks tiers and prices a mid-period upgrade.
 * <p>
 * The unused part of the current paid subscription is converted into a credit:
 * {@code credit = floor(value * remainingSeconds / totalSeconds)}, where value is what the
 * subscription was worth when bought (amount paid + credit applied at that purchase).
 * The user pays {@code target.amount - credit}.
 */
@Component
public class TierUpgradeCalculator {

    /** Level of a tier by title: free=0, plus=1, pro=2; unknown titles are -1 (not upgradable). */
    public int rank(Tier tier) {
        if (tier == null || tier.getTitle() == null) {
            return -1;
        }
        return switch (tier.getTitle().trim().toLowerCase(Locale.ROOT)) {
            case "free" -> 0;
            case "plus" -> 1;
            case "pro" -> 2;
            default -> -1;
        };
    }

    /** A higher level, or the same level with a longer duration. Downgrades are never upgrades. */
    public boolean isUpgrade(Tier current, Tier target) {
        int currentRank = rank(current);
        int targetRank = rank(target);
        if (currentRank < 0 || targetRank < 0) {
            return false;
        }
        if (targetRank != currentRank) {
            return targetRank > currentRank;
        }
        return months(target) > months(current);
    }

    /** What the subscription was worth when bought: amount paid + upgrade credit applied to it. */
    public int subscriptionValue(UserTier sub) {
        PaymentOrder order = sub.getPaymentOrder();
        if (order != null && order.getAmount() != null) {
            int credit = order.getUpgradeCreditAmount() == null ? 0 : order.getUpgradeCreditAmount();
            return order.getAmount() + credit;
        }
        return sub.getTier() == null || sub.getTier().getAmount() == null ? 0 : sub.getTier().getAmount();
    }

    /** Prorated value of the unused part of the subscription at {@code now}, rounded down to 1 VND. */
    public int credit(UserTier sub, LocalDateTime now) {
        long total = Duration.between(sub.getStartTime(), sub.getEndTime()).getSeconds();
        if (total <= 0) {
            return 0;
        }
        long remaining = Duration.between(now, sub.getEndTime()).getSeconds();
        remaining = Math.max(0, Math.min(remaining, total));
        return (int) (subscriptionValue(sub) * remaining / total);
    }

    /**
     * Prices buying {@code target}. With no current paid subscription it is a plain full-price purchase.
     *
     * @throws InvalidRequestException when the target is not an upgrade, or the credit already covers it
     */
    public UpgradeQuote quote(UserTier currentPaidSub, Tier target, LocalDateTime now) {
        int price = target.getAmount() == null ? 0 : target.getAmount();
        if (currentPaidSub == null) {
            return new UpgradeQuote(null, price, 0, price);
        }

        Tier current = currentPaidSub.getTier();
        if (!isUpgrade(current, target)) {
            throw new InvalidRequestException(
                    "You can only upgrade to a higher plan (or a longer plan of the same level). Current plan: '"
                            + (current != null ? current.getTitle() : "unknown") + "'.");
        }

        int credit = credit(currentPaidSub, now);
        if (credit >= price) {
            throw new InvalidRequestException(
                    "Your remaining credit of " + credit + " VND already covers '" + target.getTitle()
                            + "'. Please choose a longer plan.");
        }
        return new UpgradeQuote(currentPaidSub, price, credit, price - credit);
    }

    private int months(Tier tier) {
        return tier.getNoMonth() == null ? 0 : tier.getNoMonth();
    }
}
