package com.historytalk.controller.user;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.service.user.UserAvatarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/{userId}/avatar")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Avatar", description = "APIs for user avatar management")
public class UserAvatarController {

    private final UserAvatarService userAvatarService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload avatar directly via multipart/form-data (single-step)",
        description = "Uploads user avatar directly via multipart/form-data. Users can only upload their own avatars unless they are admins. Max file size: 5MB."
    )
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> uploadAvatarDirect(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId,
            @Parameter(description = "Avatar image binary file", required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Media type (must be IMAGE_2D)", required = true, schema = @Schema(allowableValues = {"IMAGE_2D"}))
            @RequestParam("mediaType") String mediaType,
            @Parameter(description = "Image width (optional)")
            @RequestParam(value = "width", required = false) Integer width,
            @Parameter(description = "Image height (optional)")
            @RequestParam(value = "height", required = false) Integer height,
            @AuthenticationPrincipal UserDetails userDetails) {

        String currentUserId = extractUserIdFromPrincipal(userDetails);
        String userRole = extractRoleFromPrincipal(userDetails);

        log.info("POST /api/v1/users/{}/avatar (direct) by user {}", userId, currentUserId);

        SignedViewUrlResponse response = userAvatarService.uploadAvatarDirect(
                userId, file, mediaType, width, height, currentUserId, userRole);

        return ResponseEntity.ok(ApiResponse.success(response, "Avatar uploaded successfully"));
    }

    @GetMapping("/view-url")
    @Operation(summary = "Get avatar view URL", description = "Retrieves a signed URL for viewing user avatar. Accessible to authenticated users.")
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> generateAvatarViewUrl(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String currentUserId = extractUserIdFromPrincipal(userDetails);

        log.info("GET /api/v1/users/{}/avatar/view-url by user {}", userId, currentUserId);

        SignedViewUrlResponse response = userAvatarService.generateAvatarViewUrl(userId, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(response, "Avatar view URL generated successfully"));
    }

    @DeleteMapping
    @Operation(summary = "Delete avatar", description = "Deletes user avatar from storage and clears avatar URL. Users can only delete their own avatars unless they are admins.")
    public ResponseEntity<Void> deleteAvatar(
            @Parameter(description = "User ID", required = true)
            @PathVariable String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String currentUserId = extractUserIdFromPrincipal(userDetails);
        String userRole = extractRoleFromPrincipal(userDetails);

        log.info("DELETE /api/v1/users/{}/avatar by user {}", userId, currentUserId);

        userAvatarService.deleteAvatar(userId, currentUserId, userRole);

        return ResponseEntity.noContent().build();
    }

    private String extractUserIdFromPrincipal(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        return userDetails.getUsername();
    }

    private String extractRoleFromPrincipal(UserDetails userDetails) {
        if (userDetails == null || userDetails.getAuthorities().isEmpty()) {
            return null;
        }
        return userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
    }
}
