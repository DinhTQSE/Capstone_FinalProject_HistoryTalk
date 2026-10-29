package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.media.MediaService;
import com.historytalk.service.media.DirectBinaryUploadStrategy;
import com.historytalk.service.media.VideoFileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMediaServiceImpl implements MediaService {

    private static final long VIEW_URL_EXPIRES_IN_SECONDS = 3600;
    private static final long MAX_2D_SIZE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_3D_SIZE_BYTES = 100 * 1024 * 1024;

    private final DocumentMediaMetadataRepository mediaMetadataRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;
    private final DirectBinaryUploadStrategy directBinaryUploadStrategy;
    private final org.springframework.core.env.Environment environment;

    // Polymorphic MediaService interface methods
    @Override
    @Transactional
    public DocumentMediaMetadataResponse uploadDirectBinary(
            UUID entityId, EntityType entityType, MultipartFile file, String mediaType,
            Integer width, Integer height, String userId, String userRole) {

        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền tải media lên");
        }

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File không được để trống");
        }

        MediaType mediaTypeEnum = parseMediaType(mediaType);

        // Validate content type and file size based on media type
        String contentType = file.getContentType();
        long maxSize;

        if (mediaTypeEnum == MediaType.IMAGE_2D) {
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new InvalidRequestException("File phải là ảnh");
            }
            maxSize = MAX_2D_SIZE_BYTES;
        } else if (mediaTypeEnum == MediaType.MODEL_3D) {
            // Accept 3D model content types
            if (contentType == null || (!contentType.startsWith("model/") && !contentType.startsWith("application/octet-stream"))) {
                throw new InvalidRequestException("File phải là 3D model");
            }
            maxSize = MAX_3D_SIZE_BYTES;
        } else {
            // VIDEO — only Historical Context has a videoUrl field
            if (entityType != EntityType.CONTEXT) {
                throw new InvalidRequestException("Video chỉ được hỗ trợ cho bối cảnh lịch sử");
            }
            contentType = VideoFileValidator.resolveContentType(contentType, file.getOriginalFilename());
            maxSize = VideoFileValidator.MAX_VIDEO_SIZE_BYTES;
        }

        // Validate file size
        if (file.getSize() > maxSize) {
            throw new InvalidRequestException("Kích thước file không được vượt quá " + (maxSize / (1024 * 1024)) + "MB");
        }

        assertCanManageMedia(entityType, entityId, userId, userRole);

        // Generate storage path
        String storagePath = generateStoragePath(entityType, entityId, file.getOriginalFilename());
        List<DocumentMediaMetadata> previous =
                mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(entityType, entityId, mediaTypeEnum);

        try {
            // Upload file using DirectBinaryUploadStrategy
            directBinaryUploadStrategy.uploadMultipartFile(
                    storagePath, file, contentType, mediaTypeEnum.name());

            // Save metadata
            DocumentMediaMetadata metadata = createMediaMetadata(
                    null, entityType, entityId, mediaTypeEnum, file.getOriginalFilename(),
                    file.getSize(), width, height, storagePath);

            mediaMetadataRepository.save(metadata);

            // One media per (entity, mediaType): the new upload replaces the previous one
            removeMetadata(previous);

            log.info("Direct binary upload completed for entity {}:{} with storage path {}", entityType, entityId, storagePath);

            DocumentMediaMetadataResponse response = mapToResponse(metadata);
            updateEntityMediaUrl(entityType, entityId, mediaTypeEnum, response.getPublicUrl());
            return response;

        } catch (IOException e) {
            throw new InvalidRequestException("Không thể đọc file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SignedViewUrlResponse generateSignedViewUrl(
            UUID entityId, EntityType entityType, String mediaType, Integer thumbnailWidth, Integer thumbnailHeight,
            String userId, String userRole) {

        assertCanViewMedia(entityType, entityId, userId, userRole);

        List<DocumentMediaMetadata> candidates = mediaType == null || mediaType.isBlank()
                ? mediaMetadataRepository.findAllByEntityTypeAndEntityId(entityType, entityId)
                : mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(entityType, entityId, parseMediaType(mediaType));

        DocumentMediaMetadata metadata = candidates.stream()
                .max(Comparator.comparing(DocumentMediaMetadata::getCreatedAt,
                        Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy media metadata: " + entityType + ":" + entityId));

        String viewUrl = supabaseDocumentStorageService.createSignedUrl(
                metadata.getStoragePath(), VIEW_URL_EXPIRES_IN_SECONDS);

        String thumbnailUrl = null;
        if (metadata.getMediaType() == MediaType.IMAGE_2D && (thumbnailWidth != null || thumbnailHeight != null)) {
            int w = thumbnailWidth != null ? thumbnailWidth : 300;
            int h = thumbnailHeight != null ? thumbnailHeight : 300;
            thumbnailUrl = metadata.getStoragePath() + "?width=" + w + "&height=" + h;
        }

        return SignedViewUrlResponse.builder()
                .viewUrl(viewUrl)
                .thumbnailUrl(thumbnailUrl)
                .expiresIn(VIEW_URL_EXPIRES_IN_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public void deleteMedia(UUID entityId, EntityType entityType, String mediaType, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền xóa media");
        }

        assertCanManageMedia(entityType, entityId, userId, userRole);

        if (mediaType == null || mediaType.isBlank()) {
            List<DocumentMediaMetadata> all = mediaMetadataRepository.findAllByEntityTypeAndEntityId(entityType, entityId);
            if (all.isEmpty()) {
                throw new ResourceNotFoundException("Không tìm thấy media metadata: " + entityType + ":" + entityId);
            }
            removeMetadata(all);
            clearEntityMediaUrl(entityType, entityId, null);
            log.info("Deleted all media for entity {}:{}", entityType, entityId);
            return;
        }

        // Typed delete also clears a URL that was entered manually (no metadata row)
        MediaType mediaTypeEnum = parseMediaType(mediaType);
        removeMetadata(mediaMetadataRepository.findAllByEntityTypeAndEntityIdAndMediaType(entityType, entityId, mediaTypeEnum));
        clearEntityMediaUrl(entityType, entityId, mediaTypeEnum);
        log.info("Deleted {} media for entity {}:{}", mediaTypeEnum, entityType, entityId);
    }

    // ── Access control ───────────────────────────────────────────────────────

    /**
     * Platform roles manage any media. School roles are confined to local content of their own
     * school: SCHOOL_ADMIN for any item of the school, TEACHER only for items they created.
     */
    private void assertCanManageMedia(EntityType entityType, UUID entityId, String userId, String userRole) {
        if (isPlatformRole(userRole)) {
            return;
        }
        if (entityType == EntityType.USER) {
            if (userId == null || !entityId.toString().equals(userId)) {
                throw new UnauthorizedException("Bạn chỉ được quản lý ảnh đại diện của chính mình");
            }
            return;
        }

        OwnedEntity owned = loadOwnedEntity(entityType, entityId);
        User user = loadUser(userId);
        UUID userSchoolId = user.getSchool() != null ? user.getSchool().getId() : null;

        if (owned.school() == null || userSchoolId == null || !userSchoolId.equals(owned.school().getId())) {
            throw new UnauthorizedException("Bạn chỉ được quản lý media của nội dung thuộc trường mình");
        }
        if (isRole(userRole, "TEACHER")
                && (owned.createdBy() == null || !user.getUid().equals(owned.createdBy().getUid()))) {
            throw new UnauthorizedException("You can only modify content you created");
        }
    }

    /** Media of school-owned content is only viewable by members of that school (or platform roles). */
    private void assertCanViewMedia(EntityType entityType, UUID entityId, String userId, String userRole) {
        if (isPlatformRole(userRole) || (entityType != EntityType.CONTEXT && entityType != EntityType.CHARACTER)) {
            return;
        }
        OwnedEntity owned = loadOwnedEntity(entityType, entityId);
        if (owned.school() == null) {
            return;
        }
        UUID userSchoolId = userId == null ? null : userRepository.findById(UUID.fromString(userId))
                .map(User::getSchool)
                .map(School::getId)
                .orElse(null);
        if (!owned.school().getId().equals(userSchoolId)) {
            throw new ResourceNotFoundException("Không tìm thấy media metadata: " + entityType + ":" + entityId);
        }
    }

    private record OwnedEntity(School school, User createdBy) {
    }

    private OwnedEntity loadOwnedEntity(EntityType entityType, UUID entityId) {
        if (entityType == EntityType.CONTEXT) {
            return historicalContextRepository.findById(entityId)
                    .map(ctx -> new OwnedEntity(ctx.getSchool(), ctx.getCreatedBy()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bối cảnh lịch sử với ID: " + entityId));
        }
        if (entityType == EntityType.CHARACTER) {
            return characterRepository.findById(entityId)
                    .map(chr -> new OwnedEntity(chr.getSchool(), chr.getCreatedBy()))
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + entityId));
        }
        throw new UnauthorizedException("Bạn không có quyền quản lý media của " + entityType);
    }

    private User loadUser(String userId) {
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MediaType parseMediaType(String mediaType) {
        try {
            return MediaType.valueOf(mediaType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException("Media type không hợp lệ: " + mediaType);
        }
    }

    private void removeMetadata(List<DocumentMediaMetadata> rows) {
        for (DocumentMediaMetadata row : rows) {
            try {
                supabaseDocumentStorageService.deleteFile(row.getStoragePath());
            } catch (RuntimeException ex) {
                // Orphaned storage object is preferable to failing the user's request
                log.warn("Could not delete media file {}: {}", row.getStoragePath(), ex.getMessage());
            }
            mediaMetadataRepository.delete(row);
        }
    }

    private void updateEntityMediaUrl(EntityType entityType, UUID entityId, MediaType mediaType, String publicUrl) {
        if (publicUrl == null) return;
        if (entityType == EntityType.CONTEXT && historicalContextRepository != null) {
            historicalContextRepository.findById(entityId).ifPresent(ctx -> {
                if (mediaType == MediaType.IMAGE_2D) {
                    ctx.setImageUrl(publicUrl);
                } else if (mediaType == MediaType.VIDEO) {
                    ctx.setVideoUrl(publicUrl);
                }
                historicalContextRepository.save(ctx);
                log.info("Updated HistoricalContext {} media url: {}", entityId, publicUrl);
            });
        } else if (entityType == EntityType.CHARACTER && characterRepository != null) {
            characterRepository.findById(entityId).ifPresent(chr -> {
                if (mediaType == MediaType.IMAGE_2D) {
                    chr.setImageUrl(publicUrl);
                } else if (mediaType == MediaType.MODEL_3D) {
                    chr.setModelUrl(publicUrl);
                }
                characterRepository.save(chr);
                log.info("Updated Character {} media url: {}", entityId, publicUrl);
            });
        }
    }

    /** Clears the URL field of {@code mediaType}, or every media URL when {@code mediaType} is null. */
    private void clearEntityMediaUrl(EntityType entityType, UUID entityId, MediaType mediaType) {
        if (entityType == EntityType.CONTEXT && historicalContextRepository != null) {
            historicalContextRepository.findById(entityId).ifPresent(ctx -> {
                if (mediaType == null || mediaType == MediaType.IMAGE_2D) ctx.setImageUrl(null);
                if (mediaType == null || mediaType == MediaType.VIDEO) ctx.setVideoUrl(null);
                historicalContextRepository.save(ctx);
            });
        } else if (entityType == EntityType.CHARACTER && characterRepository != null) {
            characterRepository.findById(entityId).ifPresent(chr -> {
                if (mediaType == null || mediaType == MediaType.IMAGE_2D) chr.setImageUrl(null);
                if (mediaType == null || mediaType == MediaType.MODEL_3D) chr.setModelUrl(null);
                characterRepository.save(chr);
            });
        }
    }

    /**
     * {@code <TYPE>/<entityId>/<random>_<safe-name>} — the random prefix stops re-uploads with the
     * same file name from being served stale by the CDN, and sanitising blocks path traversal.
     */
    private String generateStoragePath(EntityType entityType, UUID entityId, String fileName) {
        String safeName = sanitizeFileName(fileName);
        String unique = UUID.randomUUID().toString().substring(0, 8);
        if (entityType == EntityType.USER) {
            return "users/%s/avatar/%s_%s".formatted(entityId, unique, safeName);
        }
        return "%s/%s/%s_%s".formatted(
                entityType.name().toUpperCase(Locale.ROOT),
                entityId,
                unique,
                safeName);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file";
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("\\.{2,}", ".");
        if (safe.isBlank() || safe.equals(".")) {
            return "file";
        }
        return safe.length() > 120 ? safe.substring(safe.length() - 120) : safe;
    }

    private DocumentMediaMetadataResponse mapToResponse(DocumentMediaMetadata metadata) {
        String supabaseUrl = environment.getProperty("supabase.url");
        String bucketName = environment.getProperty("supabase.storage.bucket", "documents");
        String publicUrl = null;

        if (supabaseUrl != null && metadata.getStoragePath() != null) {
            publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + metadata.getStoragePath();
        }

        return DocumentMediaMetadataResponse.builder()
                .metadataId(metadata.getMetadataId() != null ? metadata.getMetadataId().toString() : null)
                .documentId(metadata.getDocumentId() != null ? metadata.getDocumentId().toString() : null)
                .entityType(metadata.getEntityType())
                .entityId(metadata.getEntityId().toString())
                .mediaType(metadata.getMediaType().name())
                .fileFormat(metadata.getFileFormat())
                .fileSizeBytes(metadata.getFileSizeBytes())
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .storagePath(metadata.getStoragePath())
                .thumbnailPath(metadata.getThumbnailPath())
                .publicUrl(publicUrl)
                .extendedMetadata(metadata.getExtendedMetadata())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .build();
    }

    private DocumentMediaMetadata createMediaMetadata(
            UUID documentId, EntityType entityType, UUID entityId, MediaType mediaType, String fileName,
            Long fileSizeBytes, Integer width, Integer height, String storagePath) {
        DocumentMediaMetadata metadata = new DocumentMediaMetadata();
        metadata.setDocumentId(documentId);
        metadata.setEntityType(entityType);
        metadata.setEntityId(entityId);
        metadata.setMediaType(mediaType);
        metadata.setFileFormat(extractFileFormatFromFileName(fileName));
        metadata.setFileSizeBytes(fileSizeBytes);
        metadata.setWidth(width);
        metadata.setHeight(height);
        metadata.setStoragePath(storagePath);
        return metadata;
    }

    private String extractFileFormatFromFileName(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    private boolean isStaffOrAdmin(String role) {
        return isPlatformRole(role) || isRole(role, "TEACHER") || isRole(role, "SCHOOL_ADMIN");
    }

    private boolean isPlatformRole(String role) {
        return isRole(role, "CONTENT_ADMIN")
                || isRole(role, "STAFF");
    }

    private boolean isRole(String role, String expected) {
        if (role == null) return false;
        return expected.equals(role.toUpperCase(Locale.ROOT).replace("ROLE_", ""));
    }
}
