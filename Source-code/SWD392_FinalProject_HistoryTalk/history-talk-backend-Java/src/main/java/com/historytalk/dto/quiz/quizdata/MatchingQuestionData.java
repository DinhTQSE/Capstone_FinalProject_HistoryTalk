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
public class MatchingQuestionData {
    private List<MatchingItem> leftItems;
    private List<MatchingItem> rightItems;
    /** One-to-one correct pairings (leftId → rightId). */
    private List<MatchingPair> correctPairs;
}
