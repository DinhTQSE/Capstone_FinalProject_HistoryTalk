package com.historytalk.repository.classroom;

import com.historytalk.entity.classroom.Classroom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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

    // Lọc theo Năm học (academicYear)
    Page<Classroom> findBySchoolIdAndAcademicYearAndDeletedAtIsNull(
            UUID schoolId, Integer academicYear, Pageable pageable);

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
}
