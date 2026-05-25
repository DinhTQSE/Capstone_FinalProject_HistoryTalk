package com.historytalk.controller.payment;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.payment.CreatePaymentRequest;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.service.payment.PaymentService;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    /**
     * Creates a PayOS payment link for the given tier.
     * Requires authentication — the user's uid is read from the JWT via SecurityUtils.
     */
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CreatePaymentResponse>> createCheckout(
            @RequestBody CreatePaymentRequest request
    ) throws Exception {
        UUID uid = UUID.fromString(SecurityUtils.getUserId());
        CreatePaymentResponse response = paymentService.createPayOSCheckout(uid, request.getTierId());
        return ResponseEntity.ok(ApiResponse.success(response, "Payment checkout created successfully"));
    }

    /**
     * Returns the authenticated user's full payment order history, newest first.
     * Requires authentication.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory() {
        UUID uid = UUID.fromString(SecurityUtils.getUserId());
        List<PaymentHistoryResponse> history = paymentService.getPaymentHistory(uid);
        return ResponseEntity.ok(ApiResponse.success(history, "Payment history retrieved successfully"));
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
}
