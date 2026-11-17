package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
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
public class ReviewTokenBoostRequestDto {

    @NotBlank(message = "Hành động phải là APPROVE hoặc REJECT")
    private String action;

    @Min(value = 100, message = "Số token được duyệt tối thiểu là 100")
    private Integer approvedTokens;

    @Size(max = 500, message = "Ghi chú duyệt không được vượt quá 500 ký tự")
    private String reviewNote;
}
