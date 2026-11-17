package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSchedulersTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserTierRepository userTierRepository;

    @Mock
    private PaymentFulfillmentService paymentFulfillmentService;

    @Test
    @DisplayName("WF-05 / F-10: PaymentExpiryScheduler marks expired PENDING orders as EXPIRED and deactivates expired UserTiers")
    void paymentExpiryScheduler_ExpiresOrdersAndSubscriptions() {
        PaymentExpiryScheduler expiryScheduler = new PaymentExpiryScheduler(
                paymentOrderRepository, userTierRepository);

        PaymentOrder pendingOrder = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(2026111299L)
                .status(PaymentOrderStatus.PENDING)
                .expiredAt(LocalDateTime.now().minusMinutes(5))
                .build();

        UserTier expiredSubscription = UserTier.builder()
                .isActive(true)
                .endTime(LocalDateTime.now().minusDays(1))
                .build();

        when(paymentOrderRepository.findExpiredPendingOrders(eq(PaymentOrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(pendingOrder));
        when(userTierRepository.findExpiredPaidSubscriptions(any(LocalDateTime.class)))
                .thenReturn(List.of(expiredSubscription));

        expiryScheduler.expirePendingOrders();
        expiryScheduler.expireUserTierSubscriptions();

        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.EXPIRED);
        assertThat(expiredSubscription.getIsActive()).isFalse();
        verify(paymentOrderRepository).saveAll(List.of(pendingOrder));
        verify(userTierRepository).saveAll(List.of(expiredSubscription));
    }

    @Test
    @DisplayName("WF-05 / N5: PaymentFulfillmentReconciliationScheduler retries unfulfilled PAID orders")
    void reconciliationScheduler_RetriesUnfulfilledPaidOrders() {
        PaymentFulfillmentReconciliationScheduler reconciliationScheduler =
                new PaymentFulfillmentReconciliationScheduler(paymentOrderRepository, paymentFulfillmentService);

        PaymentOrder paidUnfulfilled = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(2026111288L)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findOrdersNeedingFulfillmentRetry(
                eq(PaymentOrderStatus.PAID), any(), any(), any(LocalDateTime.class), anyInt(), any()))
                .thenReturn(List.of(paidUnfulfilled));

        reconciliationScheduler.retryUnfulfilledPaidOrders();

        verify(paymentFulfillmentService).fulfillPaidOrder(2026111288L);
    }
}
