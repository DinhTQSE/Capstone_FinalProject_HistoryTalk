package com.historytalk.dto.quiz.quizdata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Single-blank answer: answers list contains exactly one value for MVP. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FillInBlankAnswerData {
    private List<String> answers;
}
