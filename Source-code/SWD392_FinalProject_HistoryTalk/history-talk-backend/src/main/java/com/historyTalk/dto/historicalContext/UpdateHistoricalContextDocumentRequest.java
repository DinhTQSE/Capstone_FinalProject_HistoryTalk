package com.historyTalk.dto.historicalContext;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating historical context document
 */
@Data

@AllArgsConstructor
@Builder
public class UpdateHistoricalContextDocumentRequest {
    
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;
    
    @Size(min = 10, message = "Content must be at least 10 characters")
    private String content;
}
