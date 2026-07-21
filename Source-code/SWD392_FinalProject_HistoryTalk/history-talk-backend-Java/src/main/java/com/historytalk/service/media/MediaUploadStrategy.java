package com.historytalk.service.media;

import com.historytalk.entity.enums.MediaType;

/**
 * Strategy interface for media upload operations.
 * Implementations define how different types of media are uploaded to storage.
 */
public interface MediaUploadStrategy {

    /**
     * Returns the media type this strategy supports.
     *
     * @return the supported MediaType
     */
    MediaType getSupportedType();

    /**
     * Generates an upload URL or performs direct upload based on the strategy implementation.
     *
     * @param storagePath the storage path for the file
     * @param contentType the MIME type of the file
     * @param fileSizeBytes the size of the file in bytes
     * @return UploadResult containing the upload URL or file reference
     */
    UploadResult upload(String storagePath, String contentType, Long fileSizeBytes);

    /**
     * Result object containing upload information.
     */
    record UploadResult(
            String uploadUrl,           // Presigned URL for direct client upload (null for direct binary)
            String storagePath,         // Final storage path
            Long expiresIn,             // URL expiration in seconds (null for direct binary)
            String uploadId             // Unique upload ID for tracking
    ) {
        public static UploadResult directBinary(String storagePath, String uploadId) {
            return new UploadResult(null, storagePath, null, uploadId);
        }

        public static UploadResult presignedUrl(String uploadUrl, String storagePath, Long expiresIn, String uploadId) {
            return new UploadResult(uploadUrl, storagePath, expiresIn, uploadId);
        }
    }
}
