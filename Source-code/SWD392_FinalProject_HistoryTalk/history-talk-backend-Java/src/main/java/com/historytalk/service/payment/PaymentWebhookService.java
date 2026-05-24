package com.historytalk.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.enums.PaymentTransactionStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.PaymentTransaction;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.PaymentTransactionRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserTierRepository userTierRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handlePayOSPaidWebhook(WebhookData data) {
        PaymentOrder order = paymentOrderRepository
                .findByOrderCodeForUpdate(data.getOrderCode())
                .orElseThrow(() -> new RuntimeException("Payment order not found"));

        if ("PAID".equals(order.getStatus())) {
            return;
        }

        validatePayOSData(order, data);

        saveTransactionIfNotExists(order, data);

        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        upgradeUserTier(order);
    }

    private void validatePayOSData(PaymentOrder order, WebhookData data) {
        if (data.getAmount() == null) {
            throw new RuntimeException("PayOS amount is null");
        }

        if (!data.getAmount().equals(order.getAmount().longValue())) {
            throw new RuntimeException("Amount mismatch");
        }

        if (order.getPaymentLinkId() != null
                && data.getPaymentLinkId() != null
                && !order.getPaymentLinkId().equals(data.getPaymentLinkId())) {
            throw new RuntimeException("Payment link mismatch");
        }
    }

    private void saveTransactionIfNotExists(PaymentOrder order, WebhookData data) {
        if (data.getReference() != null
                && paymentTransactionRepository.existsByReference(data.getReference())) {
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(data);

            PaymentTransaction transaction = PaymentTransaction.builder()
                    .paymentOrder(order)
                    .amount(data.getAmount().intValue())
                    .paymentLinkId(data.getPaymentLinkId())
                    .payload(payloadJson)
                    .status(PaymentTransactionStatus.SUCCESS)
                    .transactionDate(parsePayOSDate(data.getTransactionDateTime()))
                    .reference(data.getReference())
                    .isActive(true)
                    .build();

            paymentTransactionRepository.save(transaction);
        } catch (Exception e) {
            throw new RuntimeException("Cannot save payment transaction", e);
        }
    }

    private LocalDateTime parsePayOSDate(String transactionDateTime) {
        if (transactionDateTime == null || transactionDateTime.isBlank()) {
            return LocalDateTime.now();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(transactionDateTime, formatter);
    }

    private void upgradeUserTier(PaymentOrder order) {
        User user = order.getUser();
        Tier newTier = order.getTier();

        LocalDateTime now = LocalDateTime.now();

        Optional<UserTier> activeOpt =
                userTierRepository.findActiveByUidForUpdate(user.getUid());

        if (activeOpt.isPresent()) {
            UserTier current = activeOpt.get();

            /*
             * Nếu user còn hạn, cộng tiếp từ endTime hiện tại.
             * Nếu đã hết hạn, tính từ now.
             */
            LocalDateTime baseTime = current.getEndTime().isAfter(now)
                    ? current.getEndTime()
                    : now;

            current.setIsActive(false);

            UserTier next = UserTier.builder()
                    .user(user)
                    .tier(newTier)
                    .startTime(baseTime)
                    .endTime(baseTime.plusMonths(newTier.getNoMonth()))
                    .isActive(true)
                    .build();

            userTierRepository.save(current);
            userTierRepository.save(next);
        } else {
            UserTier userTier = UserTier.builder()
                    .user(user)
                    .tier(newTier)
                    .startTime(now)
                    .endTime(now.plusMonths(newTier.getNoMonth()))
                    .isActive(true)
                    .build();

            userTierRepository.save(userTier);
        }

        /*
         * Nếu User entity của bạn có field Tier:
         * private Tier tier;
         *
         * thì mở dòng dưới.
         */
        user.setTier(newTier);

        /*
         * Nếu User entity của bạn có token/currentToken:
         * user.setToken(newTier.getLimitedToken());
         */

        userRepository.save(user);
    }
}
