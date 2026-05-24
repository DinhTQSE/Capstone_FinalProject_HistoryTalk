package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
