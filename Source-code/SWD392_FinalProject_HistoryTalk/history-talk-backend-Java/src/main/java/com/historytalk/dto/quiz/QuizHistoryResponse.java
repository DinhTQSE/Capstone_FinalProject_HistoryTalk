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
public class QuizHistoryResponse {

    private String sessionId;

    private String quizId;

    private String quizTitle;

    private Double score;

    private Integer totalQuestions;

    private Double percentage;

    private Integer timeSpentSeconds;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

}
