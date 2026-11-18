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
    @NotBlank(message = "Upload ID không được để trống")
    private String uploadId;

    @NotBlank(message = "Đường dẫn lưu trữ không được để trống")
    private String storagePath;

    @NotBlank(message = "Loại nội dung (content type) không được để trống")
    private String contentType;

    private Integer width;

    private Integer height;

    private String extendedMetadata;
}
