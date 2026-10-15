package com.historytalk.repository.assignment;

import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.enums.SubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentUid(UUID assignmentId, UUID studentUid);

    List<AssignmentSubmission> findByAssignmentId(UUID assignmentId);

    List<AssignmentSubmission> findByStudentUid(UUID studentUid);

    List<AssignmentSubmission> findByAssignmentIdInAndStudentUid(Collection<UUID> assignmentIds, UUID studentUid);

    long countByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);

    long countByAssignmentId(UUID assignmentId);

    List<AssignmentSubmission> findByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);

    @org.springframework.data.jpa.repository.Query(
            value = "SELECT s FROM AssignmentSubmission s " +
                    "JOIN FETCH s.assignment a " +
                    "JOIN FETCH a.classroom c " +
                    "WHERE s.student.uid = :studentUid " +
                    "AND a.status = 'ACTIVE' " +
                    "AND a.deletedAt IS NULL " +
                    "AND (:classroomId IS NULL OR c.id = :classroomId) " +
                    "AND (:status IS NULL OR s.status = :status)",
            countQuery = "SELECT COUNT(s) FROM AssignmentSubmission s " +
                         "JOIN s.assignment a " +
                         "WHERE s.student.uid = :studentUid " +
                         "AND a.status = 'ACTIVE' " +
                         "AND a.deletedAt IS NULL " +
                         "AND (:classroomId IS NULL OR a.classroom.id = :classroomId) " +
                         "AND (:status IS NULL OR s.status = :status)"
    )
    org.springframework.data.domain.Page<AssignmentSubmission> findStudentSubmissions(
            @org.springframework.data.repository.query.Param("studentUid") UUID studentUid,
            @org.springframework.data.repository.query.Param("classroomId") UUID classroomId,
            @org.springframework.data.repository.query.Param("status") SubmissionStatus status,
            org.springframework.data.domain.Pageable pageable);
}

