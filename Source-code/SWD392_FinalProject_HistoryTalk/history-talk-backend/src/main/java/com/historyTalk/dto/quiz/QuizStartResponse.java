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
public class QuizStartResponse {

    private String sessionId;

    private String quizId;

    private String title;

    private Integer durationSeconds;

    private List<QuestionResponse> questions;

}
