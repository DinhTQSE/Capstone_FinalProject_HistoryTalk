package com.historytalk.dto.document;

import com.historytalk.entity.enums.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMediaMetadataResponse {
    private String metadataId;
    private String documentId;            // Optional: Only set if media is attached to a Document
    private EntityType entityType;       // CHARACTER, CONTEXT, DOCUMENT, USER
    private String entityId;             // characterId, contextId, docId, or userId
    private String mediaType;
    private String fileFormat;
    private Long fileSizeBytes;
    private Integer width;
    private Integer height;
    private String storagePath;
    private String thumbnailPath;
    private String publicUrl;              // Full public URL for direct access
    private String extendedMetadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @com.fasterxml.jackson.annotation.JsonProperty("viewUrl")
    public String getViewUrl() {
        return publicUrl;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("objectPath")
    public String getObjectPath() {
        return storagePath;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("url")
    public String getUrl() {
        return publicUrl;
    }
}
