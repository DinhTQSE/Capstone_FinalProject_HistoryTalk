package com.historyTalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizHistoryResponse {

    private String resultId;

    private String quizId;

    private String quizTitle;

    private Integer score;

    private Integer totalQuestions;

    private Double percentage;

    private Integer durationSeconds;

    private LocalDateTime completedAt;

}
