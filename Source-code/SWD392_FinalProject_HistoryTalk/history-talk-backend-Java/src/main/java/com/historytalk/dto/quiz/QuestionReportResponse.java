package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionReportResponse {

    private String reportId;
    private String questionId;
    private String questionContent;
    private String quizId;
    private String quizTitle;
    private String reportedBy;
    private String reason;
    private String status;
    private LocalDateTime createdAt;
}
