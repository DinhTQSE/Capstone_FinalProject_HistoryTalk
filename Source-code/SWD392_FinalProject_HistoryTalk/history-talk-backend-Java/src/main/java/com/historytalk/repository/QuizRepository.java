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
     */
    @Query("""
            SELECT q FROM Quiz q
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND q.isPublished = true
            AND q.deletedAt IS NULL
            ORDER BY q.title ASC
            """)
    List<Quiz> findAllActiveForCustomer(@Param("search") String search);

    /**
     * Customer — single published quiz by ID.
     */
    @Query("""
            SELECT q FROM Quiz q
            WHERE q.quizId = :quizId
            AND q.isPublished = true
            AND q.deletedAt IS NULL
            """)
    Optional<Quiz> findActiveById(@Param("quizId") UUID quizId);

    /**
     * Staff — paginated list with optional search and era filter.
     * era is filtered on historicalContext.era (not on Quiz directly).
     * Shows all non-deleted quizzes regardless of isPublished.
     */
    @Query("""
            SELECT q FROM Quiz q
            JOIN q.historicalContext hc
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND (:era IS NULL OR hc.era = :era)
            AND (:includeDeleted = true OR q.deletedAt IS NULL)
            """)
    Page<Quiz> findAllForStaff(
            @Param("search") String search,
            @Param("era") EventEra era,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

    /**
     * Duplicate title check for new quiz creation.
     */
    boolean existsByTitleIgnoreCase(String title);

    /**
     * Duplicate title check for quiz update (exclude self).
     */
    boolean existsByTitleIgnoreCaseAndQuizIdNot(String title, UUID quizId);

    @Query("SELECT q FROM Quiz q WHERE q.deletedAt IS NOT NULL ORDER BY q.createdAt DESC")
    List<Quiz> findAllDeleted();

    @Query("SELECT COUNT(q) FROM Quiz q")
    long countAllQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.isPublished = true")
    long countPublishedQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NULL AND q.isPublished = false")
    long countDraftQuizzes();

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.deletedAt IS NOT NULL")
    long countDeletedQuizzes();

    @Query(value = """
            SELECT CAST(q.quiz_id AS text) AS "quizId",
                   q.title AS title,
                   q.level AS level,
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
                SELECT quiz_id, COUNT(*) AS question_count
                FROM question
                WHERE deleted_at IS NULL
                GROUP BY quiz_id
            ) qc ON qc.quiz_id = q.quiz_id
            LEFT JOIN quiz_session qs
              ON qs.quiz_id = q.quiz_id
             AND qs.deleted_at IS NULL
             AND qs.created_at >= :from
             AND qs.created_at < :to
            WHERE q.deleted_at IS NULL
            GROUP BY q.quiz_id, q.title, q.level
            ORDER BY "completedSessions" DESC, "startedSessions" DESC, q.title ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardTopQuizProjection> findTopQuizzesForDashboard(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to,
            @Param("limit") int limit);
}
