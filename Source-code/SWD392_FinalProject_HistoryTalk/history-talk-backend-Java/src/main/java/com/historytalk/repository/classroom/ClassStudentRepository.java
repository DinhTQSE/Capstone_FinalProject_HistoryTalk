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
import com.historytalk.repository.dashboard.StudentTokenUsageDetailProjection;
import java.time.LocalDateTime;

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

    @Query(value = """
            SELECT u.uid AS "studentId",
                   u.student_code AS "studentCode",
                   u.full_name AS "fullName",
                   u.user_name AS "userName",
                   u.email AS "email",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedTotal",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL AND m.created_at >= :todayStart THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedToday",
                   COALESCE(u.daily_tokens_used, 0) AS "dailyTokensUsed",
                   COALESCE(u.daily_bonus_tokens, 0) AS "dailyBonusTokens",
                   COUNT(DISTINCT m.message_id) AS "messageCount",
                   MAX(m.created_at) AS "lastActiveAt"
            FROM class_student cs
            JOIN "user" u ON u.uid = cs.student_id
            LEFT JOIN chat_session sess ON sess.uid = u.uid AND sess.deleted_at IS NULL
            LEFT JOIN message m ON m.session_id = sess.session_id
            WHERE cs.classroom_id = :classroomId
              AND cs.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND (CAST(:search AS text) IS NULL OR :search = ''
                   OR u.full_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR (u.student_code IS NOT NULL AND u.student_code ILIKE CONCAT('%', CAST(:search AS text), '%'))
                   OR u.user_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR u.email ILIKE CONCAT('%', CAST(:search AS text), '%'))
            GROUP BY u.uid, u.student_code, u.full_name, u.user_name, u.email, u.daily_tokens_used, u.daily_bonus_tokens
            ORDER BY "tokensUsedTotal" DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u.uid)
            FROM class_student cs
            JOIN "user" u ON u.uid = cs.student_id
            WHERE cs.classroom_id = :classroomId
              AND cs.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND (CAST(:search AS text) IS NULL OR :search = ''
                   OR u.full_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR (u.student_code IS NOT NULL AND u.student_code ILIKE CONCAT('%', CAST(:search AS text), '%'))
                   OR u.user_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR u.email ILIKE CONCAT('%', CAST(:search AS text), '%'))
            """,
            nativeQuery = true)
    org.springframework.data.domain.Page<StudentTokenUsageDetailProjection> findStudentTokenUsageInClassroom(
            @Param("classroomId") UUID classroomId,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);
}

