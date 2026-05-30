package com.historytalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response body for POST /api/v1/staff/quizzes/import
 *
 * <ul>
 *   <li>{@code totalQuizzesAttempted} – number of distinct quiz titles found in the CSV.</li>
 *   <li>{@code successCount} – number of quizzes successfully persisted.</li>
 *   <li>{@code skippedCount} – number of quiz groups that were skipped due to errors.</li>
 *   <li>{@code errors} – human-readable messages describing each skipped quiz and why.</li>
 *   <li>{@code imported} – full staff detail of every successfully created quiz.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizImportResponse {

    private int totalQuizzesAttempted;
    private int successCount;
    private int skippedCount;
    private List<String> errors;
    private List<QuizStaffResponse> imported;
}
