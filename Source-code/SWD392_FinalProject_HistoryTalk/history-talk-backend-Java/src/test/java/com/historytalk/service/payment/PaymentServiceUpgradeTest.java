package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.UpgradeQuoteResponse;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import vn.payos.PayOS;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceUpgradeTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;
    @Mock
    private PayOSConfig payOSConfig;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TierRepository tierRepository;
    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private UserTierRepository userTierRepository;
    @Spy
    private TierUpgradeCalculator tierUpgradeCalculator = new TierUpgradeCalculator();

    @InjectMocks
    private PaymentService service;

    private final UUID uid = UUID.randomUUID();
    private User user;
    private Tier plusTier;
    private Tier proTier;

    @BeforeEach
    void setUp() {
        user = User.builder().uid(uid).userName("customer").email("c@example.com").role(UserRole.CUSTOMER).build();
        plusTier = tier("plus", 49_000, 1);
        proTier = tier("pro", 99_000, 1);
        when(payOSConfig.getReturnUrl()).thenReturn("http://localhost/return");
        when(payOSConfig.getCancelUrl()).thenReturn("http://localhost/cancel");
        when(userRepository.findById(uid)).thenReturn(Optional.of(user));
        when(tierRepository.findById(plusTier.getTierId())).thenReturn(Optional.of(plusTier));
        when(tierRepository.findById(proTier.getTierId())).thenReturn(Optional.of(proTier));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            PaymentOrder o = inv.getArgument(0);
            if (o.getOrderId() == null) {
                o.setOrderId(UUID.randomUUID());
            }
            return o;
        });
    }

    @Test
    void checkoutForHigherTierCreatesDiscountedOrderLinkedToReplacedSubscription() throws Exception {
        UserTier plusSub = activeHalfway(plusTier, 49_000);
        when(userTierRepository.findCurrentActiveByUid(eq(uid), any())).thenReturn(Optional.of(plusSub));

        CreatePaymentResponse res = service.createPayOSCheckout(uid, proTier.getTierId().toString());

        ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository, atLeastOnce()).save(saved.capture());
        PaymentOrder order = saved.getValue();
        assertThat(order.getUpgradeCreditAmount()).isCloseTo(24_500, within(1));
        assertThat(order.getAmount()).isCloseTo(74_500, within(1));
        assertThat(order.getAmount() + order.getUpgradeCreditAmount()).isEqualTo(99_000);
        assertThat(order.getUpgradeFromUserTier()).isSameAs(plusSub);
        assertThat(res.getAmount()).isEqualTo(order.getAmount());
        assertThat(res.getOriginalAmount()).isEqualTo(99_000);
        assertThat(res.getUpgradeCreditAmount()).isEqualTo(order.getUpgradeCreditAmount());
    }

    @Test
    void checkoutForLowerTierIsRejectedAndCreatesNoOrder() {
        UserTier proSub = activeHalfway(proTier, 99_000);
        when(userTierRepository.findCurrentActiveByUid(eq(uid), any())).thenReturn(Optional.of(proSub));

        assertThatThrownBy(() -> service.createPayOSCheckout(uid, plusTier.getTierId().toString()))
                .isInstanceOf(InvalidRequestException.class);
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    void checkoutWithoutPaidSubscriptionChargesFullPrice() throws Exception {
        UserTier freeSub = activeHalfway(tier("free", 0, 1), 0);
        freeSub.setPaymentOrder(null);
        when(userTierRepository.findCurrentActiveByUid(eq(uid), any())).thenReturn(Optional.of(freeSub));

        CreatePaymentResponse res = service.createPayOSCheckout(uid, proTier.getTierId().toString());

        ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(paymentOrderRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getUpgradeFromUserTier()).isNull();
        assertThat(res.getAmount()).isEqualTo(99_000);
        assertThat(res.getUpgradeCreditAmount()).isZero();
    }

    @Test
    void upgradeQuoteReturnsSameNumbersWithoutSavingAnything() {
        UserTier plusSub = activeHalfway(plusTier, 49_000);
        when(userTierRepository.findCurrentActiveByUid(eq(uid), any())).thenReturn(Optional.of(plusSub));

        UpgradeQuoteResponse q = service.getUpgradeQuote(uid, proTier.getTierId().toString());

        assertThat(q.getUpgrade()).isTrue();
        assertThat(q.getCreditAmount()).isCloseTo(24_500, within(1));
        assertThat(q.getAmountToPay()).isCloseTo(74_500, within(1));
        assertThat(q.getOriginalAmount()).isEqualTo(99_000);
        assertThat(q.getCurrentTierTitle()).isEqualTo("plus");
        assertThat(q.getTargetTierTitle()).isEqualTo("pro");
        verify(paymentOrderRepository, never()).save(any());
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

    /** Active subscription bought for {@code paid} VND, exactly half-way through a 30-day period. */
    private UserTier activeHalfway(Tier tier, int paid) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return UserTier.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tier(tier)
                .paymentOrder(PaymentOrder.builder().orderId(UUID.randomUUID()).amount(paid).build())
                .startTime(now.minusDays(15))
                .endTime(now.plusDays(15))
                .isActive(true)
                .build();
    }
}
