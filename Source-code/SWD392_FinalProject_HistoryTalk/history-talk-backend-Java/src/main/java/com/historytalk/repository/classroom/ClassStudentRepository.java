package com.historytalk.repository.classroom;

import com.historytalk.entity.classroom.ClassStudent;
import com.historytalk.entity.classroom.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {

    boolean existsByClassroomIdAndStudentUid(UUID classroomId, UUID studentUid);

    long countByClassroomIdAndStatus(UUID classroomId, String status);

    long countByClassroomId(UUID classroomId);

    List<ClassStudent> findByStudentUidAndStatus(UUID studentUid, String status);

    Optional<ClassStudent> findByClassroomIdAndStudentUid(UUID classroomId, UUID studentUid);

    void deleteByClassroomIdAndStudentUid(UUID classroomId, UUID studentUid);

    @Query("SELECT cs.student.uid FROM ClassStudent cs WHERE cs.classroom.id = :classroomId AND cs.status = 'ACTIVE'")
    List<UUID> findEnrolledStudentUids(@Param("classroomId") UUID classroomId);

    @Query("SELECT cs.classroom FROM ClassStudent cs WHERE cs.student.uid = :studentUid " +
            "AND cs.classroom.deletedAt IS NULL AND cs.status = 'ACTIVE'")
    List<Classroom> findClassroomsByStudentUid(@Param("studentUid") UUID studentUid);

    @Query("SELECT cs.classroom FROM ClassStudent cs WHERE cs.student.uid = :studentUid " +
            "AND cs.classroom.deletedAt IS NULL AND cs.classroom.status = 'ACTIVE' AND cs.status = 'ACTIVE'")
    List<Classroom> findActiveClassroomsByStudentUid(@Param("studentUid") UUID studentUid);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.classroom.id = :classroomId " +
            "AND cs.classroom.school.id = :schoolId " +
            "AND cs.classroom.deletedAt IS NULL AND cs.status = 'ACTIVE'")
    org.springframework.data.domain.Page<ClassStudent> findStudentsInClassroom(
            @Param("classroomId") UUID classroomId,
            @Param("schoolId") UUID schoolId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT cs FROM ClassStudent cs WHERE cs.classroom.id = :classroomId " +
            "AND cs.classroom.school.id = :schoolId " +
            "AND cs.classroom.deletedAt IS NULL AND cs.status = 'ACTIVE' " +
            "AND (CAST(:keyword AS string) IS NULL OR :keyword = '' " +
            "     OR cs.student.fullName ILIKE CONCAT('%', CAST(:keyword AS string), '%') " +
            "     OR (cs.student.studentCode IS NOT NULL AND cs.student.studentCode ILIKE CONCAT('%', CAST(:keyword AS string), '%')) " +
            "     OR cs.student.userName ILIKE CONCAT('%', CAST(:keyword AS string), '%') " +
            "     OR cs.student.email ILIKE CONCAT('%', CAST(:keyword AS string), '%'))")
    org.springframework.data.domain.Page<ClassStudent> searchStudentsInClassroom(
            @Param("classroomId") UUID classroomId,
            @Param("schoolId") UUID schoolId,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
}
