package com.historytalk.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.PaymentTransaction;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.PaymentTransactionRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.model.webhooks.WebhookData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NFR N5 – Payment reliability: replaying the same PAID webhook must fulfil the order exactly once.
 * Repositories are mocked statefully so a second call "sees" rows written by the first call,
 * like a real database would.
 */
@ExtendWith(MockitoExtension.class)
class PaymentWebhookReplayTest {

    private static final long ORDER_CODE = 2026110901L;
    private static final int AMOUNT = 49_000;
    private static final int TIER_TOKENS = 100;
    private static final int REPLAY_COUNT = 5;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private UserTierRepository userTierRepository;
    @Mock
    private UserRepository userRepository;

    private PaymentWebhookService webhookService;
    private PaymentOrder order;
    private User user;

    private final List<UserTier> savedUserTiers = new ArrayList<>();
    private final List<PaymentTransaction> savedTransactions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        user = User.builder()
                .uid(UUID.randomUUID())
                .userName("customer")
                .email("customer@example.com")
                .role(UserRole.CUSTOMER)
                .token(0)
                .build();
        Tier tier = Tier.builder()
                .tierId(UUID.randomUUID())
                .title("plus")
                .amount(AMOUNT)
                .noMonth(1)
                .limitedToken(TIER_TOKENS)
                .build();
        order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(ORDER_CODE)
                .user(user)
                .tier(tier)
                .amount(AMOUNT)
                .status(PaymentOrderStatus.PENDING)
                .fulfillmentStatus(PaymentFulfillmentStatus.PENDING)
                .build();

        when(paymentOrderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        lenient().when(paymentTransactionRepository.existsByReference(anyString())).thenAnswer(inv ->
                savedTransactions.stream().anyMatch(t -> inv.getArgument(0).equals(t.getReference())));
        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            savedTransactions.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        lenient().when(userTierRepository.findByPaymentOrder_OrderId(order.getOrderId())).thenAnswer(inv ->
                savedUserTiers.stream().findFirst());
        lenient().when(userTierRepository.save(any(UserTier.class))).thenAnswer(inv -> {
            savedUserTiers.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentFulfillmentService fulfillmentService =
                new PaymentFulfillmentService(paymentOrderRepository, userTierRepository, userRepository);
        webhookService = new PaymentWebhookService(
                paymentOrderRepository, paymentTransactionRepository, fulfillmentService, new ObjectMapper());
    }

    @Test
    void replayingPaidWebhookFulfilsOrderExactlyOnce() {
        WebhookData paid = paidWebhook();

        for (int i = 0; i < REPLAY_COUNT; i++) {
            webhookService.handlePayOSWebhook(paid);
        }

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(order.getFulfillmentStatus()).isEqualTo(PaymentFulfillmentStatus.FULFILLED);
        assertThat(order.getFulfillmentAttempts()).isEqualTo(1);
        assertThat(savedUserTiers).hasSize(1);
        assertThat(savedTransactions).hasSize(1);
        assertThat(user.getToken()).isEqualTo(TIER_TOKENS);
        verify(userTierRepository, times(1)).save(any(UserTier.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void cancelWebhookAfterPaidDoesNotRevertOrder() {
        webhookService.handlePayOSWebhook(paidWebhook());

        WebhookData cancelled = paidWebhook();
        cancelled.setCode("01");
        webhookService.handlePayOSWebhook(cancelled);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        assertThat(savedUserTiers).hasSize(1);
        assertThat(user.getToken()).isEqualTo(TIER_TOKENS);
    }

    private WebhookData paidWebhook() {
        return WebhookData.builder()
                .orderCode(ORDER_CODE)
                .amount((long) AMOUNT)
                .code("00")
                .desc("success")
                .description("HistoryTalk plus")
                .accountNumber("0000000000")
                .reference("FT26110900001")
                .transactionDateTime("2026-11-09 10:00:00")
                .currency("VND")
                .paymentLinkId("test-link")
                .counterAccountBankId("")
                .counterAccountBankName("")
                .counterAccountName("")
                .counterAccountNumber("")
                .virtualAccountName("")
                .virtualAccountNumber("")
                .build();
    }
}
