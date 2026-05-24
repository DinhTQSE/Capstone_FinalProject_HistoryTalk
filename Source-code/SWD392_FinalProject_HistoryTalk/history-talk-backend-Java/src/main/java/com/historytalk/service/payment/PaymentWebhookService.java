package com.historytalk.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.enums.PaymentOrderStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserTierRepository userTierRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Main dispatcher — routes PayOS webhook events by their code field.
     * PayOS signature verification is already done by payOS.webhooks().verify()
     * in the controller using the configured checksumKey, so no additional
     * HMAC check is needed here.
     *
     * PayOS event codes:
     *   "00" → payment confirmed (PAID)
     *   "01" → payment cancelled by user
     *   "02" → payment link expired on PayOS side
     *   anything else → treat as unhandled / log and ignore
     */
    @Transactional
    public void handlePayOSWebhook(WebhookData data) {
        String code = data.getCode();
        log.info("Received PayOS webhook: orderCode={}, code={}", data.getOrderCode(), code);

        if ("00".equals(code)) {
            handlePaid(data);
        } else if ("01".equals(code) || "02".equals(code)) {
            handleCancelled(data, code);
        } else {
            log.warn("Unhandled PayOS webhook code={} for orderCode={}", code, data.getOrderCode());
        }
    }

    // -------------------------------------------------------------------------
    // PAID
    // -------------------------------------------------------------------------

    private void handlePaid(WebhookData data) {
        PaymentOrder order = lockOrder(data.getOrderCode());

        if (PaymentOrderStatus.PAID == order.getStatus()) {
            log.info("Order {} already PAID — skipping duplicate webhook", order.getOrderCode());
            return;
        }

        validatePayOSData(order, data);
        saveTransactionIfNotExists(order, data, PaymentTransactionStatus.SUCCESS);

        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        paymentOrderRepository.save(order);

        upgradeUserTier(order);
        log.info("Order {} successfully marked as PAID", order.getOrderCode());
    }

    // -------------------------------------------------------------------------
    // CANCELLED / EXPIRED (from PayOS side)
    // -------------------------------------------------------------------------

    private void handleCancelled(WebhookData data, String code) {
        PaymentOrder order = lockOrder(data.getOrderCode());

        if (PaymentOrderStatus.PAID == order.getStatus()) {
            log.warn("Order {} is already PAID — ignoring cancellation webhook (code={})",
                    order.getOrderCode(), code);
            return;
        }

        if (PaymentOrderStatus.CANCELLED == order.getStatus()
                || PaymentOrderStatus.EXPIRED == order.getStatus()) {
            log.info("Order {} already terminal (status={}) — skipping", order.getOrderCode(), order.getStatus());
            return;
        }

        // code "02" = PayOS-side expiry; "01" = user cancelled
        PaymentOrderStatus newStatus = "02".equals(code)
                ? PaymentOrderStatus.EXPIRED
                : PaymentOrderStatus.CANCELLED;

        saveTransactionIfNotExists(order, data, PaymentTransactionStatus.FAILED);

        order.setStatus(newStatus);
        paymentOrderRepository.save(order);
        log.info("Order {} set to {} (PayOS code={})", order.getOrderCode(), newStatus, code);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PaymentOrder lockOrder(Long orderCode) {
        return paymentOrderRepository
                .findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + orderCode));
    }

    private void validatePayOSData(PaymentOrder order, WebhookData data) {
        if (data.getAmount() == null) {
            throw new RuntimeException("PayOS amount is null for order " + order.getOrderCode());
        }

        if (!data.getAmount().equals(order.getAmount().longValue())) {
            throw new RuntimeException(
                    "Amount mismatch for order " + order.getOrderCode()
                            + ": expected " + order.getAmount() + ", got " + data.getAmount());
        }

        if (order.getPaymentLinkId() != null
                && data.getPaymentLinkId() != null
                && !order.getPaymentLinkId().equals(data.getPaymentLinkId())) {
            throw new RuntimeException("Payment link mismatch for order " + order.getOrderCode());
        }
    }

    private void saveTransactionIfNotExists(PaymentOrder order, WebhookData data,
                                            PaymentTransactionStatus txStatus) {
        if (data.getReference() != null
                && paymentTransactionRepository.existsByReference(data.getReference())) {
            log.info("Transaction with reference {} already exists — skipping", data.getReference());
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(data);

            PaymentTransaction transaction = PaymentTransaction.builder()
                    .paymentOrder(order)
                    .amount(data.getAmount() != null ? data.getAmount().intValue() : 0)
                    .paymentLinkId(data.getPaymentLinkId())
                    .payload(payloadJson)
                    .status(txStatus)
                    .transactionDate(parsePayOSDate(data.getTransactionDateTime()))
                    .reference(data.getReference())
                    .isActive(true)
                    .build();

            paymentTransactionRepository.save(transaction);
        } catch (Exception e) {
            throw new RuntimeException("Cannot save payment transaction for order " + order.getOrderCode(), e);
        }
    }

    private LocalDateTime parsePayOSDate(String transactionDateTime) {
        if (transactionDateTime == null || transactionDateTime.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(transactionDateTime, formatter);
        } catch (Exception e) {
            log.warn("Could not parse PayOS transaction date '{}', using now", transactionDateTime);
            return LocalDateTime.now();
        }
    }

    private void upgradeUserTier(PaymentOrder order) {
        User user = order.getUser();
        Tier newTier = order.getTier();
        LocalDateTime now = LocalDateTime.now();

        Optional<UserTier> activeOpt = userTierRepository.findActiveByUidForUpdate(user.getUid());

        if (activeOpt.isPresent()) {
            UserTier current = activeOpt.get();

            // If user still has time left, extend from their current end time.
            // If they have lapsed, start from now.
            LocalDateTime baseTime = current.getEndTime().isAfter(now)
                    ? current.getEndTime()
                    : now;

            current.setIsActive(false);
            userTierRepository.save(current);

            UserTier next = UserTier.builder()
                    .user(user)
                    .tier(newTier)
                    .startTime(baseTime)
                    .endTime(baseTime.plusMonths(newTier.getNoMonth()))
                    .isActive(true)
                    .build();
            userTierRepository.save(next);

            log.info("Extended tier for user {} → {} until {}", user.getUid(), newTier.getTitle(), next.getEndTime());
        } else {
            UserTier userTier = UserTier.builder()
                    .user(user)
                    .tier(newTier)
                    .startTime(now)
                    .endTime(now.plusMonths(newTier.getNoMonth()))
                    .isActive(true)
                    .build();
            userTierRepository.save(userTier);

            log.info("Created new tier for user {} → {} until {}",
                    user.getUid(), newTier.getTitle(), userTier.getEndTime());
        }

        // Update user's current tier reference
        user.setTier(newTier);

        // Replace token balance with the new tier's allowance (resolved decision: replace, not top-up)
        user.setToken(newTier.getLimitedToken());

        userRepository.save(user);
        log.info("User {} tier → '{}', token reset to {}", user.getUid(), newTier.getTitle(), newTier.getLimitedToken());
    }
}
