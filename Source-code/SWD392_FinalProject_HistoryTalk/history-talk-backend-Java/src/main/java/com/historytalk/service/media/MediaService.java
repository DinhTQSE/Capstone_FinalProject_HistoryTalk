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
     * Supports 2D images (max 10MB), 3D models (max 100MB, Character) and
     * videos (max 50MB, Historical Context only).
     *
     * <p>Uploading a media type that already exists on the entity replaces the previous file.
     * TEACHER may only upload onto local content they created; SCHOOL_ADMIN onto any local
     * content of their school; platform roles onto any entity.
     *
     * @param entityId The entity ID (characterId, contextId, or userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param file Binary file data
     * @param mediaType Media type (IMAGE_2D, MODEL_3D or VIDEO)
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
     * Generate signed view URL for the most recently uploaded media of an entity.
     */
    default SignedViewUrlResponse generateSignedViewUrl(
            UUID entityId,
            EntityType entityType,
            Integer thumbnailWidth,
            Integer thumbnailHeight,
            String userId,
            String userRole) {
        return generateSignedViewUrl(entityId, entityType, null, thumbnailWidth, thumbnailHeight, userId, userRole);
    }

    /**
     * Generate signed view URL for media.
     *
     * @param entityId The entity ID (characterId, contextId, userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param mediaType Media type to look up (optional — null picks the most recent media)
     * @param thumbnailWidth Thumbnail width (optional)
     * @param thumbnailHeight Thumbnail height (optional)
     * @param userId Current user ID
     * @param userRole Current user role
     * @return Signed view URL response
     */
    SignedViewUrlResponse generateSignedViewUrl(
            UUID entityId,
            EntityType entityType,
            String mediaType,
            Integer thumbnailWidth,
            Integer thumbnailHeight,
            String userId,
            String userRole);

    /**
     * Delete all media from entity.
     */
    default void deleteMedia(
            UUID entityId,
            EntityType entityType,
            String userId,
            String userRole) {
        deleteMedia(entityId, entityType, null, userId, userRole);
    }

    /**
     * Delete media from entity.
     *
     * @param entityId The entity ID (characterId, contextId, userId)
     * @param entityType The entity type (CHARACTER, CONTEXT, USER)
     * @param mediaType Media type to delete (optional — null deletes every media of the entity)
     * @param userId Current user ID
     * @param userRole Current user role
     */
    void deleteMedia(
            UUID entityId,
            EntityType entityType,
            String mediaType,
            String userId,
            String userRole);
}
