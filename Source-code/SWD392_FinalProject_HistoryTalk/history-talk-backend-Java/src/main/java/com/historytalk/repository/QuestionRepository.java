package com.historytalk.repository;

import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.entity.quiz.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    /**
     * Get all non-deleted questions linked to a quiz through quiz_question, in quiz order.
     */
    @Query("""
            SELECT q FROM QuizQuestion qq JOIN qq.question q
            WHERE qq.quiz.quizId = :quizId
              AND q.deletedAt IS NULL
            ORDER BY qq.orderIndex ASC, q.createdAt ASC
            """)
    List<Question> findActiveByQuizId(@Param("quizId") UUID quizId);

    @Query("""
            SELECT qq.quiz.quizId, q FROM QuizQuestion qq JOIN qq.question q
            WHERE qq.quiz.quizId IN :quizIds
              AND q.deletedAt IS NULL
            ORDER BY qq.orderIndex ASC, q.createdAt ASC
            """)
    List<Object[]> findActiveByQuizIds(@Param("quizIds") Collection<UUID> quizIds);

    // The composite PK keeps each (quiz, question) pair unique, so no DISTINCT is needed
    @Query("""
            SELECT qq.quiz.quizId, COUNT(q) FROM QuizQuestion qq JOIN qq.question q
            WHERE qq.quiz.quizId IN :quizIds
              AND q.deletedAt IS NULL
            GROUP BY qq.quiz.quizId
            """)
    List<Object[]> countActiveByQuizIds(@Param("quizIds") Collection<UUID> quizIds);

    /**
     * Question Bank search: non-deleted questions, optionally filtered by type, difficulty and content.
     * The `:search = ''` comparison types the parameter as text; without it PostgreSQL binds a null
     * :search as bytea and LOWER(bytea) fails, which made the whole bank load as empty.
     */
    @Query("""
            SELECT q FROM Question q
            WHERE q.deletedAt IS NULL
            AND (:questionType IS NULL OR q.questionType = :questionType)
            AND (:difficulty IS NULL OR q.difficulty = :difficulty)
            AND (:search IS NULL OR :search = ''
                 OR LOWER(q.content) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY q.createdAt DESC
            """)
    Page<Question> findAllForBank(@Param("questionType") QuestionType questionType,
                                  @Param("difficulty") QuestionDifficulty difficulty,
                                  @Param("search") String search,
                                  Pageable pageable);
}
