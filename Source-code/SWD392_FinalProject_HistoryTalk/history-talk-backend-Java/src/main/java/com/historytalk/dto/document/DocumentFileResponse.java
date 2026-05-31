package com.historytalk.dto.document;

import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentFileResponse {
    private String docId;
    private String entityId;
    private EntityType entityType;
    private String title;
    private String fileUrl;
    private DocumentType type;
    private LocalDateTime uploadDate;
    private LocalDateTime updatedDate;
}
