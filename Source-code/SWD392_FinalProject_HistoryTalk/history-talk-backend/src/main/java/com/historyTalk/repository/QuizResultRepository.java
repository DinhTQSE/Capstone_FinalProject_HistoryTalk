package com.historyTalk.repository;

import com.historyTalk.entity.quiz.QuizResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QuizResultRepository extends JpaRepository<QuizResult, UUID> {

    @Query("SELECT r FROM QuizResult r WHERE r.user.uid = :uid AND (:includeDeleted = true OR r.deletedAt IS NULL) ORDER BY r.takenDate DESC")
    Page<QuizResult> findByUserUid(@Param("uid") UUID uid,
                                    @Param("includeDeleted") boolean includeDeleted,
                                    Pageable pageable);

}
