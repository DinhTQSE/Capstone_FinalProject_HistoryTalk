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
    
    @Size(min = 3, max = 255, message = "Tiêu đề phải từ 3 đến 255 ký tự")
    private String title;
    
    @Size(min = 10, message = "Nội dung phải có ít nhất 10 ký tự")
    private String content;

    @Size(max = 500, message = "URL File tối đa 500 ký tự")
    private String fileUrl;
    
    private DocumentType type;
}
