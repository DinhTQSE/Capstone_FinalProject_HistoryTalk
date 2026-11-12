package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolTokenPurchaseRequest;
import com.historytalk.dto.school.SchoolTokenPurchaseHistoryResponse;
import com.historytalk.dto.school.SchoolTokenPurchaseResponse;
import com.historytalk.exception.GlobalExceptionHandler;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolAdminTokenPurchaseControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SchoolAdminTokenPurchaseController controller;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "admin@school.edu.vn", userId.toString(), "SCHOOL_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"))
                )
        );

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /api/v1/school-admin/tokens/purchase - Success")
    void purchaseTokens_Success() throws Exception {
        CreateSchoolTokenPurchaseRequest request = CreateSchoolTokenPurchaseRequest.builder()
                .tokens(10_000_000L)
                .build();

        SchoolTokenPurchaseResponse response = SchoolTokenPurchaseResponse.builder()
                .orderId(UUID.randomUUID().toString())
                .orderCode(123456789L)
                .schoolId(UUID.randomUUID().toString())
                .schoolName("THPT Chuyên Lê Hồng Phong")
                .tokens(10_000_000L)
                .amount(1_500_000)
                .paymentLinkId("plink_123")
                .checkoutUrl("https://pay.payos.vn/web/123456789")
                .qrCode("000201010212...")
                .status("PENDING")
                .expiredAt(LocalDateTime.now().plusMinutes(15).toString())
                .createdAt(LocalDateTime.now().toString())
                .build();

        when(paymentService.createSchoolTokenPurchase(any(CreateSchoolTokenPurchaseRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/school-admin/tokens/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value(123456789L))
                .andExpect(jsonPath("$.data.tokens").value(10000000L))
                .andExpect(jsonPath("$.data.amount").value(1500000))
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://pay.payos.vn/web/123456789"));
    }

    @Test
    @DisplayName("POST /api/v1/school-admin/tokens/purchase - Tokens below min should return 400 Bad Request")
    void purchaseTokens_BelowMin_BadRequest() throws Exception {
        CreateSchoolTokenPurchaseRequest request = CreateSchoolTokenPurchaseRequest.builder()
                .tokens(50_000L) // below 100,000
                .build();

        mockMvc.perform(post("/api/v1/school-admin/tokens/purchase")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/school-admin/tokens/purchase-history - Success")
    void getPurchaseHistory_Success() throws Exception {
        SchoolTokenPurchaseHistoryResponse item = SchoolTokenPurchaseHistoryResponse.builder()
                .orderId(UUID.randomUUID().toString())
                .orderCode(123456789L)
                .schoolId(UUID.randomUUID().toString())
                .schoolName("THPT Chuyên Lê Hồng Phong")
                .tokens(10_000_000L)
                .amount(1_500_000)
                .status("PAID")
                .fulfillmentStatus("FULFILLED")
                .paidAt(LocalDateTime.now().toString())
                .createdAt(LocalDateTime.now().toString())
                .build();

        PaginatedResponse<SchoolTokenPurchaseHistoryResponse> pageResponse =
                PaginatedResponse.<SchoolTokenPurchaseHistoryResponse>builder()
                        .content(List.of(item))
                        .currentPage(0)
                        .pageSize(10)
                        .totalElements(1L)
                        .totalPages(1)
                        .hasNext(false)
                        .hasPrevious(false)
                        .build();

        when(paymentService.getSchoolTokenPurchaseHistory(any(Pageable.class)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/school-admin/tokens/purchase-history?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].orderCode").value(123456789L))
                .andExpect(jsonPath("$.data.content[0].tokens").value(10000000L))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"));
    }
}
