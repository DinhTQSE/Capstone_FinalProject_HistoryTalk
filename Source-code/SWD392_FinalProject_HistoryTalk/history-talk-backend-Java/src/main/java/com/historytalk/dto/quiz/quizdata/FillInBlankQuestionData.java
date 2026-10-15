package com.historytalk.dto.quiz.quizdata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Single-blank question data (MVP). acceptedAnswers holds all valid answer strings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillInBlankQuestionData {
    private List<String> acceptedAnswers;
    private boolean caseSensitive;
    private boolean trimWhitespace;
}
