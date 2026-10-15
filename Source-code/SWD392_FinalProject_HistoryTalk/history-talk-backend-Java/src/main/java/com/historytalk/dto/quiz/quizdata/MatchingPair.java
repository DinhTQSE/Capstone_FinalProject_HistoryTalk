package com.historytalk.dto.quiz.quizdata;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchingPair {
    private String leftId;
    private String rightId;
}
