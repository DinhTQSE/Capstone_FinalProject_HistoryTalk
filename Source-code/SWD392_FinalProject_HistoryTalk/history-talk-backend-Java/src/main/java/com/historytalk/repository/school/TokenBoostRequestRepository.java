package com.historytalk.repository.school;

import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.school.TokenBoostRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TokenBoostRequestRepository extends JpaRepository<TokenBoostRequest, UUID> {

    boolean existsByStudent_UidAndRequestDateAndStatus(
            UUID studentUid, LocalDate requestDate, TokenBoostRequestStatus status);

    List<TokenBoostRequest> findByStudent_UidOrderByCreatedAtDesc(UUID studentUid);

    @Query("SELECT r FROM TokenBoostRequest r " +
            "WHERE r.classroom.teacher.uid = :teacherUid " +
            "AND (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.createdAt DESC")
    Page<TokenBoostRequest> findByTeacherUidAndStatus(
            @Param("teacherUid") UUID teacherUid,
            @Param("status") TokenBoostRequestStatus status,
            Pageable pageable);

    @Query("SELECT r FROM TokenBoostRequest r " +
            "WHERE r.school.id = :schoolId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.createdAt DESC")
    Page<TokenBoostRequest> findBySchoolIdAndStatus(
            @Param("schoolId") UUID schoolId,
            @Param("status") TokenBoostRequestStatus status,
            Pageable pageable);
}
