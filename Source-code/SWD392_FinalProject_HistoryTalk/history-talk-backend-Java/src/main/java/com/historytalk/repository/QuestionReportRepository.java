package com.historytalk.repository;

import com.historytalk.entity.quiz.QuestionReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface QuestionReportRepository extends JpaRepository<QuestionReport, UUID> {

    @Query("""
            SELECT r FROM QuestionReport r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<QuestionReport> findAllByStatus(@Param("status") String status, Pageable pageable);
}
