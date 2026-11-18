package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocateStudentTokenRequest {

    /**
     * ID của học sinh cụ thể (sử dụng khi cấp lẻ cho 1 học sinh)
     */
    private UUID studentId;

    /**
     * Danh sách ID học sinh (sử dụng khi cấp hàng loạt cho nhiều học sinh)
     */
    private List<UUID> studentIds;

    /**
     * Số lượng Token cấp cho MỖI học sinh
     */
    @NotNull(message = "Số token không được để trống")
    @Min(value = 1, message = "Số token phải lớn hơn 0")
    private Integer tokenAmount;
}
