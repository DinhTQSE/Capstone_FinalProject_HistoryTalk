package com.historytalk.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadRequest {
    @NotBlank(message = "Tên tệp không được để trống")
    private String fileName;

    @NotBlank(message = "Loại nội dung (content type) không được để trống")
    private String contentType;

    @NotNull(message = "Kích thước tệp không được để trống")
    @Positive(message = "Kích thước tệp phải lớn hơn 0")
    private Long fileSizeBytes;

    @NotBlank(message = "Loại media không được để trống")
    private String mediaType;
}
