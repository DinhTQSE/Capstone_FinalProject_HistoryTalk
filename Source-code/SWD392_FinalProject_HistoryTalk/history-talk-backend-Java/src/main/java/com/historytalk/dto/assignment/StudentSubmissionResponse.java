package com.historytalk.dto.assignment;

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
public class StudentSubmissionResponse {

    @Schema(description = "ID bản ghi nộp bài")
    private UUID submissionId;

    @Schema(description = "ID bài tập")
    private UUID assignmentId;

    @Schema(description = "Trạng thái nộp bài: SUBMITTED, LATE_SUBMITTED")
    private SubmissionStatus status;

    @Schema(description = "Điểm số tính được trên thang điểm 10")
    private BigDecimal score;

    @Schema(description = "Số lượt trao đổi với AI (nếu là AI Chat)")
    private Integer messageCount;

    @Schema(description = "Số giây đọc bối cảnh (nếu là Context Reading)")
    private Integer readingDurationSeconds;

    @Schema(description = "Thời điểm bắt đầu làm")
    private LocalDateTime startedAt;

    @Schema(description = "Thời điểm nộp bài")
    private LocalDateTime submittedAt;
}
