package com.historytalk.dto.assignment;

import com.historytalk.entity.enums.AssignmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAssignmentRequest {

    @NotNull(message = "classId không được để trống")
    @Schema(description = "ID lớp học được giao bài", example = "e4b2d13a-7a54-46c5-a1c2-8495f5602d1a")
    private UUID classId;

    @NotBlank(message = "title không được để trống")
    @Size(min = 3, max = 255, message = "Tiêu đề bài tập từ 3 đến 255 ký tự")
    @Schema(description = "Tiêu đề bài tập", example = "Tìm hiểu Bối cảnh Chiến thắng Bạch Đằng 938")
    private String title;

    @Schema(description = "Mô tả / Lời dặn dò của giáo viên", example = "Các em đọc kỹ bối cảnh và chuẩn bị câu hỏi thảo luận.")
    private String description;

    @NotNull(message = "assignmentType không được để trống")
    @Schema(description = "Loại bài tập: CONTEXT_READING, AI_CHAT_ROLEPLAY, QUIZ_TEST", example = "CONTEXT_READING")
    private AssignmentType assignmentType;

    @NotNull(message = "targetId không được để trống")
    @Schema(description = "ID nội dung được chọn (contextId, characterId, hoặc quizId)", example = "7c12f4b0-3d89-4a92-b432-11a5b8e90c23")
    private UUID targetId;

    @NotNull(message = "deadline không được để trống")
    @Future(message = "Hạn nộp bài phải ở tương lai")
    @Schema(description = "Hạn chót nộp bài", example = "2026-10-25T23:59:59")
    private LocalDateTime deadline;

    @Positive(message = "Tiêu chuẩn tối thiểu phải lớn hơn 0")
    @Schema(description = "Số lượng tiêu chuẩn yêu cầu để đạt tối đa 10 điểm (số tin nhắn chat AI hoặc số giây đọc)", example = "10")
    private Integer minRequirement;
}
