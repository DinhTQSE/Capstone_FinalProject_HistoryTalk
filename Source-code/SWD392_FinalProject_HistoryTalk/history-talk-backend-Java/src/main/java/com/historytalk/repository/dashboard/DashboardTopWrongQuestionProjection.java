package com.historytalk.repository.dashboard;

public interface DashboardTopWrongQuestionProjection {

    String getQuestionId();

    String getQuestionContent();

    String getQuizId();

    String getQuizTitle();

    Long getWrongAnswers();

    Long getTotalAnswers();

    Double getWrongRate();
}
