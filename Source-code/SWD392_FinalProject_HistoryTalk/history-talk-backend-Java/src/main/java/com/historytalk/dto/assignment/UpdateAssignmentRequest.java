package com.historytalk.dto.assignment;

import com.historytalk.entity.enums.AssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateAssignmentRequest {

    @Size(min = 3, max = 255, message = "Tiêu đề bài tập từ 3 đến 255 ký tự")
    @Schema(description = "Tiêu đề bài tập", example = "Tìm hiểu Bối cảnh Chiến thắng Bạch Đằng 938 (Gia hạn)")
    private String title;

    @Schema(description = "Mô tả / Lời dặn dò của giáo viên")
    private String description;

    @Schema(description = "Hạn nộp mới", example = "2026-10-28T23:59:59")
    private LocalDateTime deadline;

    @Schema(description = "Số lượng tiêu chuẩn yêu cầu để đạt điểm tối đa (thang 10)", example = "10")
    private Integer minRequirement;

    @Schema(description = "Trạng thái bài tập: ACTIVE, CANCELLED, ARCHIVED", example = "ACTIVE")
    private AssignmentStatus status;
}
