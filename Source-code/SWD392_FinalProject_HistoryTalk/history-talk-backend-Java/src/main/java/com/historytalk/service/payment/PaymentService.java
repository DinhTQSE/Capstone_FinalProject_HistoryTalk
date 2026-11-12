package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.payment.AdminPaymentHistoryResponse;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.TierResponse;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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

    @Transactional
    public CreatePaymentResponse createPayOSCheckout(UUID uid, String tierId) throws Exception {
        User user = userRepository.findById(uid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uid));

        Tier tier = tierRepository.findById(UUID.fromString(tierId))
                .orElseThrow(() -> new ResourceNotFoundException("Tier not found: " + tierId));

        if (!Boolean.TRUE.equals(tier.getIsActive()) || tier.getDeletedAt() != null) {
            throw new InvalidRequestException("Tier is inactive or deleted");
        }

        if (tier.getAmount() == null || tier.getAmount() <= 0) {
            throw new InvalidRequestException("Free tier does not require payment");
        }

        LocalDateTime guardNow = LocalDateTime.now();
        Optional<UserTier> activePaid = userTierRepository.findCurrentActiveByUid(uid, guardNow);
        if (activePaid.isPresent() && activePaid.get().getTier().getAmount() > 0) {
            throw new InvalidRequestException(
                    "You already have an active '" + activePaid.get().getTier().getTitle()
                            + "' subscription that expires on " + activePaid.get().getEndTime()
                            + ". Subscriptions cannot be stacked, upgraded, or cancelled mid-period.");
        }

        Instant expiredAtInstant = Instant.now().plus(15, ChronoUnit.MINUTES);
        long payosExpiredAt = expiredAtInstant.getEpochSecond();
        LocalDateTime expiredAtDb = LocalDateTime.ofInstant(
                expiredAtInstant,
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        Long orderCode = generateOrderCode();
        String description = "HISTALK" + orderCode.toString().substring(orderCode.toString().length() - 6);

        PaymentOrder order = PaymentOrder.builder()
                .user(user)
                .tier(tier)
                .orderCode(orderCode)
                .amount(tier.getAmount())
                .description(description)
                .status(PaymentOrderStatus.PENDING)
                .expiredAt(expiredAtDb)
                .isActive(true)
                .build();

        paymentOrderRepository.save(order);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(tier.getAmount().longValue())
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
        log.info("Created PayOS checkout: orderCode={}, user={}, tier={}", orderCode, uid, tier.getTitle());

        return CreatePaymentResponse.builder()
                .orderId(order.getOrderId().toString())
                .orderCode(order.getOrderCode())
                .paymentLinkId(order.getPaymentLinkId())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCode(order.getQrCode())
                .amount(order.getAmount())
                .status(order.getStatus().name())
                .expiredAt(order.getExpiredAt() != null ? order.getExpiredAt().toString() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryResponse> getMyPaymentHistory(UUID uid) {
        List<PaymentOrder> orders = paymentOrderRepository.findByUser_UidOrderByCreatedAtDesc(uid);

        return orders.stream()
                .map(o -> PaymentHistoryResponse.builder()
                        .orderId(o.getOrderId().toString())
                        .orderCode(o.getOrderCode())
                        .tierId(o.getTier().getTierId().toString())
                        .tierTitle(o.getTier().getTitle())
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .paymentLinkId(o.getPaymentLinkId())
                        .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                        .paidAt(o.getPaidAt() != null ? o.getPaidAt().toString() : null)
                        .expiredAt(o.getExpiredAt() != null ? o.getExpiredAt().toString() : null)
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
                        .tierId(o.getTier().getTierId().toString())
                        .tierTitle(o.getTier().getTitle())
                        .amount(o.getAmount())
                        .status(o.getStatus().name())
                        .fulfillmentStatus(o.getFulfillmentStatus() != null ? o.getFulfillmentStatus().name() : null)
                        .fulfilledAt(o.getFulfilledAt() != null ? o.getFulfilledAt().toString() : null)
                        .fulfillmentAttempts(o.getFulfillmentAttempts())
                        .fulfillmentError(o.getFulfillmentError())
                        .paymentLinkId(o.getPaymentLinkId())
                        .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                        .paidAt(o.getPaidAt() != null ? o.getPaidAt().toString() : null)
                        .expiredAt(o.getExpiredAt() != null ? o.getExpiredAt().toString() : null)
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
                        "Payment order not found: " + request.getOrderCode()));

        if (!order.getUser().getUid().equals(uid)) {
            throw new InvalidRequestException("Payment order does not belong to this user");
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
                    .message("Payment is being verified. Please wait for confirmation.")
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
            case CANCELLED -> "Payment has been cancelled.";
            case PAID -> "Payment has already been confirmed.";
            case EXPIRED -> "Payment link has expired. Please create a new order.";
            case FAILED -> "Payment failed. Please try again.";
            default -> "Payment is pending. Please wait for confirmation.";
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
                ZoneId.of("Asia/Ho_Chi_Minh")
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
                        .paidAt(o.getPaidAt() != null ? o.getPaidAt().toString() : null)
                        .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
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
}
