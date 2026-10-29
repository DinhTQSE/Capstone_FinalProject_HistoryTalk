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
     * Get all non-deleted questions for a quiz — from both the direct FK column
     * and the quiz_question link table (bank-attached questions).
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.deletedAt IS NULL
            AND (
                (q.quiz IS NOT NULL AND q.quiz.quizId = :quizId)
                OR EXISTS (SELECT qq FROM QuizQuestion qq
                           WHERE qq.quiz.quizId = :quizId
                           AND qq.question.questionId = q.questionId)
            )
            ORDER BY q.createdAt ASC
            """)
    List<Question> findActiveByQuizId(@Param("quizId") UUID quizId);

    /**
     * Question Bank search: non-deleted questions, optionally filtered by type and content.
     * The `:search = ''` comparison types the parameter as text; without it PostgreSQL binds a null
     * :search as bytea and LOWER(bytea) fails, which made the whole bank load as empty.
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.deletedAt IS NULL
            AND (:questionType IS NULL OR q.questionType = :questionType)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(q.content) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY q.createdAt DESC
            """)
    Page<Question> findAllForBank(@Param("questionType") QuestionType questionType,
                                  @Param("search") String search,
                                  Pageable pageable);
}
