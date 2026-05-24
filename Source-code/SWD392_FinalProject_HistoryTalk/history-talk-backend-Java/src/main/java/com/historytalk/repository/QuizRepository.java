package com.historytalk.repository;

import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.quiz.Quiz;
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
     * Customer — active quizzes only, optional title search (ILIKE).
     */
    @Query("""
            SELECT q FROM Quiz q
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND q.isActive = true
            AND q.deletedAt IS NULL
            ORDER BY q.title ASC
            """)
    List<Quiz> findAllActiveForCustomer(@Param("search") String search);

    /**
     * Customer — single active quiz by ID.
     */
    @Query("""
            SELECT q FROM Quiz q
            WHERE q.quizId = :quizId
            AND q.isActive = true
            AND q.deletedAt IS NULL
            """)
    Optional<Quiz> findActiveById(@Param("quizId") UUID quizId);

    /**
     * Staff — paginated list with optional search and era filter.
     * era is filtered on historicalContext.era (not on Quiz directly).
     * Shows all non-deleted quizzes regardless of isActive.
     */
    @Query("""
            SELECT q FROM Quiz q
            JOIN q.historicalContext hc
            WHERE (CAST(:search AS string) IS NULL OR q.title ILIKE CONCAT('%', CAST(:search AS string), '%'))
            AND (:era IS NULL OR hc.era = :era)
            AND q.deletedAt IS NULL
            """)
    Page<Quiz> findAllForStaff(
            @Param("search") String search,
            @Param("era") EventEra era,
            Pageable pageable);

    /**
     * Duplicate title check for new quiz creation.
     */
    boolean existsByTitleIgnoreCase(String title);

    /**
     * Duplicate title check for quiz update (exclude self).
     */
    boolean existsByTitleIgnoreCaseAndQuizIdNot(String title, UUID quizId);
}
