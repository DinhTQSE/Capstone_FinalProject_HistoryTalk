package com.historytalk.controller.payment;

import com.historytalk.service.payment.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

/**
 * Receives webhook callbacks from PayOS.
 *
 * Security note: The PayOS SDK's payOS.webhooks().verify(webhook) validates
 * the HMAC-SHA256 signature of the payload using the configured checksumKey.
 * This endpoint must be public (no JWT) so PayOS servers can reach it.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/payos")
public class PayOSWebhookController {
    private final PayOS payOS;
    private final PaymentWebhookService paymentWebhookService;

    /**
     * PayOS sends a GET to this URL during webhook registration to verify it is reachable.
     * Must return 200 — no body required.
     */
    @GetMapping("/webhook")
    public ResponseEntity<String> verifyWebhook() {
        return ResponseEntity.ok("OK");
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Webhook webhook) {
        try {
            // verify() throws an exception if the HMAC signature is invalid,
            // effectively rejecting any tampered or spoofed webhook payload.
            WebhookData data = payOS.webhooks().verify(webhook);
            paymentWebhookService.handlePayOSWebhook(data);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.warn("Rejected PayOS webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid webhook");
//            return ResponseEntity.ok("OK");
        }
    }
}
