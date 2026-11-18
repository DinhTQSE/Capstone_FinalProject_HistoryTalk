package com.historytalk.dto.school;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeactivateStudentRequest {

    @NotBlank(message = "Lý do vô hiệu hóa không được để trống (ví dụ: GRADUATED, TRANSFERRED_OUT)")
    @Size(max = 255, message = "Lý do vô hiệu hóa không được vượt quá 255 ký tự")
    private String reason;
}
