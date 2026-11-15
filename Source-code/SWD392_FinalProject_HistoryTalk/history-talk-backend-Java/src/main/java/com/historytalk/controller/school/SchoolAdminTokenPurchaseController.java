package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolTokenPurchaseRequest;
import com.historytalk.dto.school.SchoolTokenPurchaseHistoryResponse;
import com.historytalk.dto.school.SchoolTokenPurchaseResponse;
import com.historytalk.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/school-admin/tokens")
@RequiredArgsConstructor
@Tag(name = "School Token Purchase (PayOS Self-Service)",
        description = "Endpoints for School Admin to purchase additional tokens directly via PayOS without approval")
public class SchoolAdminTokenPurchaseController {

    private final PaymentService paymentService;

    @PostMapping("/purchase")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Create PayOS checkout link and VietQR code to purchase additional tokens for school")
    public ResponseEntity<ApiResponse<SchoolTokenPurchaseResponse>> purchaseTokens(
            @Valid @RequestBody CreateSchoolTokenPurchaseRequest request) throws Exception {
        log.info("POST /api/v1/school-admin/tokens/purchase: tokens={}", request.getTokens());
        SchoolTokenPurchaseResponse response = paymentService.createSchoolTokenPurchase(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Khởi tạo đơn mua token qua PayOS thành công"));
    }

    @GetMapping("/purchase-history")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Get purchase history of additional tokens for the authenticated school")
    public ResponseEntity<ApiResponse<PaginatedResponse<SchoolTokenPurchaseHistoryResponse>>> getPurchaseHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        log.info("GET /api/v1/school-admin/tokens/purchase-history: page={}, size={}", page, size);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        PaginatedResponse<SchoolTokenPurchaseHistoryResponse> history =
                paymentService.getSchoolTokenPurchaseHistory(pageable);
        return ResponseEntity.ok(ApiResponse.success(history, "Lấy lịch sử mua token thành công"));
    }
}
