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

    @NotBlank(message = "Deactivation reason must not be blank (e.g., GRADUATED, TRANSFERRED_OUT)")
    @Size(max = 255, message = "Deactivation reason must not exceed 255 characters")
    private String reason;
}
