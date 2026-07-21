package com.historytalk.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedViewUrlResponse {
    private String viewUrl;
    private String thumbnailUrl;
    private Long expiresIn;
}
