package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.repository.dashboard.DashboardQuizTrendProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    Optional<QuizSession> findBySessionId(UUID sessionId);

    @Query("""
            SELECT s FROM QuizSession s
            WHERE s.user.uid = :uid
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """)
    Page<QuizSession> findCompletedByUserUid(@Param("uid") UUID uid, Pageable pageable);

    @Query("""
            SELECT s FROM QuizSession s
            WHERE s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """)
    Page<QuizSession> findAllCompleted(Pageable pageable);

    @Query("""
            SELECT s FROM QuizSession s
            WHERE s.user.uid = :uid
              AND s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """)
    Page<QuizSession> findCompletedByUserUidForAdmin(@Param("uid") UUID uid, Pageable pageable);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.quiz.quizId = :quizId
            AND s.user.uid = :uid
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            """)
    long countCompletedByQuizAndUser(@Param("quizId") UUID quizId, @Param("uid") UUID uid);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.quiz.quizId = :quizId
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            """)
    long countCompletedByQuiz(@Param("quizId") UUID quizId);

    @Query("SELECT COUNT(s) FROM QuizSession s WHERE s.deletedAt IS NULL")
    long countStartedSessions();

    @Query("SELECT COUNT(s) FROM QuizSession s WHERE s.deletedAt IS NULL AND s.endTime IS NOT NULL")
    long countCompletedSessions();

    @Query(value = """
            SELECT COALESCE(AVG(
                CASE
                    WHEN qs.end_time IS NOT NULL
                     AND qs.score IS NOT NULL
                     AND qc.question_count > 0
                    THEN qs.score * 100.0 / qc.question_count
                END
            ), 0.0)
            FROM quiz_session qs
            JOIN (
                SELECT quiz_id, COUNT(*) AS question_count
                FROM question
                WHERE deleted_at IS NULL
                GROUP BY quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
            """, nativeQuery = true)
    Double averageScorePercentage();

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', created_at)
                    ELSE date_trunc('day', created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS started,
            COUNT(*) FILTER (WHERE end_time IS NOT NULL) AS completed
            FROM quiz_session
            WHERE deleted_at IS NULL
              AND created_at >= :from
              AND created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardQuizTrendProjection> countSessionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);
}
