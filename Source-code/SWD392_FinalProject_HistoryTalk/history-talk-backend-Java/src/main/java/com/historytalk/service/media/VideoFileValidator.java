package com.historytalk.service.media;

import com.historytalk.exception.InvalidRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * Shared validation for video uploads (Historical Context videoUrl).
 * <p>
 * Accepts MP4 / WebM / Ogg / MOV up to {@link #MAX_VIDEO_SIZE_BYTES}, which is bounded by
 * {@code spring.servlet.multipart.max-file-size} (50MB).
 */
public final class VideoFileValidator {

    public static final long MAX_VIDEO_SIZE_BYTES = 50L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("video/mp4", "video/webm", "video/ogg", "video/quicktime");

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("mp4", "webm", "ogg", "ogv", "mov");

    private VideoFileValidator() {
    }

    /**
     * Validates the file and returns the content type to store it with.
     * Browsers sometimes send {@code application/octet-stream} for videos, in which case the
     * extension decides.
     */
    public static String validateAndResolveContentType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File video không được để trống");
        }
        if (file.getSize() > MAX_VIDEO_SIZE_BYTES) {
            throw new InvalidRequestException("Kích thước file không được vượt quá " + (MAX_VIDEO_SIZE_BYTES / (1024 * 1024)) + "MB");
        }
        return resolveContentType(file.getContentType(), file.getOriginalFilename());
    }

    public static String resolveContentType(String contentType, String fileName) {
        String ext = extractExtension(fileName);
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ALLOWED_CONTENT_TYPES.contains(ct)) {
            return ct;
        }
        if ((ct.isEmpty() || ct.equals("application/octet-stream")) && ALLOWED_EXTENSIONS.contains(ext)) {
            return switch (ext) {
                case "webm" -> "video/webm";
                case "ogg", "ogv" -> "video/ogg";
                case "mov" -> "video/quicktime";
                default -> "video/mp4";
            };
        }
        throw new InvalidRequestException("File phải là video (MP4, WebM, Ogg hoặc MOV)");
    }

    /** Extension used when storing the file, derived from the resolved content type. */
    public static String extensionFor(String resolvedContentType) {
        return switch (resolvedContentType) {
            case "video/webm" -> "webm";
            case "video/ogg" -> "ogg";
            case "video/quicktime" -> "mov";
            default -> "mp4";
        };
    }

    private static String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }
}
