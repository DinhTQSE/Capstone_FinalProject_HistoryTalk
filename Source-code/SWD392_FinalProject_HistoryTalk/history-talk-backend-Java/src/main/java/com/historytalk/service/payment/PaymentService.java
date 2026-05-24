package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.user.User;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
                .orElseThrow(() -> new RuntimeException("User not found"));

        Tier tier = tierRepository.findById(UUID.fromString(tierId))
                .orElseThrow(() -> new RuntimeException("Tier not found"));

        if (!Boolean.TRUE.equals(tier.getIsActive()) || tier.getDeletedAt() != null) {
            throw new RuntimeException("Tier is inactive");
        }

        if (tier.getAmount() == null || tier.getAmount() <= 0) {
            throw new RuntimeException("Free tier does not require payment");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiredAt = now.plusMinutes(15);

        Long orderCode = generateOrderCode();

        /*
         * PayOS description nên ngắn.
         * Nếu ngân hàng không liên kết trực tiếp qua PayOS, mô tả có thể bị giới hạn ký tự.
         */
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

    private Long generateOrderCode() {
        long millis = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100, 999);
        return Long.parseLong(millis + "" + random);
    }
}
