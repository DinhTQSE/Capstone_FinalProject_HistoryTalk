package com.historytalk.controller.localcontent;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.localcontent.LocalContentResponse;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.localcontent.LocalContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Student endpoint to browse published local content from their school.
 *
 * <p><b>Access:</b> SCHOOL_STUDENT only.
 * <p><b>Rule (UC-14 from Role Matrix):</b>
 * Students see published local content belonging to their school.
 * classId param filters to content assigned to a specific classroom.
 */
@RestController
@RequestMapping("/api/v1/student/local-contents")
@PreAuthorize("hasRole('SCHOOL_STUDENT')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Local Content — Student", description = "Student: view published local history content from their school (UC-14)")
public class StudentLocalContentController {

    private final LocalContentService localContentService;

    // ── Context list ─────────────────────────────────────────────────────────

    @GetMapping("/contexts")
    @Operation(
            summary = "Danh sách bối cảnh địa phương đã duyệt",
            description = "Học sinh xem bối cảnh đã được School Admin duyệt (ACTIVE). " +
                    "Filter theo lớp bằng classId. Hỗ trợ search, phân trang.")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getPublishedLocalContexts(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String classId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> result =
                localContentService.getLocalContexts(search, "ACTIVE", page, size, principal);
        // classId filter: if provided, only return content assigned to that class
        if (classId != null && !classId.isBlank()) {
            var filtered = result.getContent().stream()
                    .filter(r -> r.getClassIds() != null && r.getClassIds().contains(classId))
                    .toList();
            result = PaginatedResponse.<LocalContentResponse>builder()
                    .content(filtered)
                    .currentPage(result.getCurrentPage())
                    .pageSize(result.getPageSize())
                    .totalElements((long) filtered.size())
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }
        return ResponseEntity.ok(ApiResponse.success(result,
                "Published local contexts retrieved — " + (result.getTotalElements() != null ? result.getTotalElements() : 0) + " found"));
    }

    @GetMapping("/contexts/{contextId}")
    @Operation(
            summary = "Chi tiết bối cảnh địa phương đã duyệt",
            description = "Học sinh xem chi tiết một bối cảnh cụ thể (chỉ khi đã ACTIVE).")
    public ResponseEntity<ApiResponse<LocalContentResponse>> getPublishedLocalContextById(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.getPublishedLocalContextById(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local context retrieved successfully"));
    }

    // ── Character list ────────────────────────────────────────────────────────

    @GetMapping("/characters")
    @Operation(
            summary = "Danh sách nhân vật địa phương đã duyệt",
            description = "Học sinh xem nhân vật đã được School Admin duyệt (ACTIVE). " +
                    "Filter theo lớp bằng classId. Hỗ trợ search, phân trang.")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getPublishedLocalCharacters(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String classId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> result =
                localContentService.getLocalCharacters(search, "ACTIVE", page, size, principal);
        if (classId != null && !classId.isBlank()) {
            var filtered = result.getContent().stream()
                    .filter(r -> r.getClassIds() != null && r.getClassIds().contains(classId))
                    .toList();
            result = PaginatedResponse.<LocalContentResponse>builder()
                    .content(filtered)
                    .currentPage(result.getCurrentPage())
                    .pageSize(result.getPageSize())
                    .totalElements((long) filtered.size())
                    .totalPages(1)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }
        return ResponseEntity.ok(ApiResponse.success(result,
                "Published local characters retrieved — " + (result.getTotalElements() != null ? result.getTotalElements() : 0) + " found"));
    }

    @GetMapping("/characters/{characterId}")
    @Operation(
            summary = "Chi tiết nhân vật địa phương đã duyệt",
            description = "Học sinh xem chi tiết một nhân vật cụ thể (chỉ khi đã ACTIVE).")
    public ResponseEntity<ApiResponse<LocalContentResponse>> getPublishedLocalCharacterById(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.getPublishedLocalCharacterById(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local character retrieved successfully"));
    }
}
