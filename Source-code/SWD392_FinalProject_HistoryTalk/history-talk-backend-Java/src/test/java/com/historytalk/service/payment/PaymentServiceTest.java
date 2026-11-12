package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.dto.school.CreateSchoolTokenPurchaseRequest;
import com.historytalk.dto.school.SchoolTokenPurchaseHistoryResponse;
import com.historytalk.dto.school.SchoolTokenPurchaseResponse;
import com.historytalk.entity.enums.PaymentOrderType;
import com.historytalk.entity.school.School;
import com.historytalk.security.AuthenticatedPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;

    @Mock
    private PayOSConfig payOSConfig;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TierRepository tierRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private UserTierRepository userTierRepository;

    @InjectMocks
    private PaymentService paymentService;

    private UUID userId;
    private UUID tierId;
    private User customer;
    private Tier plusTier;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tierId = UUID.randomUUID();

        customer = User.builder()
                .uid(userId)
                .userName("customer_01")
                .email("customer01@example.com")
                .role(UserRole.CUSTOMER)
                .token(100)
                .build();

        plusTier = Tier.builder()
                .tierId(tierId)
                .title("Plus Monthly")
                .amount(49_000)
                .noMonth(1)
                .limitedToken(50_000)
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("WF-05 / DS-05a: Customer creates PayOS checkout for paid tier and receives checkoutUrl + qrCode")
    void createPayOSCheckout_ValidPaidTier_CreatesPendingOrderAndReturnsCheckoutUrl() throws Exception {
        CreatePaymentLinkResponse linkResponse = mock(CreatePaymentLinkResponse.class);
        when(linkResponse.getPaymentLinkId()).thenReturn("plink_123456");
        when(linkResponse.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/plink_123456");
        when(linkResponse.getQrCode()).thenReturn("00020101021238570010A00000072701270006970422");

        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(plusTier));
        when(userTierRepository.findCurrentActiveByUid(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(payOSConfig.getReturnUrl()).thenReturn("https://historytalk.vn/payment/return");
        when(payOSConfig.getCancelUrl()).thenReturn("https://historytalk.vn/payment/cancel");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(linkResponse);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            PaymentOrder o = inv.getArgument(0);
            if (o.getOrderId() == null) {
                o.setOrderId(UUID.randomUUID());
            }
            return o;
        });

        CreatePaymentResponse response = paymentService.createPayOSCheckout(userId, tierId.toString());

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getAmount()).isEqualTo(49_000);
        assertThat(response.getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/plink_123456");
        assertThat(response.getPaymentLinkId()).isEqualTo("plink_123456");
        verify(paymentOrderRepository, times(2)).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("WF-05 / DS-05b (MSG30): Customer with active paid subscription is blocked from purchasing another tier")
    void createPayOSCheckout_ActivePaidSubscriptionExists_ThrowsInvalidRequestException() {
        UserTier activePaid = UserTier.builder()
                .user(customer)
                .tier(plusTier)
                .endTime(LocalDateTime.now().plusDays(20))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(plusTier));
        when(userTierRepository.findCurrentActiveByUid(eq(userId), any(LocalDateTime.class)))
                .thenReturn(Optional.of(activePaid));

        assertThatThrownBy(() -> paymentService.createPayOSCheckout(userId, tierId.toString()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already have an active");

        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("WF-05: Free tier (amount <= 0) is rejected for PayOS checkout")
    void createPayOSCheckout_FreeTier_ThrowsInvalidRequestException() {
        plusTier.setAmount(0);
        when(userRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(plusTier));

        assertThatThrownBy(() -> paymentService.createPayOSCheckout(userId, tierId.toString()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Free tier does not require payment");
    }

    @Test
    @DisplayName("WF-05 / DS-05b: User-initiated cancel on PayOS return marks order CANCELLED without upgrading tier")
    void handlePayOSReturn_UserCancelled_MarksOrderCancelled() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(2026111201L)
                .user(customer)
                .tier(plusTier)
                .status(PaymentOrderStatus.PENDING)
                .build();

        PayOSReturnRequest returnReq = PayOSReturnRequest.builder()
                .orderCode(2026111201L)
                .cancel(true)
                .status("CANCELLED")
                .build();

        when(paymentOrderRepository.findByOrderCode(2026111201L)).thenReturn(Optional.of(order));

        PayOSReturnResponse response = paymentService.handlePayOSReturn(userId, returnReq);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.CANCELLED);
        assertThat(response.getResolvedStatus()).isEqualTo("CANCELLED");
        verify(paymentOrderRepository).save(order);
    }

    @Test
    @DisplayName("WF-05: Frontend PayOS return reporting PAID keeps order PENDING until verified HMAC webhook arrives")
    void handlePayOSReturn_FrontendReportsPaid_RemainsPendingWaitingForVerifiedWebhook() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(2026111202L)
                .user(customer)
                .tier(plusTier)
                .status(PaymentOrderStatus.PENDING)
                .build();

        PayOSReturnRequest returnReq = PayOSReturnRequest.builder()
                .orderCode(2026111202L)
                .cancel(false)
                .status("PAID")
                .build();

        when(paymentOrderRepository.findByOrderCode(2026111202L)).thenReturn(Optional.of(order));

        PayOSReturnResponse response = paymentService.handlePayOSReturn(userId, returnReq);

        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(response.getResolvedStatus()).isEqualTo("PENDING");
        assertThat(response.getMessage()).contains("Payment is being verified");
        verify(paymentOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("WF-05: listActiveTiers returns active non-deleted subscription tiers")
    void listActiveTiers_ReturnsActiveNonDeletedTiers() {
        when(tierRepository.findByIsActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(plusTier));

        List<TierResponse> tiers = paymentService.listActiveTiers();

        assertThat(tiers).hasSize(1);
        assertThat(tiers.get(0).getTitle()).isEqualTo("Plus Monthly");
        assertThat(tiers.get(0).getAmount()).isEqualTo(49_000);
    }

    @Test
    @DisplayName("WF-05: getMyPaymentHistory returns customer payment orders newest first")
    void getMyPaymentHistory_ReturnsCustomerOrdersNewestFirst() {
        PaymentOrder order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(2026111203L)
                .user(customer)
                .tier(plusTier)
                .amount(49_000)
                .status(PaymentOrderStatus.PAID)
                .build();

        when(paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(userId)).thenReturn(List.of(order));

        List<PaymentHistoryResponse> history = paymentService.getMyPaymentHistory(userId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOrderCode()).isEqualTo(2026111203L);
        assertThat(history.get(0).getStatus()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("createSchoolTokenPurchase - Success creates PayOS checkout and saves order")
    void createSchoolTokenPurchase_Success() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "admin@school.edu.vn", adminId.toString(), "SCHOOL_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"))));

        School school = School.builder()
                .id(schoolId)
                .name("Trường THPT Lê Quý Đôn")
                .schoolCode("LQD-HCM")
                .totalSchoolTokenQuota(100_000_000)
                .unallocatedTokenQuota(10_000)
                .build();

        User schoolAdmin = User.builder()
                .uid(adminId)
                .userName("lqd_admin")
                .school(school)
                .role(UserRole.SCHOOL_ADMIN)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(schoolAdmin));
        when(payOSConfig.getReturnUrl()).thenReturn("https://app.historytalk.vn/return");
        when(payOSConfig.getCancelUrl()).thenReturn("https://app.historytalk.vn/cancel");

        CreatePaymentLinkResponse linkResp = mock(CreatePaymentLinkResponse.class);
        when(linkResp.getPaymentLinkId()).thenReturn("plink_999");
        when(linkResp.getCheckoutUrl()).thenReturn("https://pay.payos.vn/web/999");
        when(linkResp.getQrCode()).thenReturn("000201010212...");
        when(payOS.paymentRequests().create(any(CreatePaymentLinkRequest.class))).thenReturn(linkResp);
        when(paymentOrderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> {
            PaymentOrder o = inv.getArgument(0);
            if (o.getOrderId() == null) {
                o.setOrderId(UUID.randomUUID());
            }
            return o;
        });

        CreateSchoolTokenPurchaseRequest req = CreateSchoolTokenPurchaseRequest.builder()
                .tokens(10_000_000L)
                .build();

        SchoolTokenPurchaseResponse resp = paymentService.createSchoolTokenPurchase(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getTokens()).isEqualTo(10_000_000L);
        assertThat(resp.getAmount()).isEqualTo(1_500_000);
        assertThat(resp.getSchoolId()).isEqualTo(schoolId.toString());
        assertThat(resp.getSchoolName()).isEqualTo("Trường THPT Lê Quý Đôn");
        assertThat(resp.getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/999");
        assertThat(resp.getQrCode()).isEqualTo("000201010212...");
        verify(paymentOrderRepository, times(2)).save(any(PaymentOrder.class));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getSchoolTokenPurchaseHistory - Success returns paginated purchases")
    void getSchoolTokenPurchaseHistory_Success() {
        UUID adminId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "admin@school.edu.vn", adminId.toString(), "SCHOOL_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"))));

        School school = School.builder()
                .id(schoolId)
                .name("Trường THPT Lê Quý Đôn")
                .schoolCode("LQD-HCM")
                .build();

        User schoolAdmin = User.builder()
                .uid(adminId)
                .userName("lqd_admin")
                .school(school)
                .role(UserRole.SCHOOL_ADMIN)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(schoolAdmin));

        PaymentOrder order = PaymentOrder.builder()
                .orderId(UUID.randomUUID())
                .orderCode(888777666L)
                .school(school)
                .orderType(PaymentOrderType.SCHOOL_TOKEN_TOPUP)
                .topupTokens(5_000_000L)
                .amount(750_000)
                .status(PaymentOrderStatus.PAID)
                .build();

        Page<PaymentOrder> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);
        when(paymentOrderRepository.findBySchoolIdAndOrderTypeOrderByCreatedAtDesc(eq(schoolId), eq(PaymentOrderType.SCHOOL_TOKEN_TOPUP), any(Pageable.class)))
                .thenReturn(page);

        PaginatedResponse<SchoolTokenPurchaseHistoryResponse> history =
                paymentService.getSchoolTokenPurchaseHistory(PageRequest.of(0, 10));

        assertThat(history).isNotNull();
        assertThat(history.getContent()).hasSize(1);
        assertThat(history.getContent().get(0).getOrderCode()).isEqualTo(888777666L);
        assertThat(history.getContent().get(0).getTokens()).isEqualTo(5_000_000L);
        assertThat(history.getContent().get(0).getAmount()).isEqualTo(750_000);

        SecurityContextHolder.clearContext();
    }
}
