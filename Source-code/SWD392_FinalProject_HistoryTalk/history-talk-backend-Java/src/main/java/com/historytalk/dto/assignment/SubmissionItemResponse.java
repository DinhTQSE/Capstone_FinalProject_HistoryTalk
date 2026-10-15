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
public class SubmissionItemResponse {

    @Schema(description = "ID bản ghi submission")
    private UUID submissionId;

    @Schema(description = "UID học sinh")
    private UUID studentId;

    @Schema(description = "Mã số học sinh")
    private String studentCode;

    @Schema(description = "Họ tên học sinh")
    private String fullName;

    @Schema(description = "Email học sinh")
    private String email;

    @Schema(description = "Trạng thái nộp bài")
    private SubmissionStatus status;

    @Schema(description = "Điểm số quy về thang 10 (null nếu chưa nộp)")
    private BigDecimal score;

    @Schema(description = "Số lượt đối thoại AI (nếu là bài AI Chat)")
    private Integer messageCount;

    @Schema(description = "Số giây đọc bối cảnh (nếu là bài Reading)")
    private Integer readingDurationSeconds;

    @Schema(description = "Thời điểm bắt đầu làm")
    private LocalDateTime startedAt;

    @Schema(description = "Thời điểm nộp bài")
    private LocalDateTime submittedAt;
}
