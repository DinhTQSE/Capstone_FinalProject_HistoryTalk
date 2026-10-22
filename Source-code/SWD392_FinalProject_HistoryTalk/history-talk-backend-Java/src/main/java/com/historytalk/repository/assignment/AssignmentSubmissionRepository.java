package com.historytalk.repository.assignment;

import com.historytalk.entity.assignment.AssignmentSubmission;
import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.repository.dashboard.AssignmentSubmissionStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(
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
    Page<AssignmentSubmission> findStudentSubmissions(
            @Param("studentUid") UUID studentUid,
            @Param("classroomId") UUID classroomId,
            @Param("status") SubmissionStatus status,
            Pageable pageable);

    @Query(value = """
            SELECT
                COUNT(s.id) AS "totalSubmissions",
                COUNT(CASE WHEN s.status = 'SUBMITTED' THEN 1 END) AS "submittedCount",
                COUNT(CASE WHEN s.status = 'LATE_SUBMITTED' THEN 1 END) AS "lateSubmittedCount",
                COUNT(CASE WHEN s.status = 'NOT_STARTED' THEN 1 END) AS "notStartedCount",
                COUNT(CASE WHEN s.status = 'IN_PROGRESS' THEN 1 END) AS "inProgressCount",
                COUNT(CASE WHEN s.status = 'MISSED' THEN 1 END) AS "missedCount"
            FROM assignment_submission s
            JOIN assignment a ON a.id = s.assignment_id
            JOIN classroom c ON c.id = a.classroom_id
            WHERE c.school_id = :schoolId
              AND a.deleted_at IS NULL
              AND c.deleted_at IS NULL
            """, nativeQuery = true)
    AssignmentSubmissionStatsProjection getSubmissionStatsBySchoolId(@Param("schoolId") UUID schoolId);

    @Query(value = """
            SELECT
                COUNT(s.id) AS "totalSubmissions",
                COUNT(CASE WHEN s.status = 'SUBMITTED' THEN 1 END) AS "submittedCount",
                COUNT(CASE WHEN s.status = 'LATE_SUBMITTED' THEN 1 END) AS "lateSubmittedCount",
                COUNT(CASE WHEN s.status = 'NOT_STARTED' THEN 1 END) AS "notStartedCount",
                COUNT(CASE WHEN s.status = 'IN_PROGRESS' THEN 1 END) AS "inProgressCount",
                COUNT(CASE WHEN s.status = 'MISSED' THEN 1 END) AS "missedCount"
            FROM assignment_submission s
            JOIN assignment a ON a.id = s.assignment_id
            WHERE a.classroom_id = :classroomId
              AND a.deleted_at IS NULL
            """, nativeQuery = true)
    AssignmentSubmissionStatsProjection getSubmissionStatsByClassroomId(@Param("classroomId") UUID classroomId);
}
