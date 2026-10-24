package com.historytalk.controller.localcontent;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historytalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historytalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;
import com.historytalk.dto.quiz.CreateQuizRequest;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.dto.quiz.QuestionResponse;
import com.historytalk.dto.quiz.QuizStaffResponse;
import com.historytalk.dto.quiz.UpdateQuizRequest;
import com.historytalk.dto.localcontent.*;
import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.localcontent.LocalContentService;
import com.historytalk.service.media.MediaService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for Teacher managing their school's local history content.
 *
 * <p><b>Access:</b> TEACHER only (school must be linked via JWT principal).
 * <p><b>Base path:</b> {@code /api/v1/teacher/local-contents}
 */
@RestController
@RequestMapping("/api/v1/teacher/local-contents")
@PreAuthorize("hasRole('TEACHER')")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
@Tag(name = "Local Content — Teacher", description = "Teacher: create & manage school-owned local history content (US-SP6-02)")
public class TeacherLocalContentController {

    private final LocalContentService localContentService;
    private final MediaService mediaService;
    private final com.historytalk.service.media.MapImageUploadService mapImageUploadService;
    private final com.historytalk.service.media.VideoUploadService videoUploadService;

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
                .body(ApiResponse.success(response, "Local context created successfully"));
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
            description = "Chỉ Teacher là tác giả mới được sửa.")
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
            summary = "Xuất bản bối cảnh lên kho tư liệu trường",
            description = "Giáo viên tự do xuất bản bối cảnh (chuyển sang ACTIVE) để hiển thị cho học sinh.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> submitLocalContext(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.submitLocalContext(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Context published successfully"));
    }

    @PutMapping("/contexts/{contextId}/status")
    @Operation(
            summary = "Giáo viên thay đổi trạng thái bối cảnh",
            description = "Giáo viên cập nhật trạng thái bối cảnh của mình (ACTIVE / DRAFT / INACTIVE).")
    public ResponseEntity<ApiResponse<LocalContentResponse>> updateLocalContextStatus(
            @PathVariable String contextId,
            @Valid @RequestBody UpdateLocalContentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.updateLocalContextStatus(contextId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Context status updated to " + request.getStatus()));
    }

    // ── Media endpoints — Context ─────────────────────────────────────────────

    @PostMapping(value = "/contexts/{contextId}/media/upload-direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload ảnh/video trực tiếp cho bối cảnh địa phương",
            description = "Teacher upload ảnh (mediaType=IMAGE_2D, tối đa 10MB) hoặc video (mediaType=VIDEO: MP4/WebM/Ogg/MOV, tối đa 50MB) " +
                    "cho bối cảnh mình tạo (1 bước). Hệ thống tự cập nhật imageUrl / videoUrl; upload lại cùng mediaType sẽ thay file cũ.")
    public ResponseEntity<ApiResponse<DocumentMediaMetadataResponse>> uploadContextMediaDirect(
            @PathVariable String contextId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mediaType", defaultValue = "IMAGE_2D") String mediaType,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "height", required = false) Integer height) {

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        DocumentMediaMetadataResponse response = mediaService.uploadDirectBinary(
                contextUuid, EntityType.CONTEXT, file, mediaType, width, height, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Media uploaded successfully"));
    }

    @GetMapping("/contexts/{contextId}/media/view-url")
    @Operation(summary = "Lấy signed view URL cho media của bối cảnh")
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> getContextMediaViewUrl(
            @PathVariable String contextId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) Integer thumbnailWidth,
            @RequestParam(required = false) Integer thumbnailHeight) {

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        SignedViewUrlResponse response = mediaService.generateSignedViewUrl(
                contextUuid, EntityType.CONTEXT, mediaType, thumbnailWidth, thumbnailHeight, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "View URL generated successfully"));
    }

    @DeleteMapping("/contexts/{contextId}/media")
    @Operation(summary = "Xóa media khỏi bối cảnh",
            description = "mediaType=IMAGE_2D|VIDEO chỉ xóa loại đó (kể cả URL nhập tay); bỏ trống để xóa toàn bộ media.")
    public ResponseEntity<ApiResponse<Void>> deleteContextMedia(
            @PathVariable String contextId,
            @RequestParam(required = false) String mediaType) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        mediaService.deleteMedia(contextUuid, EntityType.CONTEXT, mediaType, userId, userRole);
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCAL CHARACTERS
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/characters")
    @Operation(
            summary = "Tạo nhân vật lịch sử địa phương",
            description = "Teacher tạo nhân vật mới gắn với trường, tự do xuất bản vào kho tư liệu trường.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> createLocalCharacter(
            @Valid @RequestBody CreateLocalCharacterRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.createLocalCharacter(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Local character created successfully"));
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
            summary = "Xuất bản nhân vật lên kho tư liệu trường",
            description = "Giáo viên tự do xuất bản nhân vật (chuyển sang ACTIVE) để hiển thị cho học sinh.")
    public ResponseEntity<ApiResponse<LocalContentResponse>> submitLocalCharacter(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.submitLocalCharacter(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Character published successfully"));
    }

    @PutMapping("/characters/{characterId}/status")
    @Operation(
            summary = "Giáo viên thay đổi trạng thái nhân vật",
            description = "Giáo viên cập nhật trạng thái nhân vật của mình (ACTIVE / DRAFT / INACTIVE).")
    public ResponseEntity<ApiResponse<LocalContentResponse>> updateLocalCharacterStatus(
            @PathVariable String characterId,
            @Valid @RequestBody UpdateLocalContentStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        LocalContentResponse response = localContentService.updateLocalCharacterStatus(characterId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Character status updated to " + request.getStatus()));
    }

    // ── Media endpoints — Character ───────────────────────────────────────────

    @PostMapping(value = "/characters/{characterId}/media/upload-direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload media/ảnh trực tiếp cho nhân vật địa phương",
            description = "Teacher upload file ảnh (IMAGE_2D) hoặc model (MODEL_3D) cho nhân vật mình tạo (1 bước). Upload lại cùng mediaType sẽ thay file cũ.")
    public ResponseEntity<ApiResponse<DocumentMediaMetadataResponse>> uploadCharacterMediaDirect(
            @PathVariable String characterId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mediaType", defaultValue = "IMAGE_2D") String mediaType,
            @RequestParam(value = "width", required = false) Integer width,
            @RequestParam(value = "height", required = false) Integer height) {

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID characterUuid = UUID.fromString(characterId);
        DocumentMediaMetadataResponse response = mediaService.uploadDirectBinary(
                characterUuid, EntityType.CHARACTER, file, mediaType, width, height, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Media uploaded successfully"));
    }

    @GetMapping("/characters/{characterId}/media/view-url")
    @Operation(summary = "Lấy signed view URL cho media của nhân vật")
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> getCharacterMediaViewUrl(
            @PathVariable String characterId,
            @RequestParam(required = false) String mediaType,
            @RequestParam(required = false) Integer thumbnailWidth,
            @RequestParam(required = false) Integer thumbnailHeight) {

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID characterUuid = UUID.fromString(characterId);
        SignedViewUrlResponse response = mediaService.generateSignedViewUrl(
                characterUuid, EntityType.CHARACTER, mediaType, thumbnailWidth, thumbnailHeight, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "View URL generated successfully"));
    }

    @DeleteMapping("/characters/{characterId}/media")
    @Operation(summary = "Xóa media khỏi nhân vật",
            description = "mediaType=IMAGE_2D|MODEL_3D chỉ xóa loại đó; bỏ trống để xóa toàn bộ media.")
    public ResponseEntity<ApiResponse<Void>> deleteCharacterMedia(
            @PathVariable String characterId,
            @RequestParam(required = false) String mediaType) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID characterUuid = UUID.fromString(characterId);
        mediaService.deleteMedia(characterUuid, EntityType.CHARACTER, mediaType, userId, userRole);
        return ResponseEntity.noContent().build();
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

    @PostMapping(value = "/contexts/{contextId}/documents/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload trực tiếp file PDF làm tài liệu cho bối cảnh địa phương",
            description = "Giáo viên tải file PDF lên cho bối cảnh địa phương (1 bước). Hệ thống tự động trích xuất văn bản, lưu trữ PDF và đẩy sang AI vector store.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> uploadLocalContextPdfDocument(
            @PathVariable String contextId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.uploadLocalContextPdfDocument(contextId, file, title, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doc, "PDF document uploaded to local context successfully"));
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

    @PostMapping(value = "/characters/{characterId}/documents/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload trực tiếp file PDF làm tài liệu cho nhân vật địa phương",
            description = "Giáo viên tải file PDF lên cho nhân vật địa phương (1 bước). Hệ thống tự động trích xuất văn bản, lưu trữ PDF và đẩy sang AI vector store.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> uploadLocalCharacterPdfDocument(
            @PathVariable String characterId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.uploadLocalCharacterPdfDocument(characterId, file, title, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(doc, "PDF document uploaded to local character successfully"));
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

    // ══════════════════════════════════════════════════════════════════════════
    // MEDIA UPLOAD
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping(value = "/media/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Giáo viên tải ảnh lên kho lưu trữ trường",
            description = "Tải file ảnh (PNG, JPG, WebP tối đa 2MB) và nhận URL công khai để điền vào imageUrl khi tạo bối cảnh / nhân vật.")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadMedia(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) {

        String userId = com.historytalk.utils.SecurityUtils.getUserId();
        String userRole = com.historytalk.utils.SecurityUtils.getRoleName();
        String url = mapImageUploadService.uploadMapImage(file, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("url", url), "Media uploaded successfully"));
    }

    @PostMapping(value = "/media/upload-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Giáo viên tải video lên kho lưu trữ trường",
            description = "Tải file video (MP4, WebM, Ogg, MOV tối đa 50MB) và nhận URL công khai để điền vào videoUrl " +
                    "khi tạo / sửa bối cảnh. Với bối cảnh đã tồn tại có thể dùng POST /contexts/{contextId}/media/upload-direct?mediaType=VIDEO.")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadVideo(
            @RequestPart("file") MultipartFile file) {

        String url = videoUploadService.uploadVideo(file, SecurityUtils.getUserId());
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("url", url), "Video uploaded successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DOCUMENT UPDATES
    // ══════════════════════════════════════════════════════════════════════════

    @PutMapping("/contexts/{contextId}/documents/{docId}")
    @Operation(summary = "Cập nhật tài liệu bối cảnh địa phương", description = "Chỉ người upload tài liệu mới sửa được.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> updateLocalContextDocument(
            @PathVariable String contextId,
            @PathVariable String docId,
            @Valid @RequestBody UpdateHistoricalContextDocumentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.updateLocalContextDocument(contextId, docId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(doc, "Document updated successfully"));
    }

    @PutMapping("/characters/{characterId}/documents/{docId}")
    @Operation(summary = "Cập nhật tài liệu nhân vật địa phương", description = "Chỉ người upload tài liệu mới sửa được.")
    public ResponseEntity<ApiResponse<HistoricalContextDocumentResponse>> updateLocalCharacterDocument(
            @PathVariable String characterId,
            @PathVariable String docId,
            @Valid @RequestBody UpdateHistoricalContextDocumentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var doc = localContentService.updateLocalCharacterDocument(characterId, docId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(doc, "Document updated successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DYNAMIC CONTEXT-CHARACTER LINKING
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/characters/{characterId}/contexts/{contextId}")
    @Operation(summary = "Liên kết nhân vật địa phương với bối cảnh lịch sử")
    public ResponseEntity<ApiResponse<Void>> linkCharacterToContext(
            @PathVariable String characterId,
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.linkCharacterToContext(characterId, contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Context linked to character successfully"));
    }

    @DeleteMapping("/characters/{characterId}/contexts/{contextId}")
    @Operation(summary = "Hủy liên kết nhân vật địa phương với bối cảnh lịch sử")
    public ResponseEntity<ApiResponse<Void>> unlinkCharacterFromContext(
            @PathVariable String characterId,
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.unlinkCharacterFromContext(characterId, contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Context unlinked from character successfully"));
    }

    @GetMapping("/characters/{characterId}/contexts")
    @Operation(summary = "Danh sách bối cảnh gắn với nhân vật địa phương")
    public ResponseEntity<ApiResponse<List<LocalContentResponse>>> getCharacterContexts(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var list = localContentService.getCharacterContexts(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(list, "Character contexts retrieved successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRASH & RESTORE (THÙNG RÁC NỘI BỘ TRƯỜNG)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/trash/contexts")
    @Operation(summary = "Danh sách bối cảnh trong thùng rác của trường")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getTrashedContexts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.getTrashedContexts(page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Trashed contexts retrieved successfully"));
    }

    @PatchMapping("/trash/contexts/{contextId}/restore")
    @Operation(summary = "Khôi phục bối cảnh từ thùng rác")
    public ResponseEntity<ApiResponse<LocalContentResponse>> restoreLocalContext(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.restoreLocalContext(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Local context restored successfully"));
    }

    @DeleteMapping("/trash/contexts/{contextId}/permanent")
    @Operation(summary = "Xóa vĩnh viễn bối cảnh khỏi thùng rác")
    public ResponseEntity<ApiResponse<Void>> permanentDeleteLocalContext(
            @PathVariable String contextId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.permanentDeleteLocalContext(contextId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Local context permanently deleted"));
    }

    @GetMapping("/trash/characters")
    @Operation(summary = "Danh sách nhân vật trong thùng rác của trường")
    public ResponseEntity<ApiResponse<PaginatedResponse<LocalContentResponse>>> getTrashedCharacters(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.getTrashedCharacters(page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Trashed characters retrieved successfully"));
    }

    @PatchMapping("/trash/characters/{characterId}/restore")
    @Operation(summary = "Khôi phục nhân vật từ thùng rác")
    public ResponseEntity<ApiResponse<LocalContentResponse>> restoreLocalCharacter(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.restoreLocalCharacter(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Local character restored successfully"));
    }

    @DeleteMapping("/trash/characters/{characterId}/permanent")
    @Operation(summary = "Xóa vĩnh viễn nhân vật khỏi thùng rác")
    public ResponseEntity<ApiResponse<Void>> permanentDeleteLocalCharacter(
            @PathVariable String characterId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.permanentDeleteLocalCharacter(characterId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Local character permanently deleted"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCAL QUIZ & QUESTION BANK (TRẮC NGHIỆM BỐI CẢNH NỘI BỘ TRƯỜNG)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/contexts/{contextId}/quizzes")
    @Operation(summary = "Danh sách bài trắc nghiệm của bối cảnh nội bộ")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizStaffResponse>>> getLocalContextQuizzes(
            @PathVariable String contextId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.getLocalContextQuizzes(contextId, page, size, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Local quizzes retrieved successfully"));
    }

    @PostMapping("/contexts/{contextId}/quizzes")
    @Operation(summary = "Tạo bài trắc nghiệm cho bối cảnh nội bộ")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> createLocalQuiz(
            @PathVariable String contextId,
            @Valid @RequestBody CreateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.createLocalQuiz(contextId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(res, "Local quiz created successfully"));
    }

    @GetMapping("/quizzes/{quizId}")
    @Operation(summary = "Chi tiết bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> getLocalQuizById(
            @PathVariable String quizId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.getLocalQuizById(quizId, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Local quiz retrieved successfully"));
    }

    @PutMapping("/quizzes/{quizId}")
    @Operation(summary = "Cập nhật bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> updateLocalQuiz(
            @PathVariable String quizId,
            @Valid @RequestBody UpdateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.updateLocalQuiz(quizId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Local quiz updated successfully"));
    }

    @DeleteMapping("/quizzes/{quizId}")
    @Operation(summary = "Xóa bài trắc nghiệm nội bộ (soft-delete)")
    public ResponseEntity<ApiResponse<Void>> deleteLocalQuiz(
            @PathVariable String quizId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalQuiz(quizId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Local quiz deleted successfully"));
    }

    @PatchMapping("/quizzes/{quizId}/publish")
    @Operation(summary = "Bật/tắt công khai bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> togglePublishLocalQuiz(
            @PathVariable String quizId,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.togglePublishLocalQuiz(quizId, principal);
        return ResponseEntity.ok(ApiResponse.success(res, "Quiz publish status updated successfully"));
    }

    @PostMapping("/quizzes/{quizId}/questions")
    @Operation(summary = "Thêm câu hỏi vào bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<QuestionResponse>> addLocalQuizQuestion(
            @PathVariable String quizId,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        var res = localContentService.addLocalQuizQuestion(quizId, request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(res, "Question added successfully"));
    }

    @PutMapping("/quizzes/{quizId}/questions/{questionId}")
    @Operation(summary = "Cập nhật câu hỏi trong bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<Void>> updateLocalQuizQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.updateLocalQuizQuestion(quizId, questionId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Question updated successfully"));
    }

    @DeleteMapping("/quizzes/{quizId}/questions/{questionId}")
    @Operation(summary = "Xóa câu hỏi khỏi bài trắc nghiệm nội bộ")
    public ResponseEntity<ApiResponse<Void>> deleteLocalQuizQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId,
            @AuthenticationPrincipal UserPrincipal principal) {

        localContentService.deleteLocalQuizQuestion(quizId, questionId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Question deleted successfully"));
    }
}
