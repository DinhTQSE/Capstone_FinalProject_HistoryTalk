package com.historytalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.DocumentType;
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
    
    /** Optional when used from local content endpoints (id is in URL path). Required for global document endpoints. */
    @JsonProperty("contextId")
    private String contextId;
    
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
