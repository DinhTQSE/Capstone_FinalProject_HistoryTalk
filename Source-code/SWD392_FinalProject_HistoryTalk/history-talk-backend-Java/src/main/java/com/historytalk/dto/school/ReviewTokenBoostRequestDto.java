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

    @NotBlank(message = "Thao tác phải là APPROVE, REJECT hoặc ESCALATE")
    private String action;

    @Min(value = 100, message = "Số token duyệt tối thiểu là 100")
    private Integer approvedTokens;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String reviewNote;
}
