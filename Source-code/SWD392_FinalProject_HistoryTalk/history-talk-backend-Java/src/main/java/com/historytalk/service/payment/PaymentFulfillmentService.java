package com.historytalk.service.payment;

import com.historytalk.entity.enums.PaymentFulfillmentStatus;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.UserTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFulfillmentService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserTierRepository userTierRepository;
    private final UserRepository userRepository;

    @Transactional
    public boolean fulfillPaidOrder(Long orderCode) {
        PaymentOrder order = paymentOrderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new RuntimeException("Payment order not found: " + orderCode));
        return fulfillLockedPaidOrder(order);
    }

    @Transactional
    public boolean fulfillLockedPaidOrder(PaymentOrder order) {
        if (order == null) {
            return false;
        }

        if (isFulfilled(order)) {
            log.info("Payment order {} already fulfilled, skipping", order.getOrderCode());
            return true;
        }

        if (order.getStatus() != PaymentOrderStatus.PAID) {
            log.warn("Payment order {} is {}, not PAID. Fulfillment skipped.",
                    order.getOrderCode(), order.getStatus());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        markProcessing(order, now);

        try {
            Optional<UserTier> userTierForOrder = userTierRepository.findByPaymentOrder_OrderId(order.getOrderId());
            if (userTierForOrder.isPresent()) {
                markFulfilled(order, now);
                log.info("Payment order {} already has UserTier {}, marked fulfilled",
                        order.getOrderCode(), userTierForOrder.get().getId());
                return true;
            }

            User user = order.getUser();
            Tier newTier = order.getTier();

            if (user == null || user.getDeletedAt() != null) {
                throw new IllegalStateException("Order user is missing or deleted");
            }
            if (newTier == null || newTier.getDeletedAt() != null) {
                throw new IllegalStateException("Order tier is missing or deleted");
            }

            Optional<UserTier> currentActive = userTierRepository.findCurrentActiveByUidForUpdate(user.getUid(), now);
            if (currentActive.isPresent()
                    && currentActive.get().getTier() != null
                    && currentActive.get().getTier().getAmount() != null
                    && currentActive.get().getTier().getAmount() > 0) {
                throw new IllegalStateException(
                        "User already has active paid subscription: " + currentActive.get().getId());
            }

            UserTier newSub = UserTier.builder()
                    .user(user)
                    .tier(newTier)
                    .paymentOrder(order)
                    .startTime(now)
                    .endTime(now.plusMonths(resolveMonths(newTier)))
                    .isActive(true)
                    .build();
            userTierRepository.save(newSub);

            user.setToken(resolveLimitedToken(newTier));
            user.setLastTokenResetAt(now);
            userRepository.save(user);

            markFulfilled(order, now);
            log.info("Payment order {} fulfilled: user={}, tier={}, tokens={}",
                    order.getOrderCode(), user.getUid(), newTier.getTitle(), user.getToken());
            return true;
        } catch (Exception ex) {
            markFailed(order, ex);
            log.error("Payment order {} fulfillment failed: {}", order.getOrderCode(), ex.getMessage());
            return false;
        }
    }

    private void markProcessing(PaymentOrder order, LocalDateTime now) {
        order.setFulfillmentStatus(PaymentFulfillmentStatus.PROCESSING);
        order.setFulfillmentLockedAt(now);
        order.setFulfillmentAttempts(nullSafe(order.getFulfillmentAttempts()) + 1);
        order.setFulfillmentError(null);
        paymentOrderRepository.save(order);
    }

    private void markFulfilled(PaymentOrder order, LocalDateTime now) {
        order.setFulfillmentStatus(PaymentFulfillmentStatus.FULFILLED);
        order.setFulfilledAt(now);
        order.setFulfillmentLockedAt(null);
        order.setFulfillmentError(null);
        paymentOrderRepository.save(order);
    }

    private void markFailed(PaymentOrder order, Exception ex) {
        order.setFulfillmentStatus(PaymentFulfillmentStatus.FAILED);
        order.setFulfillmentLockedAt(null);
        order.setFulfillmentError(truncate(ex.getMessage()));
        paymentOrderRepository.save(order);
    }

    private boolean isFulfilled(PaymentOrder order) {
        return order.getFulfillmentStatus() == PaymentFulfillmentStatus.FULFILLED
                && order.getFulfilledAt() != null;
    }

    private int resolveMonths(Tier tier) {
        return tier.getNoMonth() == null || tier.getNoMonth() <= 0 ? 1 : tier.getNoMonth();
    }

    private int resolveLimitedToken(Tier tier) {
        return tier.getLimitedToken() == null ? 0 : tier.getLimitedToken();
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
