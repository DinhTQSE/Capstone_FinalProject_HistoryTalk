package com.historytalk.dto.historicalContext;

import com.historytalk.entity.enums.DocumentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoricalContextDocumentResponse {

    private String docId;
    private String contextId;
    private String uid;
    private String userName;
    private String title;
    private String content;
    private String fileUrl;
    private DocumentType type;
    private LocalDateTime uploadDate;
    private LocalDateTime updatedDate;
    private LocalDateTime deletedAt;
}
