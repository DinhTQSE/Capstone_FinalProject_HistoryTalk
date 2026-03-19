package com.historyTalk.repository;

import com.historyTalk.entity.quiz.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @Query("""
           SELECT q FROM Question q
           WHERE q.quiz.quizId = :quizId
           ORDER BY q.orderIndex ASC
           """)
    List<Question> findByQuizIdOrderByOrderIndex(@Param("quizId") UUID quizId);

}
