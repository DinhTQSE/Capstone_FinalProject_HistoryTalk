package com.historytalk.dto.character;

import com.historytalk.entity.enums.DocumentType;
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
public class UpdateCharacterDocumentRequest {
    
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;
    
    @Size(min = 10, message = "Content must be at least 10 characters")
    private String content;

    @Size(max = 500, message = "File URL must be at most 500 characters")
    private String fileUrl;
    
    private DocumentType type;
}
