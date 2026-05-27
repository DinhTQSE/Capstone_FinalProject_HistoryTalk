package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent by the frontend after PayOS redirects back via
 * returnUrl/cancelUrl. The frontend extracts these query params from the
 * redirect URL and POSTs them here so the backend can update the order status.
 *
 * PayOS redirect params:
 *   code       - "00" (success) or "01" (invalid params)
 *   id         - paymentLinkId
 *   cancel     - true if user cancelled, false if paid/pending
 *   status     - "PAID" | "PENDING" | "PROCESSING" | "CANCELLED"
 *   orderCode  - our order code (Long)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOSReturnRequest {

    /** PayOS result code: "00" = success, "01" = invalid params */
    private String code;

    /** PayOS payment link ID — maps to PaymentOrder.paymentLinkId */
    private String id;

    /** true = user explicitly cancelled; false = paid or still pending */
    private Boolean cancel;

    /** PayOS status string: PAID | PENDING | PROCESSING | CANCELLED */
    private String status;

    /** Our order code used to look up the PaymentOrder */
    private Long orderCode;
}
