package com.historytalk.service.payment;

import com.historytalk.config.PayOSConfig;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.payment.AdminPaymentHistoryResponse;
import com.historytalk.dto.payment.CreatePaymentResponse;
import com.historytalk.dto.payment.PaymentHistoryResponse;
import com.historytalk.dto.payment.PayOSReturnRequest;
import com.historytalk.dto.payment.PayOSReturnResponse;
import com.historytalk.dto.payment.TierResponse;
import com.historytalk.entity.enums.PaymentOrderStatus;
import com.historytalk.entity.payment.PaymentOrder;
import com.historytalk.entity.payment.Tier;
import com.historytalk.entity.payment.UserTier;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.payment.PaymentOrderRepository;
import com.historytalk.repository.payment.TierRepository;
import com.historytalk.repository.payment.UserTierRepository;
import com.historytalk.repository.UserRepository;

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

        // Block purchase if user already has a live paid subscription
        LocalDateTime guardNow = LocalDateTime.now();
        Optional<UserTier> activePaid = userTierRepository.findCurrentActiveByUid(uid, guardNow);
        if (activePaid.isPresent() && activePaid.get().getTier().getAmount() > 0) {
            throw new InvalidRequestException(
                "You already have an active \'" + activePaid.get().getTier().getTitle()
                + "\' subscription that expires on " + activePaid.get().getEndTime()
                + ". Subscriptions cannot be stacked, upgraded, or cancelled mid-period.");
        }

//        LocalDateTime now = LocalDateTime.now();
//        LocalDateTime expiredAt = now.plusMinutes(15);
        Instant expiredAtInstant = Instant.now().plus(15, ChronoUnit.MINUTES);

        long payosExpiredAt = expiredAtInstant.getEpochSecond();

        LocalDateTime expiredAtDb = LocalDateTime.ofInstant(
                expiredAtInstant,
                ZoneId.of("Asia/Ho_Chi_Minh")
        );

        Long orderCode = generateOrderCode();

        // PayOS description must be short; many banks cap at 25 characters
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
//                .expiredAt(expiredAt.toEpochSecond(ZoneOffset.ofHours(7)))
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

    /**
     * Returns the authenticated customer's own payment order history, newest first.
     * Called from GET /api/v1/payments/me.
     */
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

    /**
     * Returns all customers' payment order history for SYSTEM_ADMIN.
     * Called from GET /api/v1/payments/history.
     *
     * Both filters are optional — pass null to omit.
     * Results are paginated, newest first.
     */
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
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Lists all active tiers available for purchase.
     */
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

    /**
     * Handles the PayOS return-URL callback forwarded by the frontend.
     *
     * PayOS redirects the browser to cancelUrl/returnUrl with query params
     * (code, id, cancel, status, orderCode). The frontend POSTs those params
     * here so the backend can synchronise the order status without waiting for
     * the async webhook.
     *
     * Rules:
     * - Requires JWT; uid must match the order owner (ownership guard).
     * - Idempotent: if the order is already terminal (CANCELLED/PAID/EXPIRED/FAILED)
     *   the method returns immediately without writing to the DB.
     * - PROCESSING from PayOS is treated as PENDING (no state change).
     * - This method NEVER upgrades the user's tier. Tier upgrade is exclusively
     *   handled by PaymentWebhookService.handlePaid() after HMAC verification.
     */
    @Transactional
    public PayOSReturnResponse handlePayOSReturn(UUID uid, PayOSReturnRequest request) {
        // 1. Look up the order
        PaymentOrder order = paymentOrderRepository.findByOrderCode(request.getOrderCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment order not found: " + request.getOrderCode()));

        // 2. Ownership guard
        if (!order.getUser().getUid().equals(uid)) {
            throw new InvalidRequestException("Order does not belong to this user");
        }

        PaymentOrderStatus current = order.getStatus();

        // 3. Idempotency guard — return immediately if already terminal
        if (isTerminal(current)) {
            log.info("PayOS return: order {} already terminal ({}), skipping",
                    order.getOrderCode(), current);
            return buildResponse(order.getOrderCode(), current);
        }

        // 4. Resolve new status from PayOS params
        PaymentOrderStatus resolved = resolveStatus(request);

        // 5. Persist if status changed
        if (resolved == PaymentOrderStatus.CANCELLED) {
            order.setStatus(PaymentOrderStatus.CANCELLED);
            paymentOrderRepository.save(order);
            log.info("PayOS return: order {} marked CANCELLED (user-initiated)", order.getOrderCode());

        } else if (resolved == PaymentOrderStatus.PAID) {
            // UI-only sync — tier upgrade must come from the verified webhook
            order.setStatus(PaymentOrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            paymentOrderRepository.save(order);
            log.info("PayOS return: order {} status synced to PAID (UI only, webhook handles tier)",
                    order.getOrderCode());

        } else {
            // PENDING / PROCESSING — no state change, just echo back
            log.info("PayOS return: order {} status remains {} (PayOS status={})",
                    order.getOrderCode(), current, request.getStatus());
        }

        PaymentOrderStatus finalStatus = (resolved != null) ? resolved : current;
        return buildResponse(order.getOrderCode(), finalStatus);
    }

    // -------------------------------------------------------------------------
    // Return-URL helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the order is in a terminal state that should not be
     * overwritten by a late return-URL call.
     */
    private boolean isTerminal(PaymentOrderStatus status) {
        return status == PaymentOrderStatus.CANCELLED
                || status == PaymentOrderStatus.PAID
                || status == PaymentOrderStatus.EXPIRED
                || status == PaymentOrderStatus.FAILED;
    }

    /**
     * Maps PayOS return params to our internal PaymentOrderStatus.
     * PROCESSING is intentionally treated as PENDING (no enum value added).
     * Returns null for PENDING/PROCESSING to signal "no state change".
     */
    private PaymentOrderStatus resolveStatus(PayOSReturnRequest request) {
        if (Boolean.TRUE.equals(request.getCancel())
                || "CANCELLED".equalsIgnoreCase(request.getStatus())) {
            return PaymentOrderStatus.CANCELLED;
        }
        if ("PAID".equalsIgnoreCase(request.getStatus())) {
            return PaymentOrderStatus.PAID;
        }
        // PENDING or PROCESSING — no change
        return null;
    }

    /**
     * Builds the response with a human-readable message keyed on status.
     */
    private PayOSReturnResponse buildResponse(Long orderCode, PaymentOrderStatus status) {
        String message = switch (status) {
            case CANCELLED -> "Payment has been cancelled.";
            case PAID      -> "Payment has already been confirmed.";
            case EXPIRED   -> "Payment link has expired. Please create a new order.";
            case FAILED    -> "Payment failed. Please try again.";
            default        -> "Payment is pending. Please wait for confirmation.";
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
}
