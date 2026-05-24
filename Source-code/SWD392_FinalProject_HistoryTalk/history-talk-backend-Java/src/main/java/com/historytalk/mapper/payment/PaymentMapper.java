package com.historytalk.mapper.payment;

import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.entity.payment.PaymentOrder;
public class PaymentMapper {
    public static CreatePaymentResponse toCreatePaymentResponse(PaymentOrder order) {
        return CreatePaymentResponse.builder()
                .orderId(order.getOrderId().toString())
                .orderCode(order.getOrderCode())
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .amount(order.getAmount())
                .status(order.getStatus())
                .expiredAt(order.getExpiredAt() != null ? order.getExpiredAt().toString() : null)
                .build();
    }
}
