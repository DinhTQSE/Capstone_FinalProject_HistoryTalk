package com.historytalk.dto.assignment;

import com.historytalk.entity.enums.AssignmentType;
import com.historytalk.entity.enums.SubmissionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentAssignmentResponse {

    @Schema(description = "ID bài tập")
    private UUID assignmentId;

    @Schema(description = "ID lớp học")
    private UUID classroomId;

    @Schema(description = "Tên lớp học")
    private String className;

    @Schema(description = "Tiêu đề bài tập")
    private String title;

    @Schema(description = "Mô tả / Lời dặn dò của giáo viên")
    private String description;

    @Schema(description = "Loại bài tập: CONTEXT_READING, AI_CHAT_ROLEPLAY, QUIZ_TEST")
    private AssignmentType assignmentType;

    @Schema(description = "ID nội dung mục tiêu")
    private UUID targetId;

    @Schema(description = "Tên nội dung mục tiêu (Bối cảnh/Nhân vật/Quiz)")
    private String targetTitle;

    @Schema(description = "Hạn chót nộp bài")
    private LocalDateTime deadline;

    @Schema(description = "Số lượng tiêu chuẩn yêu cầu để đạt tối đa 10 điểm (tin nhắn chat hoặc giây đọc)")
    private Integer minRequirement;

    @Schema(description = "Trạng thái bài làm của học sinh: NOT_STARTED, IN_PROGRESS, SUBMITTED, LATE_SUBMITTED, MISSED, EXEMPTED")
    private SubmissionStatus submissionStatus;

    @Schema(description = "Điểm số quy về thang 10 (null nếu chưa có điểm)")
    private BigDecimal score;

    @Schema(description = "Số lượt đối thoại AI (nếu là AI Chat)")
    private Integer messageCount;

    @Schema(description = "Số giây đọc bối cảnh (nếu là Context Reading)")
    private Integer readingDurationSeconds;

    @Schema(description = "Thời điểm bắt đầu làm")
    private LocalDateTime startedAt;

    @Schema(description = "Thời điểm nộp bài")
    private LocalDateTime submittedAt;

    @Schema(description = "Đã quá hạn deadline hay chưa")
    private boolean isExpired;
}
