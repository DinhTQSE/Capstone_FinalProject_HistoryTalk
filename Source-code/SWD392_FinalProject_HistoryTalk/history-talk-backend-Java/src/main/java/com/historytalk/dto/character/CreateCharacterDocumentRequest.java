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
    
    @NotBlank(message = "Yêu cầu Character ID")
    private String characterId;
    
    @NotBlank(message = "Yêu cầu tiêu đề")
    @Size(min = 3, max = 255, message = "Tiêu đề phải từ 3 đến 255 ký tự")
    private String title;
    
    @NotBlank(message = "Yêu cầu nội dung")
    @Size(min = 10, message = "Nội dung phải có ít nhất 10 ký tự")
    private String content;

    @Size(max = 500, message = "URL File tối đa 500 ký tự")
    private String fileUrl;
    
    private DocumentType type;
}
