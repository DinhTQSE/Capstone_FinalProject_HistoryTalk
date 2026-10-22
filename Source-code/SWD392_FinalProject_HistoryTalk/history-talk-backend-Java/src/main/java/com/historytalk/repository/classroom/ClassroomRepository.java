package com.historytalk.repository.classroom;

import com.historytalk.entity.classroom.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.historytalk.repository.dashboard.ClassroomTokenUsageProjection;
import java.time.LocalDateTime;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    Optional<Classroom> findByIdAndSchoolIdAndDeletedAtIsNull(UUID id, UUID schoolId);

    Optional<Classroom> findByClassCodeIgnoreCaseAndDeletedAtIsNull(String classCode);

    boolean existsByClassCodeIgnoreCase(String classCode);

    boolean existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, String className, Integer academicYear);

    boolean existsBySchoolIdAndClassNameIgnoreCaseAndAcademicYearAndIdNotAndDeletedAtIsNull(
            UUID schoolId, String className, Integer academicYear, UUID id);

    // Tất cả lớp của trường
    Page<Classroom> findBySchoolIdAndDeletedAtIsNull(UUID schoolId, Pageable pageable);

    // Lọc theo Khối lớp (gradeLevel)
    Page<Classroom> findBySchoolIdAndGradeLevelAndDeletedAtIsNull(
            UUID schoolId, Integer gradeLevel, Pageable pageable);

    List<Classroom> findBySchoolIdAndGradeLevelAndDeletedAtIsNull(UUID schoolId, Integer gradeLevel);

    // Lọc theo Năm học (academicYear)
    Page<Classroom> findBySchoolIdAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, Integer academicYear, Pageable pageable);

    List<Classroom> findBySchoolIdAndAcademicYearAndDeletedAtIsNull(UUID schoolId, Integer academicYear);

    List<Classroom> findBySchoolIdAndStatusAndDeletedAtIsNull(UUID schoolId, String status);

    // Lọc theo cả Khối lớp và Năm học
    Page<Classroom> findBySchoolIdAndGradeLevelAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, Integer gradeLevel, Integer academicYear, Pageable pageable);

    // Tìm kiếm theo tên lớp hoặc mã lớp trong trường
    Page<Classroom> findBySchoolIdAndDeletedAtIsNullAndClassNameContainingIgnoreCaseOrSchoolIdAndDeletedAtIsNullAndClassCodeContainingIgnoreCase(
            UUID schoolId1, String className, UUID schoolId2, String classCode, Pageable pageable);

    // Giáo viên: tất cả lớp phụ trách
    Page<Classroom> findBySchoolIdAndTeacher_UidAndDeletedAtIsNull(
            UUID schoolId, UUID teacherUid, Pageable pageable);

    // Giáo viên: lọc theo năm học
    Page<Classroom> findBySchoolIdAndTeacher_UidAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, UUID teacherUid, Integer academicYear, Pageable pageable);

    long countBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    @Query(value = """
            SELECT c.id AS "classroomId",
                   c.class_name AS "className",
                   c.class_code AS "classCode",
                   c.grade_level AS "gradeLevel",
                   c.academic_year AS "academicYear",
                   c.status AS "status",
                   t.full_name AS "teacherName",
                   t.uid AS "teacherUid",
                   COUNT(DISTINCT cs.student_id) AS "studentCount",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedTotal",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL AND m.created_at >= :todayStart THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedToday"
            FROM classroom c
            LEFT JOIN "user" t ON t.uid = c.teacher_id
            LEFT JOIN class_student cs ON cs.classroom_id = c.id AND cs.status = 'ACTIVE'
            LEFT JOIN chat_session sess ON sess.uid = cs.student_id AND sess.deleted_at IS NULL
            LEFT JOIN message m ON m.session_id = sess.session_id
            WHERE c.school_id = :schoolId
              AND c.deleted_at IS NULL
              AND (:academicYear IS NULL OR c.academic_year = :academicYear)
              AND (:gradeLevel IS NULL OR c.grade_level = :gradeLevel)
              AND (:status IS NULL OR c.status = :status)
              AND (CAST(:search AS text) IS NULL OR :search = ''
                   OR c.class_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR c.class_code ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR (t.full_name IS NOT NULL AND t.full_name ILIKE CONCAT('%', CAST(:search AS text), '%')))
            GROUP BY c.id, c.class_name, c.class_code, c.grade_level, c.academic_year, c.status, t.full_name, t.uid
            ORDER BY "tokensUsedTotal" DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT c.id)
            FROM classroom c
            LEFT JOIN "user" t ON t.uid = c.teacher_id
            WHERE c.school_id = :schoolId
              AND c.deleted_at IS NULL
              AND (:academicYear IS NULL OR c.academic_year = :academicYear)
              AND (:gradeLevel IS NULL OR c.grade_level = :gradeLevel)
              AND (:status IS NULL OR c.status = :status)
              AND (CAST(:search AS text) IS NULL OR :search = ''
                   OR c.class_name ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR c.class_code ILIKE CONCAT('%', CAST(:search AS text), '%')
                   OR (t.full_name IS NOT NULL AND t.full_name ILIKE CONCAT('%', CAST(:search AS text), '%')))
            """,
            nativeQuery = true)
    Page<ClassroomTokenUsageProjection> findClassroomTokenUsageList(
            @Param("schoolId") UUID schoolId,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("academicYear") Integer academicYear,
            @Param("gradeLevel") Integer gradeLevel,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    @Query(value = """
            SELECT c.id AS "classroomId",
                   c.class_name AS "className",
                   c.class_code AS "classCode",
                   c.grade_level AS "gradeLevel",
                   c.academic_year AS "academicYear",
                   c.status AS "status",
                   t.full_name AS "teacherName",
                   t.uid AS "teacherUid",
                   COUNT(DISTINCT cs.student_id) AS "studentCount",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedTotal",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL AND m.created_at >= :todayStart THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedToday"
            FROM classroom c
            LEFT JOIN "user" t ON t.uid = c.teacher_id
            LEFT JOIN class_student cs ON cs.classroom_id = c.id AND cs.status = 'ACTIVE'
            LEFT JOIN chat_session sess ON sess.uid = cs.student_id AND sess.deleted_at IS NULL
            LEFT JOIN message m ON m.session_id = sess.session_id
            WHERE c.school_id = :schoolId
              AND c.deleted_at IS NULL
              AND c.status = 'ACTIVE'
            GROUP BY c.id, c.class_name, c.class_code, c.grade_level, c.academic_year, c.status, t.full_name, t.uid
            ORDER BY "tokensUsedTotal" DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ClassroomTokenUsageProjection> findTopClassroomsByTokenUsage(
            @Param("schoolId") UUID schoolId,
            @Param("todayStart") LocalDateTime todayStart,
            @Param("limit") int limit);

    @Query(value = """
            SELECT c.id AS "classroomId",
                   c.class_name AS "className",
                   c.class_code AS "classCode",
                   c.grade_level AS "gradeLevel",
                   c.academic_year AS "academicYear",
                   c.status AS "status",
                   t.full_name AS "teacherName",
                   t.uid AS "teacherUid",
                   COUNT(DISTINCT cs.student_id) AS "studentCount",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedTotal",
                   COALESCE(SUM(CASE WHEN m.deleted_at IS NULL AND m.created_at >= :todayStart THEN COALESCE(m.token, 0) ELSE 0 END), 0) AS "tokensUsedToday"
            FROM classroom c
            LEFT JOIN "user" t ON t.uid = c.teacher_id
            LEFT JOIN class_student cs ON cs.classroom_id = c.id AND cs.status = 'ACTIVE'
            LEFT JOIN chat_session sess ON sess.uid = cs.student_id AND sess.deleted_at IS NULL
            LEFT JOIN message m ON m.session_id = sess.session_id
            WHERE c.school_id = :schoolId
              AND c.teacher_id = :teacherUid
              AND c.deleted_at IS NULL
            GROUP BY c.id, c.class_name, c.class_code, c.grade_level, c.academic_year, c.status, t.full_name, t.uid
            ORDER BY "tokensUsedTotal" DESC
            """, nativeQuery = true)
    List<ClassroomTokenUsageProjection> findClassroomsTokenUsageForTeacher(
            @Param("schoolId") UUID schoolId,
            @Param("teacherUid") UUID teacherUid,
            @Param("todayStart") LocalDateTime todayStart);
}

