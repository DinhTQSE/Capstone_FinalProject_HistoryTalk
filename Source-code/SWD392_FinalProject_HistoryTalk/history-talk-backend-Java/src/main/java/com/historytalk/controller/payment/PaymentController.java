package com.historytalk.controller.payment;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.payment.AdminPaymentHistoryResponse;
import com.historytalk.dto.payment.CreatePaymentRequest;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.service.payment.PaymentService;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    /**
     * Creates a PayOS payment link for the given tier.
     * Requires authentication — the user's uid is read from the JWT via
     * SecurityUtils.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createCheckout(
            @RequestBody CreatePaymentRequest request) throws Exception {
        UUID uid = UUID.fromString(SecurityUtils.getUserId());
        CreatePaymentResponse response = paymentService.createPayOSCheckout(uid, request.getTierId());
        return ResponseEntity.ok(ApiResponse.success(response, "Payment checkout created successfully"));
    }

    /**
     * Returns the authenticated customer's own payment order history, newest first.
     * Requires CUSTOMER role.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getMyPaymentHistory() {
        UUID uid = UUID.fromString(SecurityUtils.getUserId());
        List<PaymentHistoryResponse> history = paymentService.getMyPaymentHistory(uid);
        return ResponseEntity.ok(ApiResponse.success(history, "Payment history retrieved successfully"));
    }

    /**
     * Returns all customers' payment order history for SYSTEM_ADMIN.
     * Optional query params: status, userId.
     * Results are paginated (page 0-indexed, default size 20), newest first.
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<PaginatedResponse<AdminPaymentHistoryResponse>>> getAllPaymentHistory(
            @RequestParam(required = false) PaymentOrderStatus status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        var data = paymentService.getAllPaymentHistory(status, userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Payment history retrieved successfully"));
    }

    /**
     * Lists all active tiers available for purchase.
     * Public — no authentication required.
     */
    @GetMapping("/tiers")
    public ResponseEntity<ApiResponse<List<TierResponse>>> listTiers() {
        List<TierResponse> tiers = paymentService.listActiveTiers();
        return ResponseEntity.ok(ApiResponse.success(tiers, "Tiers retrieved successfully"));
    }

    /**
     * Processes the PayOS return-URL callback forwarded by the frontend.
     *
     * When PayOS redirects the user's browser to cancelUrl or returnUrl, the
     * frontend receives query params (code, id, cancel, status, orderCode) and
     * must POST them here so the backend can update the order status for the UI
     * without waiting for the async webhook.
     *
     * Requires authentication — the order ownership is validated against the
     * authenticated user's uid so that users cannot update each other's orders.
     *
     * This endpoint does NOT upgrade the user's tier; that remains exclusively
     * in the webhook path which performs HMAC verification.
     */
    @PostMapping("/payos/return")
    public ResponseEntity<ApiResponse<PayOSReturnResponse>> handlePayOSReturn(
            @RequestBody PayOSReturnRequest request) {
        UUID uid = UUID.fromString(SecurityUtils.getUserId());
        PayOSReturnResponse response = paymentService.handlePayOSReturn(uid, request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }
}
