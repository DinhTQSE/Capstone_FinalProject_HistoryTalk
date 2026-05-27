package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizAnswerDetail;
import com.historytalk.repository.dashboard.DashboardTopWrongQuestionProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuizAnswerDetailRepository extends JpaRepository<QuizAnswerDetail, UUID> {

    @Query(value = """
            SELECT CAST(qad.question_id AS text) AS "questionId",
                   CAST(q.quiz_id AS text) AS "quizId",
                   q.title AS "quizTitle",
                   COUNT(*) FILTER (WHERE qad.is_correct = false) AS "wrongAnswers",
                   COUNT(*) AS "totalAnswers",
                   CASE
                       WHEN COUNT(*) = 0 THEN 0.0
                       ELSE COUNT(*) FILTER (WHERE qad.is_correct = false) * 1.0 / COUNT(*)
                   END AS "wrongRate"
            FROM quiz_answer_detail qad
            JOIN question question ON question.question_id = qad.question_id
            JOIN quiz q ON q.quiz_id = question.quiz_id
            JOIN quiz_session qs ON qs.session_id = qad.session_id
            WHERE qad.deleted_at IS NULL
              AND question.deleted_at IS NULL
              AND q.deleted_at IS NULL
              AND qs.deleted_at IS NULL
              AND qad.created_at >= :from
              AND qad.created_at < :to
            GROUP BY qad.question_id, q.quiz_id, q.title
            HAVING COUNT(*) > 0
            ORDER BY "wrongRate" DESC, "wrongAnswers" DESC, q.title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopWrongQuestionProjection> findTopWrongQuestionsForDashboard(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("limit") int limit);
}
