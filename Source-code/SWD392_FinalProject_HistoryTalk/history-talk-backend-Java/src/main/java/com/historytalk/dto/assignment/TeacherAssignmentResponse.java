package com.historytalk.dto.assignment;

import com.historytalk.entity.enums.AssignmentStatus;
import com.historytalk.entity.enums.AssignmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeacherAssignmentResponse {

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

    @Schema(description = "Tổng số học sinh trong lớp")
    private long totalStudents;

    @Schema(description = "Số học sinh đã nộp đúng hạn")
    private long submittedCount;

    @Schema(description = "Số học sinh nộp trễ")
    private long lateSubmittedCount;

    @Schema(description = "Số học sinh đang làm bài")
    private long inProgressCount;

    @Schema(description = "Số học sinh chưa mở bài")
    private long notStartedCount;

    @Schema(description = "Thời điểm tạo bài")
    private LocalDateTime createdAt;
}
