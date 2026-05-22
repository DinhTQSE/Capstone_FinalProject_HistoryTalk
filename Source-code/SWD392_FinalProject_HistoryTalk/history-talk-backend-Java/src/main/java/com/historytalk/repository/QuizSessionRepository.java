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

    @Query("SELECT s FROM QuizSession s WHERE s.user.uid = :uid AND (:includeDeleted = true OR s.deletedAt IS NULL) ORDER BY s.createdDate DESC")
    Page<QuizSession> findByUserUid(
            @Param("uid") UUID uid,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable);

}
