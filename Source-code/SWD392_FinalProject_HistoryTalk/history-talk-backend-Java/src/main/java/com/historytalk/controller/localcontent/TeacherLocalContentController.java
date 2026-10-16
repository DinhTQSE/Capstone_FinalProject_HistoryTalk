package com.historytalk.controller.localcontent;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historytalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historytalk.dto.localcontent.*;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.localcontent.LocalContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for Teacher managing their school's local history content.
 *
 * <p><b>Access:</b> TEACHER only (school must be linked via JWT principal).
 * <p><b>Base path:</b> {@code /api/v1/teacher/local-contents}
 *
 * <p><b>Local Content rules (Sprint 6 — US-SP6-02):</b>
 * <ul>
 *   <li>All created content is initially DRAFT.</li>
 *   <li>CLASS_ONLY scope — visible only to the creating teacher.</li>
 *   <li>SCHOOL_WIDE scope — visible to all teachers in the same school (read access).</li>
 *   <li>School Admin must PUBLISH before students can access via assignments.</li>
 *   <li>HistoryTalk platform bears zero responsibility for local content accuracy.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/teacher/local-contents")
@PreAuthorize("hasRole('TEACHER')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Local Content — Teacher", description = "Teacher: create & manage school-owned local history content (US-SP6-02)")
public class TeacherLocalContentController {

    private final LocalContentService localContentService;

    // ══════════════════════════════════════════════════════════════════════════
    // LOCAL CONTEXTS
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/contexts")
    @Operation(
            summary = "Tạo bối cảnh lịch sử địa phương",
            description = "Teacher tạo bối cảnh mới, mặc định DRAFT và scope CLASS_ONLY. " +
                    "Đặt contentScope=SCHOOL_WIDE để chia sẻ vào kho nội dung toàn trường.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> createLocalContext(
            @Valid @RequestBody CreateLocalContextRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.createLocalContext(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Local context created successfully (DRAFT)"));
    }

    @GetMapping("/contexts")
    @Operation(
            summary = "Danh sách bối cảnh địa phương mà Teacher có thể thấy",
            description = "Trả về: (1) tất cả context Teacher tự tạo, " +
                    "(2) context SCHOOL_WIDE của GV khác cùng trường. " +
                    "Filter theo status: DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE.")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getLocalContexts(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> response =
                localContentService.getLocalContexts(search, status, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local contexts retrieved successfully"));
    }

    @GetMapping("/contexts/{contextId}")
    @Operation(summary = "Chi tiết bối cảnh địa phương theo ID")
    public ResponseEntity<ApiResponse<LocalContentResponse>> getLocalContextById(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.getLocalContextById(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local context retrieved successfully"));
    }

    @PutMapping("/contexts/{contextId}")
    @Operation(
            summary = "Cập nhật bối cảnh địa phương",
            description = "Chỉ Teacher là tác giả mới được sửa. " +
                    "Thay đổi contentScope sẽ reset trạng thái về DRAFT và xóa approval.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> updateLocalContext(
            @PathVariable String contextId,
            @Valid @RequestBody UpdateLocalContextRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.updateLocalContext(contextId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local context updated successfully"));
    }

    @DeleteMapping("/contexts/{contextId}")
    @Operation(summary = "Xóa mềm bối cảnh địa phương (soft-delete)")
    public ResponseEntity<ApiResponse<Void>> deleteLocalContext(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalContext(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Local context deleted successfully"));
    }

    @PutMapping("/contexts/{contextId}/submit")
    @Operation(
            summary = "Gửi bối cảnh lên để Admin duyệt",
            description = "Chuyển trạng thái từ DRAFT → PENDING_REVIEW. Admin sẽ thấy trong queue chờ duyệt.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> submitLocalContext(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.submitLocalContext(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Context submitted for review successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCAL CHARACTERS
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/characters")
    @Operation(
            summary = "Tạo nhân vật lịch sử địa phương",
            description = "Teacher tạo nhân vật mới gắn với trường, mặc định DRAFT + CLASS_ONLY.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> createLocalCharacter(
            @Valid @RequestBody CreateLocalCharacterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.createLocalCharacter(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Local character created successfully (DRAFT)"));
    }

    @GetMapping("/characters")
    @Operation(
            summary = "Danh sách nhân vật địa phương mà Teacher có thể thấy",
            description = "Filter theo status: DRAFT | PENDING_REVIEW | ACTIVE | INACTIVE.")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getLocalCharacters(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<LocalContentResponse> response =
                localContentService.getLocalCharacters(search, status, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local characters retrieved successfully"));
    }

    @GetMapping("/characters/{characterId}")
    @Operation(summary = "Chi tiết nhân vật địa phương theo ID")
    public ResponseEntity<ApiResponse<LocalContentResponse>> getLocalCharacterById(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.getLocalCharacterById(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local character retrieved successfully"));
    }

    @PutMapping("/characters/{characterId}")
    @Operation(
            summary = "Cập nhật nhân vật địa phương",
            description = "Chỉ Teacher là tác giả mới được sửa.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> updateLocalCharacter(
            @PathVariable String characterId,
            @Valid @RequestBody UpdateLocalCharacterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.updateLocalCharacter(characterId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Local character updated successfully"));
    }

    @DeleteMapping("/characters/{characterId}")
    @Operation(summary = "Xóa mềm nhân vật địa phương")
    public ResponseEntity<ApiResponse<Void>> deleteLocalCharacter(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalCharacter(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Local character deleted successfully"));
    }

    @PutMapping("/characters/{characterId}/submit")
    @Operation(
            summary = "Gửi nhân vật lên để Admin duyệt",
            description = "Chuyển trạng thái từ DRAFT → PENDING_REVIEW. Admin sẽ thấy trong queue chờ duyệt.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> submitLocalCharacter(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.submitLocalCharacter(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Character submitted for review successfully"));
    }

    // ── Document endpoints — Context ──────────────────────────────────────────

    @GetMapping("/contexts/{contextId}/documents")
    @Operation(
            summary = "Danh sách tài liệu của bối cảnh địa phương",
            description = "Trả về tất cả document không bị xóa của local context. " +
                    "Chỉ Teacher thuộc cùng trường mới xem được.")
    public ResponseEntity<ApiResponse<java.util.List<HistoricalContextDocumentResponse>>> getLocalContextDocuments(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var docs = localContentService.getLocalContextDocuments(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(docs, "Documents retrieved — " + docs.size() + " found"));
    }

    @PostMapping("/contexts/{contextId}/documents")
    @Operation(
            summary = "Thêm tài liệu cho bối cảnh địa phương",
            description = "Upload/attach tài liệu (text, URL) vào local context. " +
                    "Nội dung sẽ được gửi async lên AI backend để embed vào vector store.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> addLocalContextDocument(
            @PathVariable String contextId,
            @Valid @RequestBody CreateHistoricalContextDocumentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.addLocalContextDocument(contextId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doc, "Document added to local context successfully"));
    }

    @DeleteMapping("/contexts/{contextId}/documents/{docId}")
    @Operation(
            summary = "Xóa mềm tài liệu khỏi bối cảnh địa phương",
            description = "Chỉ người upload tài liệu mới xóa được.")
    public ResponseEntity<ApiResponse<Void>> deleteLocalContextDocument(
            @PathVariable String contextId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalContextDocument(contextId, docId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted successfully"));
    }

    // ── Document endpoints — Character ────────────────────────────────────────

    @GetMapping("/characters/{characterId}/documents")
    @Operation(
            summary = "Danh sách tài liệu của nhân vật địa phương",
            description = "Trả về tất cả document không bị xóa của local character. " +
                    "Chỉ Teacher thuộc cùng trường mới xem được.")
    public ResponseEntity<ApiResponse<java.util.List<HistoricalContextDocumentResponse>>> getLocalCharacterDocuments(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var docs = localContentService.getLocalCharacterDocuments(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(docs, "Documents retrieved — " + docs.size() + " found"));
    }

    @PostMapping("/characters/{characterId}/documents")
    @Operation(
            summary = "Thêm tài liệu cho nhân vật địa phương",
            description = "Upload/attach tài liệu vào local character. Nội dung sẽ được embed vào AI backend.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> addLocalCharacterDocument(
            @PathVariable String characterId,
            @Valid @RequestBody CreateHistoricalContextDocumentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.addLocalCharacterDocument(characterId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doc, "Document added to local character successfully"));
    }

    @DeleteMapping("/characters/{characterId}/documents/{docId}")
    @Operation(
            summary = "Xóa mềm tài liệu khỏi nhân vật địa phương",
            description = "Chỉ người upload tài liệu mới xóa được.")
    public ResponseEntity<ApiResponse<Void>> deleteLocalCharacterDocument(
            @PathVariable String characterId,
            @PathVariable String docId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalCharacterDocument(characterId, docId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted successfully"));
    }
}
