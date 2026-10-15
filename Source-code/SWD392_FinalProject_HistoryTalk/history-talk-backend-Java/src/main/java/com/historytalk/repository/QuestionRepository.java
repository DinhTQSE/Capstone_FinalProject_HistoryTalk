package com.historytalk.repository;

import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.quiz.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * Get all non-deleted questions for a quiz, ordered by insertion time.
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.quiz.quizId = :quizId
            AND q.deletedAt IS NULL
            ORDER BY q.createdAt ASC
            """)
    List<Question> findActiveByQuizId(@Param("quizId") UUID quizId);

    /**
     * Question Bank search: non-deleted questions, optionally filtered by type and content.
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.deletedAt IS NULL
            AND (:questionType IS NULL OR q.questionType = :questionType)
            AND (:search IS NULL OR LOWER(q.content) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY q.createdAt DESC
            """)
    Page<Question> findAllForBank(@Param("questionType") QuestionType questionType,
                                  @Param("search") String search,
                                  Pageable pageable);
}
