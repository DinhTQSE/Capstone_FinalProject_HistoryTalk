package com.historytalk.controller.payment;

import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.service.payment.PaymentWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.payos.PayOS;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PayOSWebhookControllerTest {

    private MockMvc mockMvc;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;

    @Mock
    private PaymentWebhookService paymentWebhookService;

    @InjectMocks
    private PayOSWebhookController payOSWebhookController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(payOSWebhookController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-WEBHOOK-01: GET /api/v1/payments/payos/webhook returns 200 OK for PayOS health check")
    void verifyWebhook_Returns200Ok() throws Exception {
        mockMvc.perform(get("/api/v1/payments/payos/webhook"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    @DisplayName("IT-WEBHOOK-02 (WF-05 / DS-05a): POST /api/v1/payments/payos/webhook with valid HMAC signature processes payment")
    void handleWebhook_ValidHmacSignature_ProcessesAndReturns200() throws Exception {
        WebhookData verifiedData = WebhookData.builder()
                .orderCode(2026111201L)
                .amount(49000L)
                .code("00")
                .desc("success")
                .description("HISTALK111201")
                .accountNumber("0000000000")
                .reference("FT2611120001")
                .transactionDateTime("2026-11-12 10:00:00")
                .currency("VND")
                .paymentLinkId("plink_001")
                .counterAccountBankId("")
                .counterAccountBankName("")
                .counterAccountName("")
                .counterAccountNumber("")
                .virtualAccountName("")
                .virtualAccountNumber("")
                .build();

        when(payOS.webhooks().verify(any(Webhook.class))).thenReturn(verifiedData);

        String payload = """
                {
                  "code": "00",
                  "desc": "success",
                  "success": true,
                  "data": {
                    "orderCode": 2026111201,
                    "amount": 49000,
                    "description": "HISTALK111201",
                    "accountNumber": "00000000",
                    "reference": "FT2611120001",
                    "transactionDateTime": "2026-11-12 10:00:00",
                    "currency": "VND",
                    "paymentLinkId": "plink_001",
                    "code": "00",
                    "desc": "success"
                  },
                  "signature": "valid_hmac_sha256_signature"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));

        verify(paymentWebhookService).handlePayOSWebhook(verifiedData);
    }

    @Test
    @DisplayName("IT-WEBHOOK-03 (WF-05 / DS-05b): POST /api/v1/payments/payos/webhook with invalid HMAC signature returns 400")
    void handleWebhook_InvalidHmacSignature_RejectsWith400() throws Exception {
        when(payOS.webhooks().verify(any(Webhook.class)))
                .thenThrow(new RuntimeException("Invalid signature"));

        String tamperedPayload = """
                {
                  "code": "00",
                  "desc": "success",
                  "success": true,
                  "data": {
                    "orderCode": 2026111201,
                    "amount": 49000
                  },
                  "signature": "forged_signature"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/payos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tamperedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid webhook"));

        verify(paymentWebhookService, never()).handlePayOSWebhook(any());
    }
}
