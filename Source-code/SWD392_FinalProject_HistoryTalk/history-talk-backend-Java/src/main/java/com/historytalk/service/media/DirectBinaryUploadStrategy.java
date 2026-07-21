package com.historytalk.service.media;

import com.historytalk.entity.enums.MediaType;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Strategy for direct binary upload via multipart/form-data.
 * Supports both 2D images (max 10MB) and 3D models (max 100MB).
 * Streams binary data directly to Supabase Storage via MultipartFile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DirectBinaryUploadStrategy implements MediaUploadStrategy {

    private static final long MAX_2D_SIZE_BYTES = 10 * 1024 * 1024L; // 10MB
    private static final long MAX_3D_SIZE_BYTES = 100 * 1024 * 1024L; // 100MB

    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Override
    public MediaType getSupportedType() {
        return MediaType.IMAGE_2D; // Legacy compatibility - now handles both types
    }

    @Override
    public UploadResult upload(String storagePath, String contentType, Long fileSizeBytes) {
        String uploadId = UUID.randomUUID().toString();
        log.debug("Direct binary upload strategy selected for file: {}, size: {} bytes", storagePath, fileSizeBytes);
        return UploadResult.directBinary(storagePath, uploadId);
    }

    /**
     * Upload MultipartFile directly to Supabase Storage.
     * Supports both 2D images (max 10MB) and 3D models (max 100MB).
     *
     * @param storagePath Storage path for the file
     * @param file MultipartFile to upload
     * @param contentType Content type of the file
     * @param mediaType Media type (IMAGE_2D or MODEL_3D)
     * @return UploadResult with storage path and upload ID
     * @throws IOException if file reading fails
     */
    public UploadResult uploadMultipartFile(String storagePath, MultipartFile file, String contentType, String mediaType) throws IOException {
        long maxSize = mediaType != null && mediaType.equalsIgnoreCase("MODEL_3D") 
                ? MAX_3D_SIZE_BYTES 
                : MAX_2D_SIZE_BYTES;

        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds maximum " + maxSize + " bytes for " + mediaType);
        }

        String uploadId = UUID.randomUUID().toString();
        log.debug("Direct binary upload via MultipartFile for file: {}, size: {} bytes, type: {}", 
                storagePath, file.getSize(), mediaType);

        // Stream the file directly to Supabase
        supabaseDocumentStorageService.uploadFile(storagePath, file.getInputStream(), contentType, file.getSize());

        return UploadResult.directBinary(storagePath, uploadId);
    }
}
