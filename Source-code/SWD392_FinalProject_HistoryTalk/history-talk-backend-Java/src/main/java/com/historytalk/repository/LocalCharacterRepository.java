package com.historytalk.repository;

import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.ContentScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Queries scoped to local (school-owned) characters.
 */
@Repository
public interface LocalCharacterRepository extends JpaRepository<Character, UUID> {

    /**
     * All local characters visible to a teacher:
     *   - characters they own, OR
     *   - SCHOOL_WIDE characters in the same school.
     */
    @Query("""
            SELECT c FROM Character c
            WHERE c.school.id = :schoolId
              AND c.deletedAt IS NULL
              AND (
                c.createdBy.uid = :teacherId
                OR c.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
              )
              AND (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%'))
            ORDER BY c.name ASC
            """)
    Page<Character> findVisibleToTeacher(
            @Param("schoolId") UUID schoolId,
            @Param("teacherId") UUID teacherId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * All local characters in the school for School Admin review.
     */
    @Query("""
            SELECT c FROM Character c
            WHERE c.school.id = :schoolId
              AND c.deletedAt IS NULL
              AND (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%'))
              AND (:scope IS NULL OR c.contentScope = :scope)
            ORDER BY c.name ASC
            """)
    Page<Character> findAllBySchool(
            @Param("schoolId") UUID schoolId,
            @Param("search") String search,
            @Param("scope") ContentScope scope,
            Pageable pageable);

    /**
     * Published characters visible to a student in a school:
     *   - SCHOOL_WIDE published characters, OR
     *   - CLASS_ONLY published characters assigned to one of the student's enrolled classrooms (or given classroom).
     */
    @Query("""
            SELECT c FROM Character c
            WHERE c.school.id = :schoolId
              AND c.isPublished = true
              AND c.contentStatus = com.historytalk.entity.enums.ContentStatus.ACTIVE
              AND c.deletedAt IS NULL
              AND (
                c.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
                OR (
                    c.contentScope = com.historytalk.entity.enums.ContentScope.CLASS_ONLY
                    AND (:hasClasses = true AND c.characterId IN (
                        SELECT clc.entityId FROM ClassLocalContent clc
                        WHERE clc.entityType = com.historytalk.entity.enums.EntityType.CHARACTER
                          AND clc.classId IN :classIds
                    ))
                )
              )
              AND (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%'))
            ORDER BY c.name ASC
            """)
    Page<Character> findVisibleToStudent(
            @Param("schoolId") UUID schoolId,
            @Param("hasClasses") boolean hasClasses,
            @Param("classIds") List<UUID> classIds,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Published SCHOOL_WIDE characters in a school (for student view via assignments).
     */
    @Query("""
            SELECT c FROM Character c
            WHERE c.school.id = :schoolId
              AND c.isPublished = true
              AND c.deletedAt IS NULL
            ORDER BY c.name ASC
            """)
    List<Character> findPublishedBySchool(@Param("schoolId") UUID schoolId);

    @Query("""
            SELECT c FROM Character c
            WHERE c.characterId = :id
              AND c.school.id = :schoolId
              AND c.deletedAt IS NULL
            """)
    Optional<Character> findByIdAndSchool(
            @Param("id") UUID id,
            @Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(c) FROM Character c WHERE c.school.id = :schoolId AND c.deletedAt IS NULL")
    long countBySchool(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(c) FROM Character c WHERE c.school.id = :schoolId AND c.isPublished = true AND c.deletedAt IS NULL")
    long countPublishedBySchool(@Param("schoolId") UUID schoolId);

    // ── Trash & Restore queries ───────────────────────────────────────────────

    @Query("""
            SELECT c FROM Character c
            WHERE c.school.id = :schoolId
              AND c.deletedAt IS NOT NULL
            ORDER BY c.deletedAt DESC
            """)
    Page<Character> findTrashBySchool(@Param("schoolId") UUID schoolId, Pageable pageable);

    @Query("""
            SELECT c FROM Character c
            WHERE c.characterId = :id
              AND c.school.id = :schoolId
              AND c.deletedAt IS NOT NULL
            """)
    Optional<Character> findTrashedByIdAndSchool(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
