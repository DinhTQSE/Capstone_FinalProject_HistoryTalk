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
import java.util.List;
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
    void fulfillPaidOrderSucceedsWhenUserAlreadyHasActiveSubscription() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        User user = User.builder()
                .uid(userId)
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(1500)
                .build();
        Tier tier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("plus")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(5000)
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
        when(userTierRepository.save(any(UserTier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean fulfilled = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(fulfilled).isTrue();
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FULFILLED);
        assertThat(user.getToken()).isEqualTo(6500);
        verify(userTierRepository).save(any(UserTier.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void fulfillPaidOrderMarksFailedWhenOrderUserIsMissing() {
        UUID orderId = UUID.randomUUID();
        Tier tier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("plus")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(5000)
                .build();
        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .orderCode(111222334L)
                .user(null)
                .tier(tier)
                .amount(49_000)
                .status(PaymentOrderStatus.PAID)
                .fulfillmentStatus(PaymentFulfillmentStatus.PENDING)
                .build();

        when(paymentOrderRepository.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTierRepository.findByPaymentOrder_OrderId(orderId)).thenReturn(Optional.empty());

        boolean fulfilled = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(fulfilled).isFalse();
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FAILED);
        assertThat(order.getFulfillmentError()).contains("Order user is missing or deleted");
        verify(userTierRepository, never()).save(any(UserTier.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void upgradeDeactivatesEveryOtherActivePaidSubscription() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().uid(userId).userName("customer").email("c@example.com")
                .role(UserRole.CUSTOMER).token(3_000).build();
        LocalDateTime start = LocalDateTime.now().minusDays(10);
        UserTier freeSub = activeSub(user, tierOf("free", 0, 1, 1_500), start);
        UserTier plusSub = activeSub(user, tierOf("plus", 49_000, 1, 5_000), start);
        UserTier proMonthSub = activeSub(user, tierOf("pro", 99_000, 1, 10_000), start);
        Tier proYear = tierOf("pro", 999_000, 12, 10_000);
        PaymentOrder order = upgradeOrder(user, proYear, plusSub, 444555666L);

        stubFulfillment(order);
        when(userTierRepository.findAllActiveByUid(eq(userId), any()))
                .thenReturn(List.of(freeSub, plusSub, proMonthSub));

        boolean ok = paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode());

        assertThat(ok).isTrue();
        assertThat(plusSub.getIsActive()).isFalse();
        assertThat(proMonthSub.getIsActive()).isFalse();
        assertThat(plusSub.getEndTime()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(freeSub.getIsActive()).isTrue();
        // keeps the 3,000 already held (incl. today's old-plan tokens) + full 10,000 of the new plan
        assertThat(user.getToken()).isEqualTo(13_000);

        ArgumentCaptor<UserTier> created = ArgumentCaptor.forClass(UserTier.class);
        verify(userTierRepository).save(created.capture());
        assertThat(created.getValue().getTier()).isSameAs(proYear);
        assertThat(created.getValue().getIsActive()).isTrue();
        assertThat(created.getValue().getEndTime()).isAfter(LocalDateTime.now().plusMonths(11));
    }

    @Test
    void upgradeStillFulfillsWhenReplacedSubscriptionAlreadyExpired() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().uid(userId).userName("customer").email("c@example.com")
                .role(UserRole.CUSTOMER).token(0).build();
        UserTier freeSub = activeSub(user, tierOf("free", 0, 1, 1_500), LocalDateTime.now().minusDays(10));
        UserTier expiredPlus = activeSub(user, tierOf("plus", 49_000, 1, 5_000), LocalDateTime.now().minusDays(31));
        expiredPlus.setIsActive(false);
        PaymentOrder order = upgradeOrder(user, tierOf("pro", 99_000, 1, 10_000), expiredPlus, 444555667L);

        stubFulfillment(order);
        when(userTierRepository.findAllActiveByUid(eq(userId), any())).thenReturn(List.of(freeSub));

        assertThat(paymentFulfillmentService.fulfillPaidOrder(order.getOrderCode())).isTrue();
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FULFILLED);
        assertThat(freeSub.getIsActive()).isTrue();
        assertThat(user.getToken()).isEqualTo(10_000);
    }

    private void stubFulfillment(PaymentOrder order) {
        when(paymentOrderRepository.findByOrderCodeForUpdate(order.getOrderCode())).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTierRepository.findByPaymentOrder_OrderId(order.getOrderId())).thenReturn(Optional.empty());
        when(userTierRepository.save(any(UserTier.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Tier tierOf(String title, int amount, int noMonth, int limitedToken) {
        return Tier.builder().tierId(UUID.randomUUID()).title(title).amount(amount)
                .noMonth(noMonth).limitedToken(limitedToken).build();
    }

    private static UserTier activeSub(User user, Tier tier, LocalDateTime start) {
        return UserTier.builder().id(UUID.randomUUID()).user(user).tier(tier)
                .startTime(start).endTime(start.plusMonths(tier.getNoMonth())).isActive(true).build();
    }

    private static PaymentOrder upgradeOrder(User user, Tier tier, UserTier replaced, long orderCode) {
        return PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(orderCode)
                .user(user)
                .tier(tier)
                .amount(tier.getAmount() - 20_000)
                .upgradeCreditAmount(20_000)
                .upgradeFromUserTier(replaced)
                .status(PaymentOrderStatus.PAID)
                .fulfillmentStatus(PaymentFulfillmentStatus.PENDING)
                .build();
    }
}
