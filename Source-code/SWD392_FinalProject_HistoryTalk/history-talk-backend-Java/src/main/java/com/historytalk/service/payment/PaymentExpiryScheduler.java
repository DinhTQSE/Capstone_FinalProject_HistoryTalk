package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.repository.payment.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler that periodically checks for PENDING payment orders whose
 * expiredAt has passed and marks them as EXPIRED.
 *
 * PayOS will not send a webhook when a payment link simply times out from
 * inactivity (only if the user explicitly cancels). This scheduler is
 * therefore needed as a safety net to keep order statuses accurate.
 *
 * Runs every 60 seconds by default.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentOrderRepository paymentOrderRepository;

    /**
     * Marks all PENDING orders with expiredAt <= now as EXPIRED.
     * fixedDelay (not fixedRate) ensures the next run starts only after the
     * current run finishes, preventing overlapping executions.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expirePendingOrders() {
        LocalDateTime now = LocalDateTime.now();

        List<PaymentOrder> expiredOrders = paymentOrderRepository
                .findExpiredPendingOrders(PaymentOrderStatus.PENDING, now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Expiry scheduler: found {} PENDING order(s) to expire", expiredOrders.size());

        for (PaymentOrder order : expiredOrders) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            paymentOrderRepository.save(order);
            log.info("Expired order: orderCode={}, expiredAt={}", order.getOrderCode(), order.getExpiredAt());
        }
    }
}
