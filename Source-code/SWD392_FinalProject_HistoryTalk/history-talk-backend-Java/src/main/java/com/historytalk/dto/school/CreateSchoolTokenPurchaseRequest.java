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
public class CreateSchoolTokenPurchaseRequest {

    @NotNull(message = "Số lượng token mua thêm không được để trống")
    @Min(value = 100_000, message = "Số lượng token mua thêm tối thiểu là 100.000 tokens")
    private Long tokens;
}
