package com.historytalk.service.media;

import com.historytalk.exception.InvalidRequestException;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Uploads battle-map background images to Supabase Storage and returns a
 * permanent public URL.
 *
 * <p>Storage layout inside the existing {@code documents} bucket:
 * <pre>
 *   battle-maps/{uuid}.{ext}
 * </pre>
 *
 * <p>The URL is public (not signed) because map images are non-sensitive
 * educational assets that every user should be able to view without a token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MapImageUploadServiceImpl implements MapImageUploadService {

    /** Maximum allowed file size: 2 MB (matches FE base64 limit). */
    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_MIME_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp");

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("png", "jpg", "jpeg", "webp");

    /** Storage path prefix within the documents bucket. */
    private static final String PATH_PREFIX = "battle-maps";

    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.bucket:documents}")
    private String bucket;

    @Override
    public String uploadMapImage(MultipartFile file, String userId, String userRole) {
        validate(file);

        String ext = extractExtension(file.getOriginalFilename());
        String objectPath = PATH_PREFIX + "/" + UUID.randomUUID() + "." + ext;

        try {
            supabaseDocumentStorageService.uploadFile(
                    objectPath,
                    file.getInputStream(),
                    resolveContentType(file, ext),
                    file.getSize()
            );
        } catch (IOException ex) {
            throw new InvalidRequestException("Không thể đọc file ảnh: " + ex.getMessage());
        }

        // Build permanent public URL — no expiry, readable by anyone.
        String cleanBase = supabaseUrl.replaceAll("/+$", "");
        String publicUrl = cleanBase + "/storage/v1/object/public/" + bucket + "/" + objectPath;
        log.info("Battle-map image uploaded: path={} by userId={}", objectPath, userId);
        return publicUrl;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File ảnh lược đồ không được để trống");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidRequestException("Kích thước file vượt quá giới hạn 2 MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new InvalidRequestException(
                    "Chỉ chấp nhận file PNG, JPG, JPEG hoặc WebP. Nhận: " + originalName);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestException(
                    "Content-type không hợp lệ: " + contentType);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new InvalidRequestException("Tên file không có đuôi mở rộng");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Prefer the MultipartFile's declared content-type; fall back to
     * extension-based mapping so uploads without a Content-Type header still work.
     */
    private String resolveContentType(MultipartFile file, String ext) {
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank() && ALLOWED_MIME_TYPES.contains(ct.toLowerCase(Locale.ROOT))) {
            return ct;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp"        -> "image/webp";
            default            -> "image/png";
        };
    }
}
