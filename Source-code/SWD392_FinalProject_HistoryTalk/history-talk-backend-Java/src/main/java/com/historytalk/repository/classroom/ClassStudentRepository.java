package com.historytalk.repository.classroom;

import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {

    boolean existsByClassroomIdAndStudentUid(UUID classroomId, UUID studentUid);

    long countByClassroomIdAndStatus(UUID classroomId, String status);

    long countByClassroomId(UUID classroomId);

    List<ClassStudent> findByStudentUidAndStatus(UUID studentUid, String status);

    @Query("SELECT cs.classroom FROM ClassStudent cs WHERE cs.student.uid = :studentUid " +
            "AND cs.classroom.deletedAt IS NULL AND cs.status = 'ACTIVE'")
    List<Classroom> findClassroomsByStudentUid(@Param("studentUid") UUID studentUid);
}
