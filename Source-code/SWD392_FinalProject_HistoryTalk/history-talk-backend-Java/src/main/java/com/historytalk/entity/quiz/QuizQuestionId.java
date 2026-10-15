package com.historytalk.entity.quiz;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class QuizQuestionId implements Serializable {

    private UUID quiz;
    private UUID question;

    public QuizQuestionId() {}

    public QuizQuestionId(UUID quiz, UUID question) {
        this.quiz = quiz;
        this.question = question;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuizQuestionId other)) return false;
        return Objects.equals(quiz, other.quiz) && Objects.equals(question, other.question);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quiz, question);
    }
}
