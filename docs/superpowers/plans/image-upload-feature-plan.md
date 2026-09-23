# Image Upload Feature Plan - Including 3D Images (Performance-Optimized Architecture)

## Current State Analysis

### Existing Document Support
- **Entity**: `Document` with `fileUrl` (String)
- **DocumentType Enum**: TEXT, MARKDOWN, PDF
- **Current Upload**: Only PDF files supported via `MultipartFile` streaming through Java backend
- **Storage**: Supabase via `SupabaseDocumentStorageService`
- **Controller**: `DocumentFileController` with `uploadPdfFile()` endpoint
- **Service**: `DocumentFileService` with PDF-specific methods

### Current Limitations & Performance Risks
- No image upload support (2D or 3D)
- File streaming through Java backend causes double-bandwidth consumption
- No file type validation beyond PDF
- No size limits configuration for different file types
- No preview/thumbnail generation
- No 3D model viewer integration
- **CRITICAL**: Current architecture cannot handle large 3D files (up to 100MB) without OOM/GC pauses

---

## Feature Requirements

### 1. Image Types to Support
- **2D Images (Primary)**: JPG, PNG, WEBP, GIF
- **3D Models (Web-Native Primary)**: GLB, GLTF
- **3D Models (Legacy/Secondary)**: OBJ, FBX (require client-side conversion or server-side async processing)

### 2. Core Functionality (Performance-Optimized)
- **Presigned Upload Flow**: Client uploads directly to Supabase Storage (no Java streaming)
- Upload 2D images for documents
- Upload 3D models for documents
- Generate signed URLs for viewing
- **On-the-fly thumbnails** via Supabase Image Transformation (no Java processing)
- Support 3D model viewer integration
- File type validation (before presigned URL generation)
- Size limits per file type
- Metadata storage in dedicated table (not overloaded Document entity)

### 3. Use Cases
- Historical character portraits (2D images)
- Historical artifacts (3D models)
- Maps and illustrations (2D images)
- 3D reconstructions of historical sites

---

## Architecture Design

### 1. Database Schema (No Entity Overloading)

#### 1.1 Document Entity (Minimal Changes)
**File**: `src/main/java/com/historytalk/entity/document/Document.java`

**Changes**:
- Keep existing `fileUrl` field (for backward compatibility with PDF)
- Add `DocumentType.IMAGE_2D` and `DocumentType.IMAGE_3D` to enum
- **NO** individual media metadata columns added to Document entity

#### 1.2 New Entity: DocumentMediaMetadata
**New File**: `src/main/java/com/historytalk/entity/document/DocumentMediaMetadata.java`

```java
@Entity
@Table(name = "document_media_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMediaMetadata {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "metadata_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID metadataId;

    @Column(name = "document_id", columnDefinition = "uuid", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", length = 20, nullable = false)
    private MediaType mediaType; // IMAGE_2D, MODEL_3D

    @Column(name = "file_format", length = 20)
    private String fileFormat; // jpg, png, glb, gltf, obj, etc.

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "storage_path", length = 500, nullable = false)
    private String storagePath; // Supabase Storage path

    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath; // Optional custom thumbnail path

    @Column(name = "extended_metadata", columnDefinition = "jsonb")
    private String extendedMetadata; // JSONB for 3D model properties (polygon count, materials, etc.)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

#### 1.3 New Enum: MediaType
**New File**: `src/main/java/com/historytalk/entity/enums/MediaType.java`

```java
package com.historytalk.entity.enums;

public enum MediaType {
    IMAGE_2D,
    MODEL_3D
}
```

#### 1.4 Database Migration Script
**New File**: `src/main/resources/db/migration/V2__add_document_media_metadata.sql`

```sql
-- Create document_media_metadata table
CREATE TABLE document_media_metadata (
    metadata_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(doc_id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    file_format VARCHAR(20),
    file_size_bytes BIGINT,
    width INTEGER,
    height INTEGER,
    storage_path VARCHAR(500) NOT NULL,
    thumbnail_path VARCHAR(500),
    extended_metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on document_id for fast lookups
CREATE INDEX idx_document_media_metadata_document_id ON document_media_metadata(document_id);

-- Create index on media_type for filtering
CREATE INDEX idx_document_media_metadata_media_type ON document_media_metadata(media_type);

-- Update DocumentType enum (if using Flyway or manual)
-- ALTER TABLE document ALTER COLUMN document_type TYPE VARCHAR(50);
-- Note: DocumentType enum update handled by JPA/Hibernate
```

---

### 2. Presigned Upload Architecture (Direct-to-Storage)

#### 2.1 Upload Flow (No Java Streaming)
```
┌─────────────┐                    ┌─────────────────┐                    ┌──────────────┐
│   Client    │                    │  Java Backend   │                    │  Supabase     │
│             │                    │                 │                    │   Storage     │
└─────────────┘                    └─────────────────┘                    └──────────────┘
       │                                  │                                      │
       │  1. POST /{docId}/media/upload-url  │                                      │
       ├────────────────────────────────────▶│                                      │
       │                                  │  2. Validate & Generate Presigned URL│
       │                                  ├──────────────────────────────────────▶│
       │                                  │                                      │
       │  3. Return Presigned URL          │  4. Return Presigned URL             │
       │◀─────────────────────────────────┤◀──────────────────────────────────────┤
       │                                  │                                      │
       │  5. Upload File Directly          │                                      │
       ├──────────────────────────────────────────────────────────────────────────▶│
       │                                  │                                      │
       │  6. POST /{docId}/media/confirm   │                                      │
       ├────────────────────────────────────▶│                                      │
       │                                  │  7. Extract Metadata & Save to DB    │
       │                                  │                                      │
       │  8. Return Success                │                                      │
       │◀─────────────────────────────────┤                                      │
       │                                  │                                      │
```

#### 2.2 Benefits
- **Zero Java Memory Pressure**: No MultipartFile streaming in Java
- **Reduced Bandwidth**: Client uploads directly to Supabase (no double-hop)
- **Better Scalability**: Java backend handles only metadata, not file bytes
- **Faster Uploads**: Direct client-to-storage connection
- **Connection Pool Preservation**: Java HTTP threads not blocked by large uploads

---

### 3. API Consolidation (Unified REST Structure)

#### 3.1 New Controller: DocumentMediaController
**New File**: `src/main/java/com/historytalk/controller/document/DocumentMediaController.java`

```java
@RestController
@RequestMapping("/api/v1/documents/{docId}/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Media", description = "Media upload and management APIs (2D images, 3D models)")
public class DocumentMediaController {

    private final DocumentMediaService documentMediaService;

    @PostMapping("/upload-url")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get presigned upload URL for direct-to-storage upload")
    public ResponseEntity<ApiResponse<PresignedUploadUrlResponse>> getUploadUrl(
            @PathVariable String docId,
            @RequestBody @Valid MediaUploadRequest request) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/documents/{}/media/upload-url by user {}", docId, userId);
        PresignedUploadUrlResponse response = documentMediaService.generatePresignedUploadUrl(
                docId, request, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Presigned URL generated successfully"));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Confirm upload completion and store metadata")
    public ResponseEntity<ApiResponse<DocumentMediaMetadataResponse>> confirmUpload(
            @PathVariable String docId,
            @RequestBody @Valid MediaUploadConfirmationRequest request) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/documents/{}/media/confirm by user {}", docId, userId);
        DocumentMediaMetadataResponse response = documentMediaService.confirmUpload(
                docId, request, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Upload confirmed successfully"));
    }

    @GetMapping("/view-url")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'USER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get signed view URL for media")
    public ResponseEntity<ApiResponse<SignedViewUrlResponse>> getViewUrl(
            @PathVariable String docId,
            @RequestParam(required = false) Integer thumbnailWidth,
            @RequestParam(required = false) Integer thumbnailHeight) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        log.info("GET /api/v1/documents/{}/media/view-url by user {}", docId, userId);
        SignedViewUrlResponse response = documentMediaService.generateSignedViewUrl(
                docId, thumbnailWidth, thumbnailHeight, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(response, "View URL generated successfully"));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete media from document")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable String docId) {
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        log.info("DELETE /api/v1/documents/{}/media by user {}", docId, userId);
        documentMediaService.deleteMedia(docId, userId, userRole);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
```

#### 3.2 New DTOs
**New Files**:

`src/main/java/com/historytalk/dto/document/MediaUploadRequest.java`:
```java
public class MediaUploadRequest {
    @NotBlank
    private String fileName;

    @NotBlank
    private String contentType; // MIME type (image/jpeg, model/gltf-binary, etc.)

    @NotNull
    @Positive
    private Long fileSizeBytes;

    @NotBlank
    private String mediaType; // IMAGE_2D, MODEL_3D
}
```

`src/main/java/com/historytalk/dto/document/PresignedUploadUrlResponse.java`:
```java
public class PresignedUploadUrlResponse {
    private String uploadUrl; // Presigned URL for direct upload
    private String storagePath; // Storage path for confirmation
    private Long expiresIn; // URL expiration in seconds
    private String uploadId; // Unique upload ID for confirmation
}
```

`src/main/java/com/historytalk/dto/document/MediaUploadConfirmationRequest.java`:
```java
public class MediaUploadConfirmationRequest {
    @NotBlank
    private String uploadId;

    @NotBlank
    private String storagePath;

    @NotBlank
    private String contentType;

    private Integer width; // For images
    private Integer height; // For images

    private String extendedMetadata; // JSON string for 3D model properties
}
```

`src/main/java/com/historytalk/dto/document/SignedViewUrlResponse.java`:
```java
public class SignedViewUrlResponse {
    private String viewUrl;
    private String thumbnailUrl; // On-the-fly transformed URL
    private Long expiresIn;
}
```

---

## Implementation Plan

### Phase 1: Database & Entity Changes

#### 1.1 Update DocumentType Enum
**File**: `src/main/java/com/historytalk/entity/enums/DocumentType.java`

```java
public enum DocumentType {
    TEXT,
    MARKDOWN,
    PDF,
    IMAGE_2D,
    IMAGE_3D
}
```

#### 1.2 Create MediaType Enum
**File**: `src/main/java/com/historytalk/entity/enums/MediaType.java`

```java
public enum MediaType {
    IMAGE_2D,
    MODEL_3D
}
```

#### 1.3 Create DocumentMediaMetadata Entity
**File**: `src/main/java/com/historytalk/entity/document/DocumentMediaMetadata.java`

See Architecture Design section 1.2 for full entity definition.

#### 1.4 Create DocumentMediaMetadata Repository
**New File**: `src/main/java/com/historytalk/repository/DocumentMediaMetadataRepository.java`

```java
public interface DocumentMediaMetadataRepository extends JpaRepository<DocumentMediaMetadata, UUID> {
    Optional<DocumentMediaMetadata> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
```

#### 1.5 Database Migration
**File**: `src/main/resources/db/migration/V2__add_document_media_metadata.sql`

See Architecture Design section 1.4 for full migration script.

**Estimated Time**: 2-3 hours

---

### Phase 2: Service Layer Implementation

#### 2.1 Create DocumentMediaService Interface
**New File**: `src/main/java/com/historytalk/service/document/DocumentMediaService.java`

```java
public interface DocumentMediaService {
    PresignedUploadUrlResponse generatePresignedUploadUrl(
            String docId, MediaUploadRequest request, String userId, String userRole);

    DocumentMediaMetadataResponse confirmUpload(
            String docId, MediaUploadConfirmationRequest request, String userId, String userRole);

    SignedViewUrlResponse generateSignedViewUrl(
            String docId, Integer thumbnailWidth, Integer thumbnailHeight, String userId, String userRole);

    void deleteMedia(String docId, String userId, String userRole);
}
```

#### 2.2 Create DocumentMediaServiceImpl
**New File**: `src/main/java/com/historytalk/service/document/DocumentMediaServiceImpl.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentMediaServiceImpl implements DocumentMediaService {

    private static final long UPLOAD_URL_EXPIRES_IN_SECONDS = 300;
    private static final long VIEW_URL_EXPIRES_IN_SECONDS = 3600;

    private final DocumentRepository documentRepository;
    private final DocumentMediaMetadataRepository mediaMetadataRepository;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Override
    @Transactional(readOnly = true)
    public PresignedUploadUrlResponse generatePresignedUploadUrl(
            String docId, MediaUploadRequest request, String userId, String userRole) {

        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền tải media lên");
        }

        // Validate document exists
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        // Validate file type and size
        validateMediaUpload(request);

        // Generate storage path
        String storagePath = generateStoragePath(doc.getEntityType(), doc.getEntityId(), doc.getDocId(), request.getFileName());

        // Generate presigned upload URL from Supabase
        String presignedUrl = supabaseDocumentStorageService.generatePresignedUploadUrl(
                storagePath, request.getContentType(), UPLOAD_URL_EXPIRES_IN_SECONDS);

        // Generate unique upload ID
        String uploadId = UUID.randomUUID().toString();

        log.info("Generated presigned upload URL for document {} with uploadId {}", docId, uploadId);

        return PresignedUploadUrlResponse.builder()
                .uploadUrl(presignedUrl)
                .storagePath(storagePath)
                .expiresIn(UPLOAD_URL_EXPIRES_IN_SECONDS)
                .uploadId(uploadId)
                .build();
    }

    @Override
    @Transactional
    public DocumentMediaMetadataResponse confirmUpload(
            String docId, MediaUploadConfirmationRequest request, String userId, String userRole) {

        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền xác nhận upload");
        }

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        // Verify storage path matches document
        if (!request.getStoragePath().contains(doc.getDocId().toString())) {
            throw new InvalidRequestException("Storage path không khớp với document");
        }

        // Determine media type from content type
        MediaType mediaType = determineMediaType(request.getContentType());

        // Extract file format
        String fileFormat = extractFileFormat(request.getContentType());

        // Update document type
        if (mediaType == MediaType.IMAGE_2D) {
            doc.setDocumentType(DocumentType.IMAGE_2D);
        } else {
            doc.setDocumentType(DocumentType.IMAGE_3D);
        }

        Document savedDoc = documentRepository.save(doc);

        // Create media metadata record
        DocumentMediaMetadata metadata = DocumentMediaMetadata.builder()
                .documentId(savedDoc.getDocId())
                .mediaType(mediaType)
                .fileFormat(fileFormat)
                .width(request.getWidth())
                .height(request.getHeight())
                .storagePath(request.getStoragePath())
                .extendedMetadata(request.getExtendedMetadata())
                .build();

        DocumentMediaMetadata savedMetadata = mediaMetadataRepository.save(metadata);

        log.info("Confirmed upload for document {} with metadataId {}", docId, savedMetadata.getMetadataId());

        return mapToResponse(savedMetadata);
    }

    @Override
    @Transactional(readOnly = true)
    public SignedViewUrlResponse generateSignedViewUrl(
            String docId, Integer thumbnailWidth, Integer thumbnailHeight, String userId, String userRole) {

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        DocumentMediaMetadata metadata = mediaMetadataRepository.findByDocumentId(doc.getDocId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy media metadata: " + docId));

        // Generate signed view URL
        String viewUrl = supabaseDocumentStorageService.createSignedUrl(
                metadata.getStoragePath(), VIEW_URL_EXPIRES_IN_SECONDS);

        // Generate thumbnail URL using Supabase Image Transformation (on-the-fly)
        String thumbnailUrl = null;
        if (metadata.getMediaType() == MediaType.IMAGE_2D && (thumbnailWidth != null || thumbnailHeight != null)) {
            thumbnailUrl = generateThumbnailUrl(metadata.getStoragePath(), thumbnailWidth, thumbnailHeight);
        }

        return SignedViewUrlResponse.builder()
                .viewUrl(viewUrl)
                .thumbnailUrl(thumbnailUrl)
                .expiresIn(VIEW_URL_EXPIRES_IN_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public void deleteMedia(String docId, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền xóa media");
        }

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        DocumentMediaMetadata metadata = mediaMetadataRepository.findByDocumentId(doc.getDocId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy media metadata: " + docId));

        // Delete from Supabase Storage
        supabaseDocumentStorageService.deleteFile(metadata.getStoragePath());

        // Delete metadata record
        mediaMetadataRepository.delete(metadata);

        // Reset document type
        doc.setDocumentType(DocumentType.TEXT);
        documentRepository.save(doc);

        log.info("Deleted media for document {}", docId);
    }

    private void validateMediaUpload(MediaUploadRequest request) {
        // Validate file size
        long maxSizeBytes = request.getMediaType().equals("IMAGE_2D") ? 10 * 1024 * 1024 : 100 * 1024 * 1024;
        if (request.getFileSizeBytes() > maxSizeBytes) {
            throw new InvalidRequestException("File size exceeds maximum limit");
        }

        // Validate content type
        String contentType = request.getContentType().toLowerCase();
        if (request.getMediaType().equals("IMAGE_2D")) {
            if (!contentType.startsWith("image/")) {
                throw new InvalidRequestException("Invalid content type for 2D image");
            }
        } else {
            if (!contentType.contains("gltf") && !contentType.contains("model")) {
                throw new InvalidRequestException("Invalid content type for 3D model");
            }
        }
    }

    private String generateStoragePath(EntityType entityType, UUID entityId, UUID docId, String fileName) {
        return String.format("%s/%s/%s/%s", entityType.name().toLowerCase(), entityId, docId, fileName);
    }

    private MediaType determineMediaType(String contentType) {
        if (contentType.startsWith("image/")) {
            return MediaType.IMAGE_2D;
        }
        return MediaType.MODEL_3D;
    }

    private String extractFileFormat(String contentType) {
        if (contentType.startsWith("image/")) {
            return contentType.split("/")[1];
        }
        if (contentType.contains("gltf-binary")) {
            return "glb";
        }
        if (contentType.contains("gltf+json")) {
            return "gltf";
        }
        return "unknown";
    }

    private String generateThumbnailUrl(String storagePath, Integer width, Integer height) {
        // Supabase Image Transformation URL format
        // https://[project].supabase.co/storage/v1/object/public/[path]?width=300&height=300
        return storagePath + "?width=" + (width != null ? width : 300) + "&height=" + (height != null ? height : 300);
    }

    private DocumentMediaMetadataResponse mapToResponse(DocumentMediaMetadata metadata) {
        return DocumentMediaMetadataResponse.builder()
                .metadataId(metadata.getMetadataId().toString())
                .documentId(metadata.getDocumentId().toString())
                .mediaType(metadata.getMediaType().name())
                .fileFormat(metadata.getFileFormat())
                .fileSizeBytes(metadata.getFileSizeBytes())
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .storagePath(metadata.getStoragePath())
                .thumbnailPath(metadata.getThumbnailPath())
                .extendedMetadata(metadata.getExtendedMetadata())
                .createdAt(metadata.getCreatedAt())
                .updatedAt(metadata.getUpdatedAt())
                .build();
    }

    private boolean isStaffOrAdmin(String role) {
        return role != null && (
                "CONTENT_ADMIN".equalsIgnoreCase(role)
                        || "SYSTEM_ADMIN".equalsIgnoreCase(role)
                        || "STAFF".equalsIgnoreCase(role)
                        || "ADMIN".equalsIgnoreCase(role)
        );
    }
}
```

#### 2.3 Update SupabaseDocumentStorageService
**File**: `src/main/java/com/historytalk/service/document/SupabaseDocumentStorageService.java`

Add new methods:
```java
String generatePresignedUploadUrl(String storagePath, String contentType, long expiresIn);

String createSignedUrl(String storagePath, long expiresIn);

void deleteFile(String storagePath);
```

**Estimated Time**: 4-5 hours

---

### Phase 3: Controller & DTOs

#### 3.1 Create DocumentMediaController
**File**: `src/main/java/com/historytalk/controller/document/DocumentMediaController.java`

See Architecture Design section 3.1 for full controller definition.

#### 3.2 Create DTOs
**Files**:
- `src/main/java/com/historytalk/dto/document/MediaUploadRequest.java`
- `src/main/java/com/historytalk/dto/document/PresignedUploadUrlResponse.java`
- `src/main/java/com/historytalk/dto/document/MediaUploadConfirmationRequest.java`
- `src/main/java/com/historytalk/dto/document/SignedViewUrlResponse.java`
- `src/main/java/com/historytalk/dto/document/DocumentMediaMetadataResponse.java`

See Architecture Design section 3.2 for DTO definitions.

**Estimated Time**: 2-3 hours

---

### Phase 4: Configuration

#### 4.1 Update application.properties
```properties
# =============================================
# Media Upload Configuration
# =============================================
media.upload.2d.max-size-mb=10
media.upload.3d.max-size-mb=100
media.upload.allowed-2d-formats=jpg,jpeg,png,webp,gif
media.upload.allowed-3d-formats=glb,gltf,obj,fbx
media.upload.url-expires-seconds=300
media.view.url-expires-seconds=3600
media.thumbnail.default-width=300
media.thumbnail.default-height=300
```

**Estimated Time**: 30 minutes

---

### Phase 5: Testing

#### 5.1 Unit Tests
**New File**: `src/test/java/com/historytalk/service/document/DocumentMediaServiceImplTest.java`

Test cases:
- Test presigned URL generation
- Test file type validation
- Test size limit validation
- Test upload confirmation
- Test metadata extraction
- Test signed view URL generation
- Test thumbnail URL generation
- Test media deletion

#### 5.2 Integration Tests
**New File**: `src/test/java/com/historytalk/controller/document/DocumentMediaControllerTest.java`

Test cases:
- Test POST /{docId}/media/upload-url endpoint
- Test POST /{docId}/media/confirm endpoint
- Test GET /{docId}/media/view-url endpoint
- Test DELETE /{docId}/media endpoint
- Test error handling for invalid requests
- Test authorization checks

#### 5.3 Performance Tests
- Test presigned URL generation latency (should be < 100ms)
- Test upload confirmation latency (should be < 200ms)
- Test concurrent upload requests (100 concurrent requests)
- Test database query performance for metadata lookups

**Estimated Time**: 3-4 hours

---

## Implementation Order

1. **Phase 1**: Database & Entity Changes (2-3 hours)
2. **Phase 2**: Service Layer Implementation (4-5 hours)
3. **Phase 3**: Controller & DTOs (2-3 hours)
4. **Phase 4**: Configuration (30 minutes)
5. **Phase 5**: Testing (3-4 hours)

**Total Estimated Time**: 12-16 hours (backend only)

---

## Frontend Integration Guide

### Client-Side Upload Flow

#### Step 1: Request Presigned Upload URL
```typescript
const response = await fetch(`/api/v1/documents/${docId}/media/upload-url`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    fileName: 'portrait.jpg',
    contentType: 'image/jpeg',
    fileSizeBytes: 2048576,
    mediaType: 'IMAGE_2D'
  })
});

const { uploadUrl, storagePath, uploadId } = await response.json();
```

#### Step 2: Upload File Directly to Supabase
```typescript
await fetch(uploadUrl, {
  method: 'PUT',
  headers: {
    'Content-Type': 'image/jpeg'
  },
  body: file // File object from input
});
```

#### Step 3: Confirm Upload
```typescript
await fetch(`/api/v1/documents/${docId}/media/confirm`, {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    uploadId,
    storagePath,
    contentType: 'image/jpeg',
    width: 1920,
    height: 1080
  })
});
```

#### Step 4: Get View URL
```typescript
const response = await fetch(`/api/v1/documents/${docId}/media/view-url?thumbnailWidth=300&thumbnailHeight=300`, {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const { viewUrl, thumbnailUrl } = await response.json();
```

### 3D Model Viewer Integration

Use `<model-viewer>` web component for 3D models:
```html
<model-viewer
  src="${viewUrl}"
  alt="3D Model"
  auto-rotate
  camera-controls
  shadow-intensity="1"
></model-viewer>
```

---

## Risks & Mitigations

### Risk 1: Presigned URL Security
- **Mitigation**: Use short expiration times (5 minutes)
- **Mitigation**: Validate storage path matches document on confirmation
- **Mitigation**: Use unique upload IDs for confirmation verification

### Risk 2: Failed Uploads Without Confirmation
- **Mitigation**: Implement cleanup job for orphaned files in Supabase
- **Mitigation**: Add timeout for presigned URLs
- **Mitigation**: Client-side retry logic for failed uploads

### Risk 3: Supabase Image Transformation Limits
- **Mitigation**: Fallback to original image if transformation fails
- **Mitigation**: Cache transformed URLs
- **Mitigation**: Implement client-side thumbnail generation as fallback

### Risk 4: 3D Model Format Compatibility
- **Mitigation**: Validate format before presigned URL generation
- **Mitigation**: Provide clear error messages for unsupported formats
- **Mitigation**: Recommend client-side conversion for legacy formats (OBJ, FBX)

### Risk 5: Storage Costs
- **Mitigation**: Implement size limits
- **Mitigation**: Monitor storage usage
- **Mitigation**: Consider compression for 3D models
- **Mitigation**: Implement cleanup for deleted media

---

## Success Criteria

- [ ] Users can upload 2D images (JPG, PNG, WEBP, GIF) via presigned URLs
- [ ] Users can upload 3D models (GLB, GLTF) via presigned URLs
- [ ] File type validation works correctly before presigned URL generation
- [ ] Size limits are enforced (10MB for 2D, 100MB for 3D)
- [ ] Thumbnails generated via Supabase Image Transformation (no Java processing)
- [ ] Signed URLs work for viewing
- [ ] Metadata stored in dedicated table (not overloaded Document entity)
- [ ] No MultipartFile streaming through Java backend (zero memory pressure)
- [ ] Presigned URL generation latency < 100ms
- [ ] Upload confirmation latency < 200ms
- [ ] Error handling is robust
- [ ] Authorization checks work correctly
- [ ] 3D model viewer integration works with GLB/GLTF files
