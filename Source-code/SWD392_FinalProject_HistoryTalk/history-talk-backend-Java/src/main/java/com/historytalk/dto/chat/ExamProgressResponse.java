package com.historytalk.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExamProgressResponse {

    @JsonProperty("totalQuestions")
    private int totalQuestions;

    @JsonProperty("answeredQuestions")
    private int answeredQuestions;

    @JsonProperty("correctCount")
    private int correctCount;

    @JsonProperty("partialCount")
    private int partialCount;

    @JsonProperty("incorrectCount")
    private int incorrectCount;

    /** Out of 10: a correct answer counts fully, a partial answer counts half. */
    @JsonProperty("score")
    private double score;

    @JsonProperty("finished")
    private boolean finished;
}
