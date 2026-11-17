package com.historytalk.repository;

import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.repository.dashboard.DashboardTopQuizProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    /**
     * Customer — published quizzes only, optional title search (ILIKE).
     * Global quizzes (school IS NULL) are visible to everyone; local quizzes only to their school.
     */
    @Query("""
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.historicalContext hc
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND (:contextId IS NULL OR hc.contextId = :contextId)
            AND (hc.school IS NULL OR (:schoolId IS NOT NULL AND hc.school.id = :schoolId))
            AND q.isPublished = true
            AND q.deletedAt IS NULL
            ORDER BY q.title ASC
            """)
    List<Quiz> findAllActiveForCustomer(
            @Param("search") String search,
            @Param("contextId") UUID contextId,
            @Param("schoolId") UUID schoolId);

    /**
     * Customer — single published quiz by ID.
     */
    @Query("""
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.historicalContext
            WHERE q.quizId = :quizId
            AND q.isPublished = true
            AND q.deletedAt IS NULL
            """)
    Optional<Quiz> findActiveById(@Param("quizId") UUID quizId);

    /**
     * Staff — paginated list with optional search and era filter.
     * era is filtered on historicalContext.era (not on Quiz directly).
     * Platform admins (schoolId = null) see global quizzes; school staff see global + their own school's quizzes.
     */
    @Query(value = """
            SELECT q FROM Quiz q
            JOIN FETCH q.historicalContext hc
            LEFT JOIN FETCH q.createdBy
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND (:era IS NULL OR hc.era = :era)
            AND (:includeDeleted = true OR q.deletedAt IS NULL)
            AND (hc.school IS NULL OR (:schoolId IS NOT NULL AND hc.school.id = :schoolId))
            """,
            countQuery = """
            SELECT COUNT(q) FROM Quiz q
            JOIN q.historicalContext hc
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND (:era IS NULL OR hc.era = :era)
            AND (:includeDeleted = true OR q.deletedAt IS NULL)
            AND (hc.school IS NULL OR (:schoolId IS NOT NULL AND hc.school.id = :schoolId))
            """)
    Page<Quiz> findAllForStaff(
            @Param("search") String search,
            @Param("era") EventEra era,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("schoolId") UUID schoolId,
            Pageable pageable);

    @Query(value = """
            SELECT q FROM Quiz q
            LEFT JOIN FETCH q.historicalContext
            LEFT JOIN FETCH q.createdBy
            WHERE q.historicalContext.contextId = :contextId
              AND q.deletedAt IS NULL
            ORDER BY q.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(q) FROM Quiz q
            WHERE q.historicalContext.contextId = :contextId
              AND q.deletedAt IS NULL
            """)
    Page<Quiz> findByContextIdAndDeletedAtIsNull(@Param("contextId") UUID contextId, Pageable pageable);

    /**
     * Duplicate title check within tenant scope (global when schoolId is null, or same school) for active quizzes.
     */
    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END
            FROM Quiz q
            JOIN q.historicalContext hc
            WHERE LOWER(q.title) = LOWER(:title)
              AND q.deletedAt IS NULL
              AND ((:schoolId IS NULL AND hc.school IS NULL)
                   OR (:schoolId IS NOT NULL AND hc.school.id = :schoolId))
            """)
    boolean existsActiveByTitleInScope(@Param("title") String title, @Param("schoolId") UUID schoolId);

    /**
     * Duplicate title check within tenant scope for quiz update (exclude self).
     */
    @Query("""
            SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END
            FROM Quiz q
            JOIN q.historicalContext hc
            WHERE LOWER(q.title) = LOWER(:title)
              AND q.quizId <> :quizId
              AND q.deletedAt IS NULL
              AND ((:schoolId IS NULL AND hc.school IS NULL)
                   OR (:schoolId IS NOT NULL AND hc.school.id = :schoolId))
            """)
    boolean existsActiveByTitleInScopeAndQuizIdNot(
            @Param("title") String title,
            @Param("schoolId") UUID schoolId,
            @Param("quizId") UUID quizId);

    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.historicalContext LEFT JOIN FETCH q.createdBy WHERE q.deletedAt IS NOT NULL ORDER BY q.createdAt DESC")
    List<Quiz> findAllDeleted();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL")
    long countAllQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.isPublished = true")
    long countPublishedQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.isPublished = false")
    long countDraftQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NOT NULL")
    long countDeletedQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NULL")
    long countGlobalQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NULL AND q.isPublished = true")
    long countGlobalPublishedQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NULL AND q.isPublished = false")
    long countGlobalDraftQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NOT NULL")
    long countLocalQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NOT NULL AND q.isPublished = true")
    long countLocalPublishedQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.historicalContext.school IS NOT NULL AND q.isPublished = false")
    long countLocalDraftQuizzes();

    @Query(value = """
            SELECT CAST(q.quiz_id AS text) AS "quizId",
                   q.title AS title,
                   CAST(NULL AS varchar) AS level,
                   COUNT(qs.session_id) AS "startedSessions",
                   COUNT(qs.session_id) FILTER (WHERE qs.end_time IS NOT NULL) AS "completedSessions",
                   COALESCE(AVG(
                       CASE
                           WHEN qs.end_time IS NOT NULL
                            AND qs.score IS NOT NULL
                            AND qc.question_count > 0
                           THEN qs.score * 100.0 / qc.question_count
                       END
                   ), 0.0) AS "averageScorePercentage"
            FROM quiz q
            JOIN historical_context hc ON hc.context_id = q.context_id AND hc.deleted_at IS NULL
            LEFT JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = q.quiz_id
            LEFT JOIN quiz_session qs
              ON qs.quiz_id = q.quiz_id
             AND qs.deleted_at IS NULL
             AND qs.created_at >= :from
             AND qs.created_at < :to
            WHERE q.deleted_at IS NULL
              AND (:isLocal IS NULL
                   OR (:isLocal = false AND hc.school_id IS NULL)
                   OR (:isLocal = true AND hc.school_id IS NOT NULL))
            GROUP BY q.quiz_id, q.title
            ORDER BY "completedSessions" DESC, "startedSessions" DESC, q.title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopQuizProjection> findTopQuizzesForDashboardByScope(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("isLocal") Boolean isLocal,
            @Param("limit") int limit);

    @Query(value = """
            SELECT CAST(q.quiz_id AS text) AS "quizId",
                   q.title AS title,
                   CAST(NULL AS varchar) AS level,
                   COUNT(qs.session_id) AS "startedSessions",
                   COUNT(qs.session_id) FILTER (WHERE qs.end_time IS NOT NULL) AS "completedSessions",
                   COALESCE(AVG(
                       CASE
                           WHEN qs.end_time IS NOT NULL
                            AND qs.score IS NOT NULL
                            AND qc.question_count > 0
                           THEN qs.score * 100.0 / qc.question_count
                       END
                   ), 0.0) AS "averageScorePercentage"
            FROM quiz q
            LEFT JOIN (
                SELECT qq.quiz_id, COUNT(*) AS question_count
                FROM quiz_question qq
                JOIN question qu ON qu.question_id = qq.question_id
                WHERE qu.deleted_at IS NULL
                GROUP BY qq.quiz_id
            ) qc ON qc.quiz_id = q.quiz_id
            LEFT JOIN quiz_session qs
              ON qs.quiz_id = q.quiz_id
             AND qs.deleted_at IS NULL
             AND qs.created_at >= :from
             AND qs.created_at < :to
            WHERE q.deleted_at IS NULL
            GROUP BY q.quiz_id, q.title
            ORDER BY "completedSessions" DESC, "startedSessions" DESC, q.title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopQuizProjection> findTopQuizzesForDashboard(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("limit") int limit);
}
