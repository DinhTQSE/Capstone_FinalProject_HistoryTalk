package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentResponse {
    private String orderId;

    private Long orderCode;

    private String paymentLinkId;

    private String checkoutUrl;

    private String qrCode;

    private Integer amount;

    private String status;

    private String expiredAt;
}
