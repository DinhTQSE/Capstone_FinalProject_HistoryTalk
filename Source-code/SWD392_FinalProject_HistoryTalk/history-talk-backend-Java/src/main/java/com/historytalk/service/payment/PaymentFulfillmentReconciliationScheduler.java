package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.repository.payment.PaymentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFulfillmentReconciliationScheduler {

    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 10;
    private static final int PROCESSING_STALE_MINUTES = 10;

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;

    @Scheduled(fixedDelay = 60_000)
    public void retryUnfulfilledPaidOrders() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(PROCESSING_STALE_MINUTES);
        List<PaymentOrder> orders = paymentOrderRepository.findOrdersNeedingFulfillmentRetry(
                PaymentOrderStatus.PAID,
                List.of(PaymentFulfillmentStatus.PENDING, PaymentFulfillmentStatus.FAILED),
                PaymentFulfillmentStatus.PROCESSING,
                staleBefore,
                MAX_ATTEMPTS,
                PageRequest.of(0, BATCH_SIZE)
        );

        if (orders.isEmpty()) {
            return;
        }

        log.info("Payment fulfillment reconciliation found {} order(s)", orders.size());

        for (PaymentOrder order : orders) {
            try {
                paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());
            } catch (Exception ex) {
                log.warn("Payment fulfillment retry failed for order {}: {}",
                        order.getOrderCode(), ex.getMessage());
            }
        }
    }
}
