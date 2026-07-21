package com.historytalk.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUploadUrlResponse {
    private String uploadUrl;
    private String storagePath;
    private Long expiresIn;
    private String uploadId;
}
