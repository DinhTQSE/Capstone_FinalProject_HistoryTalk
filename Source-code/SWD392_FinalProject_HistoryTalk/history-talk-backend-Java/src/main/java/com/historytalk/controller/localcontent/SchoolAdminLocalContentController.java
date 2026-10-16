package com.historytalk.controller.localcontent;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.localcontent.*;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.localcontent.LocalContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for School Admin reviewing and approving local history content.
 *
 * <p><b>Access:</b> SCHOOL_ADMIN only.
 * <p><b>Base path:</b> {@code /api/v1/school-admin/local-contents}
 *
 * <p><b>Approval workflow (Sprint 6 — US-SP6-03):</b>
 * <ul>
 *   <li>Teacher creates content → DRAFT.</li>
 *   <li>School Admin reviews and sets status to PUBLISHED (ACTIVE), DRAFT (revert), or INACTIVE.</li>
 *   <li>On PUBLISHED: {@code approved_by_school_admin_id} is recorded for audit.</li>
 *   <li>Policy Disclaimer: HistoryTalk platform bears zero legal responsibility for accuracy
 *       of local content. The school assumes full responsibility upon publishing.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/school-admin/local-contents")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Local Content — School Admin", description = "School Admin: review & approve local history content (US-SP6-03)")
public class SchoolAdminLocalContentController {

    private final LocalContentService localContentService;

    // ══════════════════════════════════════════════════════════════════════════
    // REVIEW — Contexts
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/contexts")
    @Operation(
            summary = "Xem toàn bộ bối cảnh địa phương của trường",
            description = "School Admin xem danh sách tất cả bối cảnh do GV trong trường tạo, " +
                    "có thể lọc theo scope (CLASS_ONLY / SCHOOL_WIDE).")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getSchoolLocalContexts(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> response =
                localContentService.getSchoolLocalContexts(search, scope, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "School local contexts retrieved successfully"));
    }

    @PutMapping("/contexts/{contextId}/status")
    @Operation(
            summary = "Duyệt / thay đổi trạng thái bối cảnh địa phương",
            description = """
                    Thay đổi trạng thái của một bối cảnh lịch sử địa phương.
                    
                    Các giá trị `status` hợp lệ:
                    - **ACTIVE** → Duyệt & publish (isPublished=true, lưu approved_by).
                    - **DRAFT**  → Trả lại giáo viên chỉnh sửa (isPublished=false, xóa approved_by).
                    - **INACTIVE** → Ẩn tạm thời (isPublished=false, giữ approved_by để audit).
                    
                    ⚠️ **Policy Disclaimer:** Bằng cách duyệt (ACTIVE), nhà trường xác nhận chịu toàn bộ
                    trách nhiệm pháp lý về tính xác thực của nội dung. HistoryTalk không chịu trách nhiệm.
                    """)
    public ResponseEntity<ApiResponse<LocalContentResponse>> approveLocalContext(
            @PathVariable String contextId,
            @Valid @RequestBody UpdateLocalContentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.approveLocalContext(contextId, request, principal);
        String message = buildApprovalMessage("context", request);
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REVIEW — Characters
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/characters")
    @Operation(
            summary = "Xem toàn bộ nhân vật địa phương của trường",
            description = "School Admin xem danh sách tất cả nhân vật do GV trong trường tạo.")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getSchoolLocalCharacters(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String scope,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> response =
                localContentService.getSchoolLocalCharacters(search, scope, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "School local characters retrieved successfully"));
    }

    @PutMapping("/characters/{characterId}/status")
    @Operation(
            summary = "Duyệt / thay đổi trạng thái nhân vật địa phương",
            description = """
                    Thay đổi trạng thái của một nhân vật lịch sử địa phương.
                    
                    Các giá trị `status` hợp lệ: **ACTIVE**, **DRAFT**, **INACTIVE**.
                    
                    ⚠️ **Policy Disclaimer:** Tương tự bối cảnh — nhà trường chịu toàn bộ trách nhiệm
                    về tính xác thực của nhân vật địa phương sau khi duyệt.
                    """)
    public ResponseEntity<ApiResponse<LocalContentResponse>> approveLocalCharacter(
            @PathVariable String characterId,
            @Valid @RequestBody UpdateLocalContentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.approveLocalCharacter(characterId, request, principal);
        String message = buildApprovalMessage("character", request);
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private String buildApprovalMessage(String type, UpdateLocalContentStatusRequest req) {
        return switch (req.getStatus()) {
            case ACTIVE   -> "Local " + type + " published successfully. School assumes full content responsibility.";
            case DRAFT    -> "Local " + type + " reverted to DRAFT.";
            case INACTIVE -> "Local " + type + " set to INACTIVE.";
        };
    }
}
