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

    /** Amount actually charged (originalAmount - upgradeCreditAmount) */
    private Integer amount;

    /** List price of the purchased tier */
    private Integer originalAmount;

    /** Prorated credit from the replaced paid plan; 0 for a plain purchase */
    private Integer upgradeCreditAmount;

    private String status;

    private String expiredAt;
}
