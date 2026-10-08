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

    @Query("SELECT c FROM Classroom c WHERE c.school.id = :schoolId AND c.deletedAt IS NULL " +
            "AND (:gradeLevel IS NULL OR c.gradeLevel = :gradeLevel) " +
            "AND (:academicYear IS NULL OR c.academicYear = :academicYear) " +
            "AND (:search IS NULL OR LOWER(c.className) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "     OR LOWER(c.classCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Classroom> findSchoolClassrooms(
            @Param("schoolId") UUID schoolId,
            @Param("search") String search,
            @Param("gradeLevel") Integer gradeLevel,
            @Param("academicYear") Integer academicYear,
            Pageable pageable);

    @Query("SELECT c FROM Classroom c WHERE c.school.id = :schoolId AND c.teacher.uid = :teacherUid AND c.deletedAt IS NULL " +
            "AND (:academicYear IS NULL OR c.academicYear = :academicYear)")
    Page<Classroom> findTeacherClassrooms(
            @Param("schoolId") UUID schoolId,
            @Param("teacherUid") UUID teacherUid,
            @Param("academicYear") Integer academicYear,
            Pageable pageable);
}
