package com.historytalk.dto.document;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadConfirmationRequest {
    @NotBlank(message = "Upload ID is required")
    private String uploadId;

    @NotBlank(message = "Storage path is required")
    private String storagePath;

    @NotBlank(message = "Content type is required")
    private String contentType;

    private Integer width;

    private Integer height;

    private String extendedMetadata;
}
