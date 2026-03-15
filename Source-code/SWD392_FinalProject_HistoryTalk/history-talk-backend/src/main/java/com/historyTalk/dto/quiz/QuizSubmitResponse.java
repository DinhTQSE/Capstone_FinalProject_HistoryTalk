package com.historyTalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSubmitResponse {

    private String resultId;

    private Integer score;

    private Integer totalQuestions;

    private Double percentage;

    private List<Integer> correctAnswers;

    private List<Integer> wrongAnswers;

}
