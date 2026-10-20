package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.service.media.MediaService;
import com.historytalk.service.media.DirectBinaryUploadStrategy;
import com.historytalk.service.media.MediaUploadStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMediaServiceImpl implements MediaService {

    private static final long VIEW_URL_EXPIRES_IN_SECONDS = 3600;
    private static final long MAX_2D_SIZE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_3D_SIZE_BYTES = 100 * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final DocumentMediaMetadataRepository mediaMetadataRepository;
    private final HistoricalContextRepository historicalContextRepository;
    private final CharacterRepository characterRepository;
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

        // Validate media type
        MediaType mediaTypeEnum;
        try {
            mediaTypeEnum = MediaType.valueOf(mediaType);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Media type không hợp lệ: " + mediaType);
        }

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
            throw new InvalidRequestException("Media type không được hỗ trợ: " + mediaType);
        }

        // Validate file size
        if (file.getSize() > maxSize) {
            throw new InvalidRequestException("Kích thước file không được vượt quá " + (maxSize / (1024 * 1024)) + "MB");
        }

        // Generate storage path
        String storagePath = generateStoragePath(entityType, entityId, null, file.getOriginalFilename());

        try {
            // Upload file using DirectBinaryUploadStrategy
            directBinaryUploadStrategy.uploadMultipartFile(
                    storagePath, file, contentType, mediaType);

            // Save metadata
            DocumentMediaMetadata metadata = createMediaMetadata(
                    null, entityType, entityId, mediaTypeEnum, file.getOriginalFilename(),
                    file.getSize(), width, height, storagePath);

            mediaMetadataRepository.save(metadata);

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
            UUID entityId, EntityType entityType, Integer thumbnailWidth, Integer thumbnailHeight, String userId, String userRole) {

        DocumentMediaMetadata metadata = mediaMetadataRepository.findByEntityTypeAndEntityId(entityType, entityId)
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
    public void deleteMedia(UUID entityId, EntityType entityType, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền xóa media");
        }

        DocumentMediaMetadata metadata = mediaMetadataRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy media metadata: " + entityType + ":" + entityId));

        supabaseDocumentStorageService.deleteFile(metadata.getStoragePath());

        mediaMetadataRepository.delete(metadata);
        clearEntityMediaUrl(entityType, entityId);

        log.info("Deleted media for entity {}:{}", entityType, entityId);
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

    private void clearEntityMediaUrl(EntityType entityType, UUID entityId) {
        if (entityType == EntityType.CONTEXT && historicalContextRepository != null) {
            historicalContextRepository.findById(entityId).ifPresent(ctx -> {
                ctx.setImageUrl(null);
                ctx.setVideoUrl(null);
                historicalContextRepository.save(ctx);
            });
        } else if (entityType == EntityType.CHARACTER && characterRepository != null) {
            characterRepository.findById(entityId).ifPresent(chr -> {
                chr.setImageUrl(null);
                chr.setModelUrl(null);
                characterRepository.save(chr);
            });
        }
    }

    private String generateStoragePath(EntityType entityType, UUID entityId, UUID documentId, String fileName) {
        if (entityType == EntityType.USER) {
            return "users/%s/avatar/%s".formatted(entityId, fileName);
        }
        return "%s/%s/%s".formatted(
                entityType.name().toUpperCase(Locale.ROOT),
                entityId,
                fileName);
    }

    private DocumentMediaMetadataResponse mapToResponse(DocumentMediaMetadata metadata) {
        String supabaseUrl = environment.getProperty("supabase.url");
        String bucketName = environment.getProperty("supabase.storage.bucket", "documents");
        String publicUrl = null;
        
        if (supabaseUrl != null && metadata.getStoragePath() != null) {
            publicUrl = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + metadata.getStoragePath();
        }
        
        return DocumentMediaMetadataResponse.builder()
                .metadataId(metadata.getMetadataId().toString())
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
        if (role == null) return false;
        String r = role.toUpperCase(Locale.ROOT).replace("ROLE_", "");
        return "CONTENT_ADMIN".equals(r)
                || "SYSTEM_ADMIN".equals(r)
                || "STAFF".equals(r)
                || "ADMIN".equals(r)
                || "TEACHER".equals(r);
    }
}
