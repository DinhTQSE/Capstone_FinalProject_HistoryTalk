package com.historytalk.dto.document;

import com.historytalk.entity.enums.ContentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveDocumentRequest {
    @NotBlank(message = "Document ID is required")
    private String docId;

    @NotBlank(message = "Content is required")
    private String content;

    @NotNull(message = "Status is required")
    private ContentStatus status;
}
