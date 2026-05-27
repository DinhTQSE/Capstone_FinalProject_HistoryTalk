package com.historytalk.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO returned exclusively to SYSTEM_ADMIN via
 * GET /api/v1/payments/history.
 *
 * Extends the customer-facing history shape with the order owner's
 * identity fields (userId, userName, userEmail) so admins can see
 * which customer each order belongs to without a separate user lookup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPaymentHistoryResponse {

    // ── Order fields (same as PaymentHistoryResponse) ───────────────────────

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

    // ── Customer identity fields (admin-only) ────────────────────────────────

    /** UUID of the customer who created the order. */
    private String userId;

    /** Username of the customer. */
    private String userName;

    /** Email of the customer. */
    private String userEmail;
}
