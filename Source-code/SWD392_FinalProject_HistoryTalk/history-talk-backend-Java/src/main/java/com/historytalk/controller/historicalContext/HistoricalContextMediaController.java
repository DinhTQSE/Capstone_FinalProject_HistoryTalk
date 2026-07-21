package com.historytalk.controller.historicalContext;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.service.media.MediaService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contexts/{contextId}/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Historical Context Media", description = "Media upload and management APIs for Historical Contexts")
public class HistoricalContextMediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload-direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'STAFF', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "Upload media directly via multipart/form-data",
        description = "Uploads media directly via multipart/form-data for a Historical Context. Supports both 2D images (max 10MB) and 3D models (max 100MB). Single-step flow for direct binary upload."
    )
    public ResponseEntity<ApiResponse<DocumentMediaMetadataResponse>> uploadDirectBinary(
            @Parameter(description = "Context ID", required = true)
            @PathVariable String contextId,
            @Parameter(description = "Media binary file (2D image or 3D model)", required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Media type (IMAGE_2D or MODEL_3D)", required = true, schema = @Schema(allowableValues = {"IMAGE_2D", "MODEL_3D"}))
            @RequestParam("mediaType") String mediaType,
            @Parameter(description = "Image width (optional, for 2D images)")
            @RequestParam(value = "width", required = false) Integer width,
            @Parameter(description = "Image height (optional, for 2D images)")
            @RequestParam(value = "height", required = false) Integer height) {
        
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        
        log.info("POST /api/v1/contexts/{}/media/upload-direct by user {}", contextId, userId);
        
        DocumentMediaMetadataResponse response = mediaService.uploadDirectBinary(
                contextUuid, EntityType.CONTEXT, file, mediaType, width, height, userId, userRole);
        
        return ResponseEntity.ok(ApiResponse.success(response, "Media uploaded successfully"));
    }

    @GetMapping("/view-url")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get signed view URL for context media")
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> getViewUrl(
            @PathVariable String contextId,
            @RequestParam(required = false) Integer thumbnailWidth,
            @RequestParam(required = false) Integer thumbnailHeight) {
        
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        
        log.info("GET /api/v1/contexts/{}/media/view-url by user {}", contextId, userId);
        
        SignedViewUrlResponse response = mediaService.generateSignedViewUrl(
                contextUuid, EntityType.CONTEXT, thumbnailWidth, thumbnailHeight, userId, userRole);
        
        return ResponseEntity.ok(ApiResponse.success(response, "View URL generated successfully"));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete media from context")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String contextId) {
        
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        UUID contextUuid = UUID.fromString(contextId);
        
        log.info("DELETE /api/v1/contexts/{}/media by user {}", contextId, userId);
        
        mediaService.deleteMedia(contextUuid, EntityType.CONTEXT, userId, userRole);
        
        return ResponseEntity.noContent().build();
    }
}
