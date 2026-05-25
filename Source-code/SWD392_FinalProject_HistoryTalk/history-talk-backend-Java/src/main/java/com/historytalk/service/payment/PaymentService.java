package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PayOS payOS;
    private final PayOSConfig payOSConfig;

    private final UserRepository userRepository;
    private final TierRepository tierRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    @Transactional
    public CreatePaymentResponse createPayOSCheckout(UUID uid, String tierId) throws Exception {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uid));

        Tier tier = tierRepository.findById(UUID.fromString(tierId))
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + tierId));

        if (!Boolean.TRUE.equals(tier.getIsActive()) || tier.getDeletedAt() != null) {
            throw new InvalidRequestException("Tier is inactive or deleted");
        }

        if (tier.getAmount() == null || tier.getAmount() <= 0) {
            throw new InvalidRequestException("Free tier does not require payment");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredAt = now.plusMinutes(15);

        Long orderCode = generateOrderCode();

        // PayOS description must be short; many banks cap at 25 characters
        String description = "HISTALK" + orderCode.toString().substring(orderCode.toString().length() - 6);

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .tier(tier)
                .orderCode(orderCode)
                .amount(tier.getAmount())
                .description(description)
                .status(PaymentOrderStatus.PENDING)
                .expiredAt(expiredAt)
                .isActive(true)
                .build();

        paymentOrderRepository.save(order);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(tier.getAmount().longValue())
                .description(description)
                .returnUrl(payOSConfig.getReturnUrl())
                .cancelUrl(payOSConfig.getCancelUrl())
                .expiredAt(expiredAt.toEpochSecond(ZoneOffset.ofHours(7)))
                .build();

        var paymentLink = payOS.paymentRequests().create(request);

        order.setPaymentLinkId(paymentLink.getPaymentLinkId());
        order.setCheckoutUrl(paymentLink.getCheckoutUrl());
        order.setQrCode(paymentLink.getQrCode());

        paymentOrderRepository.save(order);
        log.info("Created PayOS checkout: orderCode={}, user={}, tier={}", orderCode, uid, tier.getTitle());

        return CreatePaymentResponse.builder()
                .orderId(order.getOrderId().toString())
                .orderCode(order.getOrderCode())
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .amount(order.getAmount())
                .status(order.getStatus().name())
                .expiredAt(order.getExpiredAt() != null ? order.getExpiredAt().toString() : null)
                .build();
    }

    /**
     * Returns the authenticated user's payment order history, newest first.
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getPaymentHistory(UUID uid) {
        List<PaymentOrder> orders = paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(uid);

        return orders.stream()
                .map(o -> PaymentHistoryResponse.builder()
                        .orderId(o.getOrderId().toString())
                        .orderCode(o.getOrderCode())
                        .tierId(o.getTier().getTierId().toString())
                        .tierTitle(o.getTier().getTitle())
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .paymentLinkId(o.getPaymentLinkId())
                        .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                        .paidAt(o.getPaidAt() != null ? o.getPaidAt().toString() : null)
                        .expiredAt(o.getExpiredAt() != null ? o.getExpiredAt().toString() : null)
                        .build())
                .toList();
    }

    /**
     * Lists all active tiers available for purchase.
     */
    @Transactional(readOnly = true)
    public List<TierResponse> listActiveTiers() {
        return tierRepository.findByIsActiveTrueAndDeletedAtIsNull().stream()
                .map(t -> TierResponse.builder()
                        .tierId(t.getTierId().toString())
                        .title(t.getTitle())
                        .amount(t.getAmount())
                        .noMonth(t.getNoMonth())
                        .limitedToken(t.getLimitedToken())
                        .isActive(t.getIsActive())
                        .build())
                .toList();
    }

    private Long generateOrderCode() {
        long millis = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100, 999);
        return Long.parseLong(millis + "" + random);
    }
}
