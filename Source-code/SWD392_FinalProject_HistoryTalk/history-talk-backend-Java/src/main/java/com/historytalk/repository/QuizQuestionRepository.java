package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizQuestion;
import com.historytalk.entity.quiz.QuizQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, QuizQuestionId> {

    @Query("""
            SELECT qq FROM QuizQuestion qq
            JOIN FETCH qq.question q
            WHERE qq.quiz.quizId = :quizId
            AND q.deletedAt IS NULL
            ORDER BY qq.orderIndex ASC, q.createdAt ASC
            """)
    List<QuizQuestion> findActiveByQuizId(@Param("quizId") UUID quizId);

    boolean existsByQuiz_QuizIdAndQuestion_QuestionId(UUID quizId, UUID questionId);

    /** Ids among {@code questionIds} linked to at least one quiz that is not soft-deleted. */
    @Query("""
            SELECT DISTINCT qq.question.questionId FROM QuizQuestion qq
            WHERE qq.question.questionId IN :questionIds
              AND qq.quiz.deletedAt IS NULL
            """)
    Set<UUID> findActivelyLinkedQuestionIds(@Param("questionIds") Collection<UUID> questionIds);

    @Query("SELECT COALESCE(MAX(qq.orderIndex), -1) FROM QuizQuestion qq WHERE qq.quiz.quizId = :quizId")
    Integer findMaxOrderIndex(@Param("quizId") UUID quizId);
}
