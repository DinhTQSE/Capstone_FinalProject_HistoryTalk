package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {
    private String orderId;

    private Long orderCode;

    private String tierId;

    private String tierTitle;

    private Integer amount;

    private String status;

    private String paymentLinkId;

    private String createdAt;

    private String paidAt;

    private String expiredAt;
}
