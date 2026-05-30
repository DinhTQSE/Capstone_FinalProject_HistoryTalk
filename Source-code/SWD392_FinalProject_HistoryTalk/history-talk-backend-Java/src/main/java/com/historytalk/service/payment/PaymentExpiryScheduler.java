package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that periodically handles two expiry concerns:
 *
 * 1. PENDING payment orders whose expiredAt has passed → marked EXPIRED.
 *    PayOS does not send a webhook when a payment link simply times out from
 *    inactivity (only if the user explicitly cancels). This is a safety net.
 *
 * 2. PAID UserTier subscriptions whose endTime has passed → isActive set to false.
 *    This ensures the subscription guard and profile mapper always see the correct
 *    active tier. Free-tier UserTier rows (tier.amount = 0) are never touched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserTierRepository userTierRepository;

    /**
     * Marks PENDING payment orders as EXPIRED when their expiredAt time has passed.
     * Runs every 60 seconds.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirePendingOrders() {
        LocalDateTime now = LocalDateTime.now();
        log.info("Payment order expiry scheduler running. now={}", now);

        List<PaymentOrder> expiredOrders = paymentOrderRepository
                .findExpiredPendingOrders(PaymentOrderStatus.PENDING, now);

        log.info("Payment order expiry scheduler found {} expired PENDING order(s)", expiredOrders.size());

        if (expiredOrders.isEmpty()) {
            return;
        }

        for (PaymentOrder order : expiredOrders) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            log.info("Expired payment order: orderCode={}, expiredAt={}",
                    order.getOrderCode(), order.getExpiredAt());
        }

        paymentOrderRepository.saveAll(expiredOrders);
    }

    /**
     * Flips isActive = false for any paid UserTier subscriptions whose endTime has passed.
     * Free-tier rows (tier.amount = 0) are intentionally excluded — they never expire.
     * After this runs, the next call to findCurrentActiveByUid automatically falls back
     * to the user's free-tier row without any additional inserts.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void expireUserTierSubscriptions() {
        LocalDateTime now = LocalDateTime.now();
        List<UserTier> expired = userTierRepository.findExpiredPaidSubscriptions(now);

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(ut -> ut.setIsActive(false));
        userTierRepository.saveAll(expired);
        log.info("Marked {} paid UserTier subscription(s) as expired", expired.size());
    }
}
