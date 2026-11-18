package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopUpSchoolTokensRequest {

    @NotNull(message = "Số token nạp thêm không được để trống")
    @Min(value = 1000, message = "Số token nạp thêm tối thiểu là 1.000")
    private Integer additionalTokens;

    private String note;
}
