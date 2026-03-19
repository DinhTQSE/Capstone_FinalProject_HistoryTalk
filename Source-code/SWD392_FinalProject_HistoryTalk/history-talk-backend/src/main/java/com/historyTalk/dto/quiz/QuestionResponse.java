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
public class QuestionResponse {

    private String questionId;

    private String content;

    private List<String> options;

    private Integer correctAnswer;

    private Integer orderIndex;

    private String explanation;

}
