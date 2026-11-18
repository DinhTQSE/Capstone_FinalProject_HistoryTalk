package com.historytalk.dto.document;

import com.historytalk.entity.enums.ContentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveDocumentRequest {
    @NotBlank(message = "ID tài liệu không được để trống")
    private String docId;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @NotNull(message = "Trạng thái không được để trống")
    private ContentStatus status;
}
