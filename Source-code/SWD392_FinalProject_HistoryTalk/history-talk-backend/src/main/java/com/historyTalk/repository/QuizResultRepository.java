package com.historyTalk.repository;

import com.historyTalk.entity.quiz.QuizResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QuizResultRepository extends JpaRepository<QuizResult, UUID> {

    Page<QuizResult> findByUserUid(UUID uid, Pageable pageable);

}
