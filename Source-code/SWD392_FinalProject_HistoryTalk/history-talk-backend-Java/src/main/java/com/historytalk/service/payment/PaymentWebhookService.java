package com.historytalk.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.PaymentTransactionStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.PaymentTransaction;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.model.webhooks.WebhookData;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentFulfillmentService paymentFulfillmentService;
    private final ObjectMapper objectMapper;

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

    private void handlePaid(WebhookData data) {
        PaymentOrder order = lockOrder(data.getOrderCode());

        validatePayOSData(order, data);
        saveTransactionIfNotExists(order, data, PaymentTransactionStatus.SUCCESS);

        if (PaymentOrderStatus.PAID != order.getStatus()) {
            order.setStatus(PaymentOrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
        } else {
            log.info("Order {} already marked PAID; checking fulfillment status={}",
                    order.getOrderCode(), order.getFulfillmentStatus());
        }
        paymentOrderRepository.save(order);

        if (order.getFulfillmentStatus() != PaymentFulfillmentStatus.FULFILLED) {
            boolean fulfilled = paymentFulfillmentService.fulfillLockedPaidOrder(order);
            if (!fulfilled) {
                log.warn("Order {} is PAID but fulfillment did not complete; scheduler will retry",
                        order.getOrderCode());
            }
        }

        log.info("Order {} successfully processed as PAID", order.getOrderCode());
    }

    private void handleCancelled(WebhookData data, String code) {
        PaymentOrder order = lockOrder(data.getOrderCode());

        if (PaymentOrderStatus.PAID == order.getStatus()) {
            log.warn("Order {} is already PAID; ignoring cancellation webhook (code={})",
                    order.getOrderCode(), code);
            return;
        }

        if (PaymentOrderStatus.CANCELLED == order.getStatus()
                || PaymentOrderStatus.EXPIRED == order.getStatus()) {
            log.info("Order {} already terminal (status={}); skipping", order.getOrderCode(), order.getStatus());
            return;
        }

        PaymentOrderStatus newStatus = "02".equals(code)
                ? PaymentOrderStatus.EXPIRED
                : PaymentOrderStatus.CANCELLED;

        saveTransactionIfNotExists(order, data, PaymentTransactionStatus.FAILED);

        order.setStatus(newStatus);
        paymentOrderRepository.save(order);
        log.info("Order {} set to {} (PayOS code={})", order.getOrderCode(), newStatus, code);
    }

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
                    "PayOS amount mismatch for order " + order.getOrderCode()
                            + ": expected " + order.getAmount() + ", got " + data.getAmount());
        }

        if (order.getPaymentLinkId() != null
                && data.getPaymentLinkId() != null
                && !order.getPaymentLinkId().equals(data.getPaymentLinkId())) {
            throw new RuntimeException("PayOS payment link mismatch for order " + order.getOrderCode());
        }
    }

    private void saveTransactionIfNotExists(PaymentOrder order, WebhookData data,
                                            PaymentTransactionStatus txStatus) {
        if (data.getReference() != null
                && paymentTransactionRepository.existsByReference(data.getReference())) {
            log.info("Transaction with reference {} already exists; skipping", data.getReference());
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
        } catch (DataIntegrityViolationException e) {
            log.info("Payment transaction for reference {} already exists or violates uniqueness; skipping",
                    data.getReference());
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
}
