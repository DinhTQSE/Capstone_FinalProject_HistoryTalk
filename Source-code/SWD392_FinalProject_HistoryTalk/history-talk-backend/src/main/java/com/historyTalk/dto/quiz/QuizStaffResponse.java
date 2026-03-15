package com.historyTalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizStaffResponse {

    private String quizId;

    private String title;

    private String description;

    private Integer grade;

    private Integer chapterNumber;

    private String chapterTitle;

    private String era;

    private Integer durationSeconds;

    private Integer playCount;

    private Double rating;

    private String contextId;

    private String contextTitle;

    private String createdBy;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;

    private List<QuestionResponse> questions;

}
