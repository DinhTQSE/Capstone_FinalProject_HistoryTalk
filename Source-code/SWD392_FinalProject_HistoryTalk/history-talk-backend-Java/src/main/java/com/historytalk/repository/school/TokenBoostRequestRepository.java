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

    boolean existsByStudent_UidAndRequestDateAndStatusIn(
            UUID studentUid, LocalDate requestDate, java.util.Collection<TokenBoostRequestStatus> statuses);

    /** Row lock so two reviewers (or a double click) cannot process the same ticket twice. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM TokenBoostRequest r WHERE r.id = :id")
    java.util.Optional<TokenBoostRequest> findByIdForUpdate(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"classroom", "student", "requester", "teacherReviewedBy", "adminReviewedBy", "reviewedBy"})
    List<TokenBoostRequest> findByStudent_UidOrderByCreatedAtDesc(UUID studentUid);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"classroom", "student", "requester", "teacherReviewedBy", "adminReviewedBy", "reviewedBy"})
    @Query("SELECT r FROM TokenBoostRequest r " +
            "LEFT JOIN r.classroom c " +
            "LEFT JOIN c.teacher t " +
            "WHERE (t.uid = :teacherUid OR r.requester.uid = :teacherUid) " +
            "AND (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.createdAt DESC")
    Page<TokenBoostRequest> findByTeacherUidAndStatus(
            @Param("teacherUid") UUID teacherUid,
            @Param("status") TokenBoostRequestStatus status,
            Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"classroom", "student", "requester", "teacherReviewedBy", "adminReviewedBy", "reviewedBy"})
    @Query("SELECT r FROM TokenBoostRequest r " +
            "WHERE r.school.id = :schoolId " +
            "AND (:status IS NULL OR r.status = :status) " +
            "ORDER BY r.createdAt DESC")
    Page<TokenBoostRequest> findBySchoolIdAndStatus(
            @Param("schoolId") UUID schoolId,
            @Param("status") TokenBoostRequestStatus status,
            Pageable pageable);

    long countByClassroom_IdAndStatus(UUID classroomId, TokenBoostRequestStatus status);

    long countBySchool_IdAndStatus(UUID schoolId, TokenBoostRequestStatus status);
}
