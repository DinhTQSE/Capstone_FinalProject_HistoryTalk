package com.historytalk.service.user;

import com.historytalk.dto.document.SignedViewUrlResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for user avatar management.
 * Handles avatar upload, viewing, and deletion operations via direct binary upload.
 */
public interface UserAvatarService {

    /**
     * Uploads user avatar directly via multipart/form-data (single-step).
     *
     * @param userId the user ID
     * @param file the avatar file
     * @param mediaType the media type (must be IMAGE_2D)
     * @param width the image width (optional)
     * @param height the image height (optional)
     * @param currentUserId the ID of the current authenticated user
     * @param userRole the role of the current user
     * @return SignedViewUrlResponse containing the new avatar view URL
     */
    SignedViewUrlResponse uploadAvatarDirect(
            String userId,
            MultipartFile file,
            String mediaType,
            Integer width,
            Integer height,
            String currentUserId,
            String userRole
    );

    /**
     * Generates a signed view URL for user avatar.
     *
     * @param userId the user ID
     * @param currentUserId the ID of the current authenticated user
     * @return SignedViewUrlResponse containing the avatar view URL
     */
    SignedViewUrlResponse generateAvatarViewUrl(
            String userId,
            String currentUserId
    );

    /**
     * Deletes user avatar from storage and clears avatar URL.
     *
     * @param userId the user ID
     * @param currentUserId the ID of the current authenticated user
     * @param userRole the role of the current user
     */
    void deleteAvatar(
            String userId,
            String currentUserId,
            String userRole
    );
}
