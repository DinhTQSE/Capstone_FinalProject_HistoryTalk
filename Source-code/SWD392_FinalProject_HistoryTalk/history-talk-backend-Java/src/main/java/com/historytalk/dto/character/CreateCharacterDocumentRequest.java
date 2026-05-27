package com.historytalk.dto.character;

import com.historytalk.entity.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating character document
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCharacterDocumentRequest {
    
    @NotBlank(message = "Character ID is required")
    private String characterId;
    
    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;
    
    @NotBlank(message = "Content is required")
    @Size(min = 10, message = "Content must be at least 10 characters")
    private String content;

    @Size(max = 500, message = "File URL must be at most 500 characters")
    private String fileUrl;
    
    private DocumentType type;
}
