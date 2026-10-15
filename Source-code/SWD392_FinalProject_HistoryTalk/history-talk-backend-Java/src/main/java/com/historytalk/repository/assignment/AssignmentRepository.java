package com.historytalk.repository.assignment;

import com.historytalk.entity.assignment.Assignment;
import com.historytalk.entity.enums.AssignmentStatus;
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
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByIdAndDeletedAtIsNull(UUID id);

    Page<Assignment> findByClassroomIdAndDeletedAtIsNull(UUID classroomId, Pageable pageable);

    Page<Assignment> findByClassroomIdAndStatusAndDeletedAtIsNull(UUID classroomId, AssignmentStatus status, Pageable pageable);

    List<Assignment> findByClassroomIdInAndStatusAndDeletedAtIsNull(Collection<UUID> classroomIds, AssignmentStatus status);

    List<Assignment> findByClassroomIdInAndDeletedAtIsNull(Collection<UUID> classroomIds);

    Page<Assignment> findByTeacherUidAndDeletedAtIsNull(UUID teacherUid, Pageable pageable);

    @Query("SELECT a FROM Assignment a WHERE a.classroom.id = :classroomId AND a.deletedAt IS NULL AND " +
           "(:status IS NULL OR a.status = :status)")
    Page<Assignment> findAssignmentsByClassroomAndOptionalStatus(
            @Param("classroomId") UUID classroomId,
            @Param("status") AssignmentStatus status,
            Pageable pageable);
}
