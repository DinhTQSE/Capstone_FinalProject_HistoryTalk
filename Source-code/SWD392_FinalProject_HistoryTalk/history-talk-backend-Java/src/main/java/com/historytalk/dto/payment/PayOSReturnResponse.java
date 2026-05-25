package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response returned after the backend processes the PayOS return-URL callback.
 * Tells the frontend the resolved order status and a human-readable message.
 *
 * resolvedStatus is the name of PaymentOrderStatus (PENDING, PAID, CANCELLED,
 * EXPIRED, FAILED). The frontend should use this to decide what to display.
 *
 * Message values by status:
 *   CANCELLED  → "Payment has been cancelled."
 *   PAID       → "Payment has already been confirmed."
 *   PENDING    → "Payment is pending. Please wait for confirmation."
 *   EXPIRED    → "Payment link has expired. Please create a new order."
 *   FAILED     → "Payment failed. Please try again."
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOSReturnResponse {

    /** Our order code, echoed back for frontend correlation */
    private Long orderCode;

    /** The resolved PaymentOrderStatus name (e.g. "CANCELLED", "PAID") */
    private String resolvedStatus;

    /** Human-readable message for the frontend to display */
    private String message;
}
