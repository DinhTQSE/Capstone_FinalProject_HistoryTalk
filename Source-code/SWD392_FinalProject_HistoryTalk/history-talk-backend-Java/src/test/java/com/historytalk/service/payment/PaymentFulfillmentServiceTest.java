package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFulfillmentServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserTierRepository userTierRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentFulfillmentService paymentFulfillmentService;

    @Test
    void fulfillPaidOrderCreatesSubscriptionAndResetsTokens() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(0)
                .build();
        Tier tier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("plus")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(100)
                .build();
        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .orderCode(123456789L)
                .user(user)
                .tier(tier)
                .amount(49_000)
                .status(PaymentOrderStatus.PAID)
                .fulfillmentStatus(PaymentFulfillmentStatus.PENDING)
                .build();

        when(paymentOrderRepository.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTierRepository.findByPaymentOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(userTierRepository.findCurrentActiveByUidForUpdate(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(userTierRepository.save(any(UserTier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean fulfilled = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(fulfilled).isTrue();
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FULFILLED);
        assertThat(order.getFulfilledAt()).isNotNull();
        assertThat(order.getFulfillmentAttempts()).isEqualTo(1);
        assertThat(user.getToken()).isEqualTo(100);
        assertThat(user.getLastTokenResetAt()).isNotNull();

        ArgumentCaptor<UserTier> userTierCaptor = ArgumentCaptor.forClass(UserTier.class);
        verify(userTierRepository).save(userTierCaptor.capture());
        assertThat(userTierCaptor.getValue().getPaymentOrder()).isSameAs(order);
        assertThat(userTierCaptor.getValue().getUser()).isSameAs(user);
        assertThat(userTierCaptor.getValue().getTier()).isSameAs(tier);
    }

    @Test
    void fulfillPaidOrderSkipsAlreadyFulfilledOrder() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(987654321L)
                .status(PaymentOrderStatus.PAID)
                .fulfillmentStatus(PaymentFulfillmentStatus.FULFILLED)
                .fulfilledAt(LocalDateTime.now())
                .build();

        when(paymentOrderRepository.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));

        boolean fulfilled = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(fulfilled).isTrue();
        verify(userTierRepository, never()).save(any(UserTier.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void fulfillPaidOrderMarksFailedWhenUserAlreadyHasDifferentPaidSubscription() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(0)
                .build();
        Tier tier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("plus")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(100)
                .build();
        Tier activeTier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("pro")
                .amount(99_000)
                .noMonth(1)
                .limitedToken(999)
                .build();
        UserTier activePaid = UserTier.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tier(activeTier)
                .startTime(LocalDateTime.now().minusDays(1))
                .endTime(LocalDateTime.now().plusDays(29))
                .isActive(true)
                .build();
        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .orderCode(111222333L)
                .user(user)
                .tier(tier)
                .amount(49_000)
                .status(PaymentOrderStatus.PAID)
                .fulfillmentStatus(PaymentFulfillmentStatus.PENDING)
                .build();

        when(paymentOrderRepository.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTierRepository.findByPaymentOrder_OrderId(orderId)).thenReturn(Optional.empty());
        when(userTierRepository.findCurrentActiveByUidForUpdate(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activePaid));

        boolean fulfilled = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(fulfilled).isFalse();
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FAILED);
        assertThat(order.getFulfillmentError()).contains("active paid subscription");
        verify(userTierRepository, never()).save(any(UserTier.class));
        verify(userRepository, never()).save(any(User.class));
    }
}
