package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.quote.ApproveB2bQuoteResponse;
import com.historytalk.dto.school.quote.B2bPlanCatalogResponse;
import com.historytalk.dto.school.quote.B2bQuoteCalculationResponse;
import com.historytalk.dto.school.quote.B2bQuoteResponse;
import com.historytalk.dto.school.quote.CalculateB2bQuoteRequest;
import com.historytalk.dto.school.quote.SubmitB2bQuoteRequest;
import com.historytalk.dto.school.quote.UpdateB2bQuoteStatusRequest;
import com.historytalk.entity.enums.B2bQuoteStatus;
import com.historytalk.service.school.B2bQuoteService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "B2B Subscription & Quote Flow", description = "Public B2B pricing calculator, Humy-style quote registration, and System Admin quote approval")
public class B2bQuoteController {

    private final B2bQuoteService b2bQuoteService;

    @GetMapping("/api/v1/b2b/plans")
    @Operation(summary = "Get B2B school subscription plan catalog and volume discount tiers (Public)")
    public ResponseEntity<ApiResponse<B2bPlanCatalogResponse>> getPlanCatalog() {
        B2bPlanCatalogResponse catalog = b2bQuoteService.getB2bPlanCatalog();
        return ResponseEntity.ok(ApiResponse.success(catalog, "Lấy danh mục gói B2B trường học thành công"));
    }

    @PostMapping("/api/v1/b2b/quotes/calculate")
    @Operation(summary = "Live B2B quote calculator for fixed or custom student/teacher counts (Public)")
    public ResponseEntity<ApiResponse<B2bQuoteCalculationResponse>> calculateQuote(
            @Valid @RequestBody CalculateB2bQuoteRequest request) {
        B2bQuoteCalculationResponse calculation = b2bQuoteService.calculateQuote(request);
        return ResponseEntity.ok(ApiResponse.success(calculation, "Tính toán báo giá gói trường học thành công"));
    }

    @PostMapping("/api/v1/b2b/quotes/request")
    @Operation(summary = "Submit a B2B quote & school registration request (Public)")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> submitQuoteRequest(
            @Valid @RequestBody SubmitB2bQuoteRequest request) {
        B2bQuoteResponse response = b2bQuoteService.submitQuoteRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Gửi yêu cầu báo giá và đăng ký trường học thành công"));
    }

    @GetMapping("/api/v1/admin/b2b-quotes")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "List B2B quote requests with status and search filter (System Admin)")
    public ResponseEntity<ApiResponse<PaginatedResponse<B2bQuoteResponse>>> getQuotes(
            @RequestParam(required = false) B2bQuoteStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PaginatedResponse<B2bQuoteResponse> result = b2bQuoteService.getQuoteRequests(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Lấy danh sách yêu cầu báo giá B2B thành công"));
    }

    @GetMapping("/api/v1/admin/b2b-quotes/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get B2B quote request details by ID (System Admin)")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> getQuoteById(@PathVariable UUID id) {
        B2bQuoteResponse response = b2bQuoteService.getQuoteRequestById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Lấy chi tiết báo giá B2B thành công"));
    }

    @PatchMapping("/api/v1/admin/b2b-quotes/{id}/status")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Update B2B quote request status (CONTACTED / REJECTED) (System Admin)")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> updateQuoteStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateB2bQuoteStatusRequest request) {
        UUID reviewerUid = resolveReviewerUid();
        B2bQuoteResponse response = b2bQuoteService.updateQuoteStatus(id, request, reviewerUid);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật trạng thái báo giá B2B thành công"));
    }

    @PostMapping("/api/v1/admin/b2b-quotes/{id}/approve")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Approve B2B quote request and automatically provision School + School Admin account (System Admin)")
    public ResponseEntity<ApiResponse<ApproveB2bQuoteResponse>> approveQuoteAndProvisionSchool(
            @PathVariable UUID id) {
        UUID reviewerUid = resolveReviewerUid();
        ApproveB2bQuoteResponse response = b2bQuoteService.approveQuoteAndProvisionSchool(id, reviewerUid);
        return ResponseEntity.ok(ApiResponse.success(response, "Phê duyệt báo giá và khởi tạo trường học thành công"));
    }

    private UUID resolveReviewerUid() {
        String userId = SecurityUtils.getUserId();
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
