package com.historytalk.repository;

import com.historytalk.entity.quiz.QuizRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizRatingRepository extends JpaRepository<QuizRating, UUID> {

    Optional<QuizRating> findByQuizQuizIdAndUserUid(UUID quizId, UUID userId);

    @Query("SELECT AVG(r.ratingValue) FROM QuizRating r WHERE r.quiz.quizId = :quizId")
    Double getAverageRatingByQuizId(@Param("quizId") UUID quizId);

    @Query("SELECT COUNT(r) FROM QuizRating r WHERE r.quiz.quizId = :quizId")
    long countByQuizId(@Param("quizId") UUID quizId);
}
