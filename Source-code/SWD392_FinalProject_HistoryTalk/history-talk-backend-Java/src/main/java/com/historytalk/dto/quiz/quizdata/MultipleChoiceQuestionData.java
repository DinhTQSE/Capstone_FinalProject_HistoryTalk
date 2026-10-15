package com.historytalk.dto.quiz.quizdata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultipleChoiceQuestionData {
    private List<QuestionOption> options;
    /** IDs from options list that are correct (supports single or multi-correct) */
    private List<String> correctOptionIds;
}
