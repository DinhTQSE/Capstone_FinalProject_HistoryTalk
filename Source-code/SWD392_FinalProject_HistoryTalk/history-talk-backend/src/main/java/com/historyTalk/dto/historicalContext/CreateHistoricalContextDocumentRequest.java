package com.historyTalk.dto.historicalContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating historical context document
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateHistoricalContextDocumentRequest {
    
    @NotBlank(message = "Context ID is required")
    private String contextId;
    
    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;
    
    @NotBlank(message = "Content is required")
    @Size(min = 10, message = "Content must be at least 10 characters")
    private String content;
}
