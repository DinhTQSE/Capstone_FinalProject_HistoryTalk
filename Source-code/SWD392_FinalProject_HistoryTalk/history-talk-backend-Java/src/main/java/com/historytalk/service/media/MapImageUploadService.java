package com.historytalk.service.media;

import org.springframework.web.multipart.MultipartFile;

/**
 * Handles battle-map background image uploads.
 * <p>
 * Map images are public assets (not sensitive), so the returned URL is a
 * permanent public Supabase Storage URL — not a time-limited signed URL.
 * The image is stored in the existing {@code documents} bucket under the
 * {@code battle-maps/} prefix to avoid creating a new bucket.
 * <p>
 * This service is intentionally separate from {@link MediaService} because map
 * images are not bound to a specific entity row and should NOT pollute the
 * {@code document_media_metadata} table.
 */
public interface MapImageUploadService {

    /**
     * Upload a battle-map background image and return its public CDN URL.
     *
     * @param file     PNG, JPG, or WebP file; maximum 2 MB.
     * @param userId   Caller's user ID (for logging / audit).
     * @param userRole Caller's role; must be CONTENT_ADMIN / SYSTEM_ADMIN / STAFF / ADMIN.
     * @return Permanent public Supabase URL to the uploaded image.
     * @throws com.historytalk.exception.InvalidRequestException if the file fails validation.
     */
    String uploadMapImage(MultipartFile file, String userId, String userRole);
}
