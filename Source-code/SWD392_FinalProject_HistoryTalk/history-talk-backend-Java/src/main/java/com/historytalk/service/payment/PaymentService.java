package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.payment.AdminPaymentHistoryResponse;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.dto.payment.UpgradeQuoteResponse;
import com.historytalk.dto.school.CreateSchoolTokenPurchaseRequest;
import com.historytalk.dto.school.SchoolTokenPurchaseHistoryResponse;
import com.historytalk.dto.school.SchoolTokenPurchaseResponse;
import com.historytalk.entity.enums.PaymentOrderType;
import com.historytalk.entity.school.School;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PayOS payOS;
    private final PayOSConfig payOSConfig;

    private final UserRepository userRepository;
    private final TierRepository tierRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserTierRepository userTierRepository;
    private final TierUpgradeCalculator tierUpgradeCalculator;

    @Transactional
    public CreatePaymentResponse createPayOSCheckout(UUID uid, String tierId) throws Exception {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + uid));

        Tier tier = loadPurchasableTier(tierId);

        // Mid-period upgrade: the unused part of the active paid plan is deducted from the price.
        // Downgrades and same-plan purchases are rejected by the calculator.
        UpgradeQuote quote = resolveQuote(uid, tier, LocalDateTime.now(ZoneOffset.UTC));

        Instant expiredAtInstant = Instant.now().plus(15, ChronoUnit.MINUTES);
        long payosExpiredAt = expiredAtInstant.getEpochSecond();
        LocalDateTime expiredAtDb = LocalDateTime.ofInstant(
                expiredAtInstant,
                ZoneOffset.UTC
        );

        Long orderCode = generateOrderCode();
        String description = "HISTALK" + orderCode.toString().substring(orderCode.toString().length() - 6);

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .tier(tier)
                .orderCode(orderCode)
                .amount(quote.amountToPay())
                .upgradeFromUserTier(quote.replacedSubscription())
                .upgradeCreditAmount(quote.replacedSubscription() != null ? quote.creditAmount() : null)
                .description(description)
                .status(PaymentOrderStatus.PENDING)
                .expiredAt(expiredAtDb)
                .isActive(true)
                .build();

        paymentOrderRepository.save(order);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount((long) quote.amountToPay())
                .description(description)
                .returnUrl(payOSConfig.getReturnUrl())
                .cancelUrl(payOSConfig.getCancelUrl())
                .expiredAt(payosExpiredAt)
                .build();

        var paymentLink = payOS.paymentRequests().create(request);

        order.setPaymentLinkId(paymentLink.getPaymentLinkId());
        order.setCheckoutUrl(paymentLink.getCheckoutUrl());
        order.setQrCode(paymentLink.getQrCode());

        paymentOrderRepository.save(order);
        log.info("Created PayOS checkout: orderCode={}, user={}, tier={}, amount={}, upgradeCredit={}",
                orderCode, uid, tier.getTitle(), quote.amountToPay(), quote.creditAmount());

        return CreatePaymentResponse.builder()
                .orderId(order.getOrderId().toString())
                .orderCode(order.getOrderCode())
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .amount(order.getAmount())
                .originalAmount(quote.originalAmount())
                .upgradeCreditAmount(quote.creditAmount())
                .status(order.getStatus().name())
                .expiredAt(order.getExpiredAt() != null ? order.getExpiredAt().toString() : null)
                .build();
    }

    /**
     * Previews the price of buying a tier for the user, including the prorated credit
     * from the active paid plan when this is a mid-period upgrade. Saves nothing.
     */
    @Transactional(readOnly = true)
    public UpgradeQuoteResponse getUpgradeQuote(UUID uid, String tierId) {
        Tier tier = loadPurchasableTier(tierId);
        UpgradeQuote quote = resolveQuote(uid, tier, LocalDateTime.now(ZoneOffset.UTC));
        UserTier current = quote.replacedSubscription();

        return UpgradeQuoteResponse.builder()
                .currentTierId(current != null ? current.getTier().getTierId().toString() : null)
                .currentTierTitle(current != null ? current.getTier().getTitle() : null)
                .currentTierNoMonth(current != null ? current.getTier().getNoMonth() : null)
                .currentEndTime(current != null ? current.getEndTime().toString() : null)
                .targetTierId(tier.getTierId().toString())
                .targetTierTitle(tier.getTitle())
                .targetTierNoMonth(tier.getNoMonth())
                .originalAmount(quote.originalAmount())
                .creditAmount(quote.creditAmount())
                .amountToPay(quote.amountToPay())
                .upgrade(current != null)
                .build();
    }

    private Tier loadPurchasableTier(String tierId) {
        Tier tier = tierRepository.findById(UUID.fromString(tierId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói hội viên: " + tierId));

        if (!Boolean.TRUE.equals(tier.getIsActive()) || tier.getDeletedAt() != null) {
            throw new InvalidRequestException("Gói hội viên đã ngừng hoạt động hoặc đã bị xóa");
        }

        if (tier.getAmount() == null || tier.getAmount() <= 0) {
            throw new InvalidRequestException("Gói miễn phí không cần thanh toán");
        }
        return tier;
    }

    /** Only an active paid plan (amount > 0) is upgraded; a free plan means a plain full-price purchase. */
    private UpgradeQuote resolveQuote(UUID uid, Tier target, LocalDateTime now) {
        UserTier currentPaid = userTierRepository.findCurrentActiveByUid(uid, now)
                .filter(ut -> ut.getTier() != null && ut.getTier().getAmount() != null && ut.getTier().getAmount() > 0)
                .orElse(null);
        return tierUpgradeCalculator.quote(currentPaid, target, now);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getMyPaymentHistory(UUID uid) {
        List<PaymentOrder> orders = paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(uid);

        return orders.stream()
                .map(o -> PaymentHistoryResponse.builder()
                        .orderId(o.getOrderId().toString())
                        .orderCode(o.getOrderCode())
                        .tierId(resolveTierId(o))
                        .tierTitle(resolveTierTitle(o))
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .paymentLinkId(o.getPaymentLinkId())
                        .createdAt(formatUtcIso(o.getCreatedAt()))
                        .paidAt(formatUtcIso(o.getPaidAt()))
                        .expiredAt(formatUtcIso(o.getExpiredAt()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<AdminPaymentHistoryResponse> getAllPaymentHistory(
            PaymentOrderStatus status,
            UUID userId,
            Pageable pageable) {

        Page<PaymentOrder> page = paymentOrderRepository.findAllForAdmin(status, userId, pageable);

        List<AdminPaymentHistoryResponse> content = page.getContent().stream()
                .map(o -> AdminPaymentHistoryResponse.builder()
                        .orderId(o.getOrderId().toString())
                        .orderCode(o.getOrderCode())
                        .tierId(resolveTierId(o))
                        .tierTitle(resolveTierTitle(o))
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .fulfillmentStatus(o.getFulfillmentStatus() != null ? o.getFulfillmentStatus().name() : null)
                        .fulfilledAt(formatUtcIso(o.getFulfilledAt()))
                        .fulfillmentAttempts(o.getFulfillmentAttempts())
                        .fulfillmentError(o.getFulfillmentError())
                        .paymentLinkId(o.getPaymentLinkId())
                        .createdAt(formatUtcIso(o.getCreatedAt()))
                        .paidAt(formatUtcIso(o.getPaidAt()))
                        .expiredAt(formatUtcIso(o.getExpiredAt()))
                        .userId(o.getUser().getUid().toString())
                        .userName(o.getUser().getUserName())
                        .userEmail(o.getUser().getEmail())
                        .build())
                .toList();

        return PaginatedResponse.<AdminPaymentHistoryResponse>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TierResponse> listActiveTiers() {
        return tierRepository.findByIsActiveTrueAndDeletedAtIsNull().stream()
                .map(t -> TierResponse.builder()
                        .tierId(t.getTierId().toString())
                        .title(t.getTitle())
                        .amount(t.getAmount())
                        .noMonth(t.getNoMonth())
                        .limitedToken(t.getLimitedToken())
                        .isActive(t.getIsActive())
                        .build())
                .toList();
    }

    @Transactional
    public PayOSReturnResponse handlePayOSReturn(UUID uid, PayOSReturnRequest request) {
        PaymentOrder order = paymentOrderRepository.findByOrderCode(request.getOrderCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy đơn thanh toán: " + request.getOrderCode()));

        if (!order.getUser().getUid().equals(uid)) {
            throw new InvalidRequestException("Đơn thanh toán không thuộc về người dùng này");
        }

        PaymentOrderStatus current = order.getStatus();

        if (isTerminal(current)) {
            log.info("PayOS return: order {} already terminal ({}), skipping",
                    order.getOrderCode(), current);
            return buildResponse(order.getOrderCode(), current);
        }

        PaymentOrderStatus resolved = resolveStatus(request);

        if (resolved == PaymentOrderStatus.CANCELLED) {
            order.setStatus(PaymentOrderStatus.CANCELLED);
            paymentOrderRepository.save(order);
            log.info("PayOS return: order {} marked CANCELLED (user-initiated)", order.getOrderCode());
            return buildResponse(order.getOrderCode(), PaymentOrderStatus.CANCELLED);
        }

        if (resolved == PaymentOrderStatus.PAID) {
            log.info("PayOS return: order {} reported PAID by frontend; waiting for verified webhook",
                    order.getOrderCode());
            return PayOSReturnResponse.builder()
                    .orderCode(order.getOrderCode())
                    .resolvedStatus(current.name())
                    .message("Thanh toán đang được xác minh. Vui lòng chờ xác nhận.")
                    .build();
        }

        log.info("PayOS return: order {} status remains {} (PayOS status={})",
                order.getOrderCode(), current, request.getStatus());
        return buildResponse(order.getOrderCode(), current);
    }

    private boolean isTerminal(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.CANCELLED
                || status == PaymentOrderStatus.PAID
                || status == PaymentOrderStatus.EXPIRED
                || status == PaymentOrderStatus.FAILED;
    }

    private PaymentOrderStatus resolveStatus(PayOSReturnRequest request) {
        if (Boolean.TRUE.equals(request.getCancel())
                || "CANCELLED".equalsIgnoreCase(request.getStatus())) {
            return PaymentOrderStatus.CANCELLED;
        }
        if ("PAID".equalsIgnoreCase(request.getStatus())) {
            return PaymentOrderStatus.PAID;
        }
        return null;
    }

    private PayOSReturnResponse buildResponse(Long orderCode, PaymentOrderStatus status) {
        String message = switch (status) {
            case CANCELLED -> "Thanh toán đã bị hủy.";
            case PAID -> "Thanh toán đã được xác nhận.";
            case EXPIRED -> "Liên kết thanh toán đã hết hạn. Vui lòng tạo đơn hàng mới.";
            case FAILED -> "Thanh toán thất bại. Vui lòng thử lại.";
            default -> "Thanh toán đang chờ xử lý. Vui lòng chờ xác nhận.";
        };
        return PayOSReturnResponse.builder()
                .orderCode(orderCode)
                .resolvedStatus(status.name())
                .message(message)
                .build();
    }

    private Long generateOrderCode() {
        long millis = System.currentTimeMillis();
        int random = ThreadLocalRandom.current().nextInt(100, 999);
        return Long.parseLong(millis + "" + random);
    }

    @Transactional
    public SchoolTokenPurchaseResponse createSchoolTokenPurchase(
            CreateSchoolTokenPurchaseRequest request) throws Exception {
        String uidStr = SecurityUtils.getUserId();
        if (uidStr == null) {
            throw new InvalidRequestException("Vui lòng đăng nhập với tài khoản Quản trị viên Trường");
        }
        UUID uid = UUID.fromString(uidStr);
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản người dùng: " + uid));

        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với bất kỳ trường học nào");
        }
        School school = user.getSchool();

        if (request.getTokens() == null || request.getTokens() < 100_000) {
            throw new InvalidRequestException("Số lượng token mua thêm tối thiểu là 100.000 tokens");
        }

        // 0.15 VND per token
        int amount = (int) Math.round(request.getTokens() * 0.15);
        if (amount < 2_000) {
            amount = 2_000;
        }

        Instant expiredAtInstant = Instant.now().plus(15, ChronoUnit.MINUTES);
        long payosExpiredAt = expiredAtInstant.getEpochSecond();
        LocalDateTime expiredAtDb = LocalDateTime.ofInstant(
                expiredAtInstant,
                ZoneOffset.UTC
        );

        Long orderCode = generateOrderCode();
        String description = "HTTOK" + orderCode.toString().substring(orderCode.toString().length() - 6);

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .school(school)
                .orderType(PaymentOrderType.SCHOOL_TOKEN_TOPUP)
                .topupTokens(request.getTokens())
                .orderCode(orderCode)
                .amount(amount)
                .description(description)
                .status(PaymentOrderStatus.PENDING)
                .expiredAt(expiredAtDb)
                .isActive(true)
                .build();

        paymentOrderRepository.save(order);

        CreatePaymentLinkRequest payosRequest = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount((long) amount)
                .description(description)
                .returnUrl(payOSConfig.getReturnUrl())
                .cancelUrl(payOSConfig.getCancelUrl())
                .expiredAt(payosExpiredAt)
                .build();

        var paymentLink = payOS.paymentRequests().create(payosRequest);

        order.setPaymentLinkId(paymentLink.getPaymentLinkId());
        order.setCheckoutUrl(paymentLink.getCheckoutUrl());
        order.setQrCode(paymentLink.getQrCode());

        paymentOrderRepository.save(order);
        log.info("Created PayOS school token purchase checkout: orderCode={}, school={}, tokens={}, amount={}",
                orderCode, school.getSchoolCode(), request.getTokens(), amount);

        return SchoolTokenPurchaseResponse.builder()
                .orderId(order.getOrderId().toString())
                .orderCode(order.getOrderCode())
                .schoolId(school.getId().toString())
                .schoolName(school.getName())
                .tokens(request.getTokens())
                .amount(amount)
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .status(order.getStatus().name())
                .expiredAt(order.getExpiredAt() != null ? order.getExpiredAt().toString() : null)
                .createdAt(order.getCreatedAt() != null ? order.getCreatedAt().toString() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SchoolTokenPurchaseHistoryResponse> getSchoolTokenPurchaseHistory(
            Pageable pageable) {
        String uidStr = SecurityUtils.getUserId();
        if (uidStr == null) {
            throw new InvalidRequestException("Vui lòng đăng nhập với tài khoản Quản trị viên Trường");
        }
        UUID uid = UUID.fromString(uidStr);
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản người dùng: " + uid));

        if (user.getSchool() == null) {
            throw new InvalidRequestException("Tài khoản chưa được liên kết với bất kỳ trường học nào");
        }
        School school = user.getSchool();

        Page<PaymentOrder> orders = paymentOrderRepository.findBySchoolIdAndOrderTypeOrderByCreatedAtDesc(
                school.getId(), PaymentOrderType.SCHOOL_TOKEN_TOPUP, pageable);

        List<SchoolTokenPurchaseHistoryResponse> items = orders.getContent().stream()
                .map(o -> SchoolTokenPurchaseHistoryResponse.builder()
                        .orderId(o.getOrderId().toString())
                        .orderCode(o.getOrderCode())
                        .schoolId(school.getId().toString())
                        .schoolName(school.getName())
                        .tokens(o.getTopupTokens())
                        .amount(o.getAmount())
                        .status(o.getStatus() != null ? o.getStatus().name() : null)
                        .fulfillmentStatus(o.getFulfillmentStatus() != null ? o.getFulfillmentStatus().name() : null)
                        .paidAt(formatUtcIso(o.getPaidAt()))
                        .createdAt(formatUtcIso(o.getCreatedAt()))
                        .build())
                .toList();

        return PaginatedResponse.<SchoolTokenPurchaseHistoryResponse>builder()
                .content(items)
                .currentPage(orders.getNumber())
                .pageSize(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .hasNext(orders.hasNext())
                .hasPrevious(orders.hasPrevious())
                .build();
    }

    private String resolveTierId(PaymentOrder order) {
        return order.getTier() != null ? order.getTier().getTierId().toString() : null;
    }

    private String resolveTierTitle(PaymentOrder order) {
        if (order.getTier() != null) {
            return order.getTier().getTitle();
        }
        if (order.getOrderType() == PaymentOrderType.SCHOOL_TOKEN_TOPUP) {
            return order.getSchool() != null
                    ? "Nạp quỹ token trường — " + order.getSchool().getName()
                    : "Nạp quỹ token trường";
        }
        return "Gói dịch vụ";
    }

    private String formatUtcIso(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(value.atZone(ZoneOffset.UTC).toInstant());
    }
}
