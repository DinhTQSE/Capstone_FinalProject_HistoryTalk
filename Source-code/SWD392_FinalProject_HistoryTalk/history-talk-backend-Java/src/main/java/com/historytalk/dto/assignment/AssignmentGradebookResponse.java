package com.historytalk.dto.assignment;

import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentGradebookResponse {

    @Schema(description = "ID bài tập")
    private UUID assignmentId;

    @Schema(description = "ID lớp học")
    private UUID classroomId;

    @Schema(description = "Tên lớp học")
    private String className;

    @Schema(description = "Tiêu đề bài tập")
    private String title;

    @Schema(description = "Mô tả bài tập")
    private String description;

    @Schema(description = "Loại bài tập")
    private AssignmentType assignmentType;

    @Schema(description = "ID nội dung mục tiêu")
    private UUID targetId;

    @Schema(description = "Tên nội dung mục tiêu (Bối cảnh/Nhân vật/Quiz)")
    private String targetTitle;

    @Schema(description = "Hạn chót nộp bài")
    private LocalDateTime deadline;

    @Schema(description = "Số lượng tiêu chuẩn yêu cầu để đạt điểm tối đa (thang 10)")
    private Integer minRequirement;

    @Schema(description = "Trạng thái bài tập")
    private AssignmentStatus status;

    @Schema(description = "Tổng số học sinh được giao")
    private int totalAssignedStudents;

    @Schema(description = "Số học sinh đã nộp")
    private int totalSubmittedStudents;

    @Schema(description = "Danh sách chi tiết bài làm của từng học sinh trong lớp")
    private List<SubmissionItemResponse> submissions;
}
