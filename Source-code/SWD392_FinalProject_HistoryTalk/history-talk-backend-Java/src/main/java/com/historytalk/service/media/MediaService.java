package com.historytalk.service.media;

import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.enums.EntityType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Base interface for polymorphic media operations across different entity types.
 * This interface provides a unified API for media upload, view, and deletion
 * for Characters, Historical Contexts, and Users.
 */
public interface MediaService {

    /**
     * Upload media directly via multipart/form-data (single-step).
     * Supports both 2D images (max 10MB) and 3D models (max 100MB).
     *
     * @param entityId The entity ID (characterId, contextId, or userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param file Binary file data
     * @param mediaType Media type (IMAGE_2D or MODEL_3D)
     * @param width Image width (optional, for 2D images)
     * @param height Image height (optional, for 2D images)
     * @param userId Current user ID
     * @param userRole Current user role
     * @return Media metadata response
     */
    DocumentMediaMetadataResponse uploadDirectBinary(
            UUID entityId,
            EntityType entityType,
            MultipartFile file,
            String mediaType,
            Integer width,
            Integer height,
            String userId,
            String userRole);

    /**
     * Generate signed view URL for media.
     *
     * @param entityId The entity ID (characterId, contextId, userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param thumbnailWidth Thumbnail width (optional)
     * @param thumbnailHeight Thumbnail height (optional)
     * @param userId Current user ID
     * @param userRole Current user role
     * @return Signed view URL response
     */
    SignedViewUrlResponse generateSignedViewUrl(
            UUID entityId,
            EntityType entityType,
            Integer thumbnailWidth,
            Integer thumbnailHeight,
            String userId,
            String userRole);

    /**
     * Delete media from entity.
     *
     * @param entityId The entity ID (characterId, contextId, userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param userId Current user ID
     * @param userRole Current user role
     */
    void deleteMedia(
            UUID entityId,
            EntityType entityType,
            String userId,
            String userRole);
}
