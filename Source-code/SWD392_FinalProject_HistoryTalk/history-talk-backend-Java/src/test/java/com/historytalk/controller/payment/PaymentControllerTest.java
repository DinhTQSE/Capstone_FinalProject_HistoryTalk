package com.historytalk.controller.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.payment.CreatePaymentRequest;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.security.AuthenticatedPrincipal;
import com.historytalk.service.payment.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID userId;
    private String tierId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID().toString();

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "customer@example.com", userId.toString(), "CUSTOMER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
                )
        );

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("IT-PAY-01: GET /api/v1/payments/tiers returns active subscription tiers")
    void listTiers_Public_Returns200() throws Exception {
        TierResponse tier = TierResponse.builder()
                .tierId(tierId)
                .title("Plus Monthly")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(50_000)
                .isActive(true)
                .build();

        when(paymentService.listActiveTiers()).thenReturn(List.of(tier));

        mockMvc.perform(get("/api/v1/payments/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].title").value("Plus Monthly"))
                .andExpect(jsonPath("$.data[0].amount").value(49000));
    }

    @Test
    @DisplayName("IT-PAY-02: POST /api/v1/payments/checkout creates PayOS checkout link and returns 200")
    void createCheckout_ValidTier_Returns200() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setTierId(tierId);

        CreatePaymentResponse res = CreatePaymentResponse.builder()
                .orderId(UUID.randomUUID().toString())
                .orderCode(2026111201L)
                .paymentLinkId("plink_001")
                .checkoutUrl("https://pay.payos.vn/web/plink_001")
                .amount(49_000)
                .status("PENDING")
                .build();

        when(paymentService.createPayOSCheckout(eq(userId), eq(tierId))).thenReturn(res);

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value(2026111201L))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("IT-PAY-03: POST /api/v1/payments/checkout when active subscription exists returns 400 Bad Request")
    void createCheckout_ActiveSubscriptionExists_Returns400() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest();
        req.setTierId(tierId);

        when(paymentService.createPayOSCheckout(eq(userId), eq(tierId)))
                .thenThrow(new InvalidRequestException("You already have an active 'Plus Monthly' subscription"));

        mockMvc.perform(post("/api/v1/payments/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-PAY-04: POST /api/v1/payments/payos/return handles user cancellation")
    void handlePayOSReturn_Cancelled_Returns200() throws Exception {
        PayOSReturnRequest req = PayOSReturnRequest.builder()
                .orderCode(2026111201L)
                .cancel(true)
                .status("CANCELLED")
                .build();

        PayOSReturnResponse res = PayOSReturnResponse.builder()
                .orderCode(2026111201L)
                .resolvedStatus("CANCELLED")
                .message("Payment has been cancelled.")
                .build();

        when(paymentService.handlePayOSReturn(eq(userId), any(PayOSReturnRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/payments/payos/return")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolvedStatus").value("CANCELLED"));
    }

    @Test
    @DisplayName("IT-PAY-05: GET /api/v1/payments/me returns customer payment order history")
    void getMyPaymentHistory_Customer_Returns200() throws Exception {
        PaymentHistoryResponse item = PaymentHistoryResponse.builder()
                .orderId(UUID.randomUUID().toString())
                .orderCode(2026111201L)
                .tierTitle("Plus Monthly")
                .amount(49_000)
                .status("PAID")
                .build();

        when(paymentService.getMyPaymentHistory(eq(userId))).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/payments/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].orderCode").value(2026111201L))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }
}
