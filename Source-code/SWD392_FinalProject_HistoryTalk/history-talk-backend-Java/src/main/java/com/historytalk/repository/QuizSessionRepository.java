package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizSession;
import com.historytalk.repository.dashboard.DashboardQuizTrendProjection;
import com.historytalk.repository.dashboard.StudentQuizSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {

    Optional<QuizSession> findBySessionId(UUID sessionId);

    @Query(value = """
            SELECT s FROM QuizSession s
            JOIN FETCH s.quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE s.user.uid = :uid
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.user.uid = :uid
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            """)
    Page<QuizSession> findCompletedByUserUid(@Param("uid") UUID uid, Pageable pageable);

    /** Completed sessions of one user for one quiz (quiz detail page history). */
    @Query(value = """
            SELECT s FROM QuizSession s
            JOIN FETCH s.quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE s.user.uid = :uid
            AND s.quiz.quizId = :quizId
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.user.uid = :uid
            AND s.quiz.quizId = :quizId
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            """)
    Page<QuizSession> findCompletedByUserUidAndQuizId(@Param("uid") UUID uid, @Param("quizId") UUID quizId, Pageable pageable);

    @Query(value = """
            SELECT s FROM QuizSession s
            JOIN FETCH s.quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            """)
    Page<QuizSession> findAllCompleted(Pageable pageable);

    @Query(value = """
            SELECT s FROM QuizSession s
            JOIN FETCH s.quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE s.user.uid = :uid
              AND s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """,
            countQuery = """
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.user.uid = :uid
              AND s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
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
            SELECT s.quiz.quizId, COUNT(s) FROM QuizSession s
            WHERE s.quiz.quizId IN :quizIds
            AND s.user.uid = :uid
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            GROUP BY s.quiz.quizId
            """)
    List<Object[]> countCompletedByQuizIdsAndUser(@Param("quizIds") Collection<UUID> quizIds, @Param("uid") UUID uid);

    @Query("""
            SELECT s FROM QuizSession s
            WHERE s.quiz.quizId = :quizId
              AND s.user.uid = :uid
              AND s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.score DESC NULLS LAST, s.endTime DESC
            """)
    List<QuizSession> findCompletedByQuizAndUser(@Param("quizId") UUID quizId, @Param("uid") UUID uid);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.quiz.quizId = :quizId
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            """)
    long countCompletedByQuiz(@Param("quizId") UUID quizId);

    @Query("""
            SELECT s.quiz.quizId, COUNT(s) FROM QuizSession s
            WHERE s.quiz.quizId IN :quizIds
            AND s.endTime IS NOT NULL
            AND s.deletedAt IS NULL
            GROUP BY s.quiz.quizId
            """)
    List<Object[]> countCompletedByQuizIds(@Param("quizIds") Collection<UUID> quizIds);

    @Query("SELECT COUNT(s) FROM QuizSession s WHERE s.deletedAt IS NULL AND s.quiz.deletedAt IS NULL")
    long countStartedSessions();

    @Query("SELECT COUNT(s) FROM QuizSession s WHERE s.deletedAt IS NULL AND s.quiz.deletedAt IS NULL AND s.endTime IS NOT NULL")
    long countCompletedSessions();

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.deletedAt IS NULL
              AND s.quiz.deletedAt IS NULL
              AND ((:isLocal = false AND s.quiz.historicalContext.school IS NULL)
                   OR (:isLocal = true AND s.quiz.historicalContext.school IS NOT NULL))
            """)
    long countStartedSessionsByScope(@Param("isLocal") boolean isLocal);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.deletedAt IS NULL
              AND s.quiz.deletedAt IS NULL
              AND s.endTime IS NOT NULL
              AND ((:isLocal = false AND s.quiz.historicalContext.school IS NULL)
                   OR (:isLocal = true AND s.quiz.historicalContext.school IS NOT NULL))
            """)
    long countCompletedSessionsByScope(@Param("isLocal") boolean isLocal);

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
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
            """, nativeQuery = true)
    Double averageScorePercentage();

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
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
              AND ((:isLocal = false AND hc.school_id IS NULL)
                   OR (:isLocal = true AND hc.school_id IS NOT NULL))
            """, nativeQuery = true)
    Double averageScorePercentageByScope(@Param("isLocal") boolean isLocal);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', qs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', qs.created_at)
                    ELSE date_trunc('day', qs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS started,
            COUNT(*) FILTER (WHERE qs.end_time IS NOT NULL) AS completed
            FROM quiz_session qs
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            WHERE qs.deleted_at IS NULL
              AND qs.created_at >= :from
              AND qs.created_at < :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardQuizTrendProjection> countSessionsByPeriod(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', qs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', qs.created_at)
                    ELSE date_trunc('day', qs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS started,
            COUNT(*) FILTER (WHERE qs.end_time IS NOT NULL) AS completed
            FROM quiz_session qs
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            WHERE qs.deleted_at IS NULL
              AND qs.created_at >= :from
              AND qs.created_at < :to
              AND ((:isLocal = false AND hc.school_id IS NULL)
                   OR (:isLocal = true AND hc.school_id IS NOT NULL))
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardQuizTrendProjection> countSessionsByPeriodAndScope(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("isLocal") boolean isLocal);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.deletedAt IS NULL
              AND s.quiz.deletedAt IS NULL
              AND s.quiz.historicalContext.deletedAt IS NULL
              AND s.quiz.historicalContext.school.id = :schoolId
            """)
    long countStartedSessionsBySchoolId(@Param("schoolId") UUID schoolId);

    @Query("""
            SELECT COUNT(s) FROM QuizSession s
            WHERE s.deletedAt IS NULL
              AND s.quiz.deletedAt IS NULL
              AND s.quiz.historicalContext.deletedAt IS NULL
              AND s.endTime IS NOT NULL
              AND s.quiz.historicalContext.school.id = :schoolId
            """)
    long countCompletedSessionsBySchoolId(@Param("schoolId") UUID schoolId);

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
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
              AND hc.school_id = :schoolId
            """, nativeQuery = true)
    Double averageScorePercentageBySchoolId(@Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT to_char(
                CASE
                    WHEN :bucket = 'month' THEN date_trunc('month', qs.created_at)
                    WHEN :bucket = 'week' THEN date_trunc('week', qs.created_at)
                    ELSE date_trunc('day', qs.created_at)
                END,
                'YYYY-MM-DD'
            ) AS period,
            COUNT(*) AS started,
            COUNT(*) FILTER (WHERE qs.end_time IS NOT NULL) AS completed
            FROM quiz_session qs
            JOIN quiz q ON q.quiz_id = qs.quiz_id AND q.deleted_at IS NULL
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            WHERE qs.deleted_at IS NULL
              AND qs.created_at >= :from
              AND qs.created_at < :to
              AND hc.school_id = :schoolId
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<DashboardQuizTrendProjection> countSessionsByPeriodAndSchoolId(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("bucket") String bucket,
            @Param("schoolId") UUID schoolId);

    long countByUser_UidAndEndTimeIsNotNullAndDeletedAtIsNull(UUID uid);

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
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
              AND qs.uid = :uid
            """, nativeQuery = true)
    Double averageScorePercentageByUser(@Param("uid") UUID uid);

    @Query(value = """
            SELECT 
                qs.uid AS "studentUid",
                COUNT(qs.session_id) AS "quizzesAttempted",
                COALESCE(AVG(
                    CASE
                        WHEN qs.score IS NOT NULL AND qc.question_count > 0
                        THEN qs.score * 100.0 / qc.question_count
                    END
                ), 0.0) AS "averageScorePercentage"
            FROM quiz_session qs
            LEFT JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = qs.quiz_id
            WHERE qs.deleted_at IS NULL
              AND qs.end_time IS NOT NULL
              AND qs.uid IN (:studentUids)
            GROUP BY qs.uid
            """, nativeQuery = true)
    List<StudentQuizSummaryProjection> findQuizSummariesByStudentUids(@Param("studentUids") Collection<UUID> studentUids);

    @Query("""
            SELECT s FROM QuizSession s
            JOIN FETCH s.quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE s.user.uid = :uid
              AND s.endTime IS NOT NULL
              AND s.deletedAt IS NULL
            ORDER BY s.endTime DESC
            """)
    List<QuizSession> findRecentCompletedByUser(@Param("uid") UUID uid, Pageable pageable);

    @Query(value = """
            SELECT CAST(hc.era AS text) AS era, COUNT(*) AS count
            FROM quiz_session qs
            JOIN quiz q ON q.quiz_id = qs.quiz_id
            JOIN historical_context hc ON hc.context_id = q.context_id
            WHERE qs.uid = :uid
              AND qs.end_time IS NOT NULL
              AND qs.deleted_at IS NULL
            GROUP BY hc.era
            """, nativeQuery = true)
    List<Object[]> countEraDistributionByUser(@Param("uid") UUID uid);
}
