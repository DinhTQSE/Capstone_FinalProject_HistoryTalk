package com.historytalk.controller.map;

import com.historytalk.dto.ApiResponse;
import com.historytalk.service.media.MapImageUploadService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Endpoint for uploading battle-map background images to Supabase Storage.
 *
 * <p>Map images are public assets — the returned URL is permanent and
 * does not expire, so admins can embed it directly in the {@code battleMap.imageUrl}
 * field when saving the overlay.
 *
 * <p>Base path: {@code /api/v1/media/map-image}
 */
@RestController
@RequestMapping("/api/v1/media/map-image")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Battle Map Media", description = "Upload background images for the interactive battle-map editor")
public class MapImageUploadController {

    private final MapImageUploadService mapImageUploadService;

    /**
     * POST /api/v1/media/map-image
     *
     * <p>Accepts a PNG / JPG / WebP file (max 2 MB) and stores it in the
     * Supabase {@code documents} bucket under the {@code battle-maps/} prefix.
     * Returns the permanent public URL so the FE can embed it in
     * {@code battleMap.imageUrl}.
     *
     * <p>Authorization: CONTENT_ADMIN, SYSTEM_ADMIN, STAFF, ADMIN
     *
     * @param file Binary image file (multipart/form-data, field name {@code file}).
     * @return {@code { "data": { "url": "https://..." } }}
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'STAFF', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Upload a battle-map background image",
            description = """
                    Upload a PNG, JPG, or WebP image (≤ 2 MB) to be used as the background
                    of the interactive battle-map editor.
                    Returns a permanent public URL to embed in battleMap.imageUrl when saving
                    the battle-map overlay via PUT /historical-contexts/{id}.
                    """)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadMapImage(
            @Parameter(
                    description = "PNG, JPG, or WebP image file. Maximum size: 2 MB.",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestPart("file") MultipartFile file) {

        String userId   = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/media/map-image by userId={} role={}", userId, userRole);

        String url = mapImageUploadService.uploadMapImage(file, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("url", url),
                "Battle-map image uploaded successfully"));
    }
}
