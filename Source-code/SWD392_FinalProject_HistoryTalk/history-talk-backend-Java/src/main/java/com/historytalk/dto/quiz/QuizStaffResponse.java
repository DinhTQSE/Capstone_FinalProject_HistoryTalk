package com.historytalk.dto.quiz;

import com.historytalk.entity.enums.ContentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response for GET /staff/quizzes and GET /staff/quizzes/:quizId.
 * Shape matches contract ContentAdminQuizSet.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizStaffResponse {

    private String quizId;

    private String title;

    /** ANCIENT | MEDIEVAL | MODERN | CONTEMPORARY — sourced from historicalContext.era */
    private String era;

    /** EASY | MEDIUM | HARD */
    private String level;

    /** Total completed sessions across all users */
    private int playCount;

    private String contextId;

    private String contextTitle;

    /** Username of the staff member who created this quiz */
    private String createdBy;

    private LocalDateTime createdDate;

    private LocalDateTime updatedDate;

    private Boolean isPublished;

    private ContentStatus status;

    private List<QuestionResponse> questions;
}
