package com.historytalk.repository;

import com.historytalk.entity.enums.ContentScope;
import com.historytalk.entity.historicalContext.HistoricalContext;
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
 * Queries scoped to local (school-owned) historical contexts.
 * All methods filter on school_id IS NOT NULL to avoid touching global content.
 */
@Repository
public interface LocalContextRepository extends JpaRepository<HistoricalContext, UUID> {

    // ── Teacher queries ───────────────────────────────────────────────────────

    /**
     * All local contexts visible to a teacher:
     *   - contexts they own (CLASS_ONLY or SCHOOL_WIDE), OR
     *   - SCHOOL_WIDE contexts in the same school (from any teacher) regardless of status.
     */
    @Query(value = """
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NULL
              AND (
                hc.createdBy.uid = :teacherId
                OR hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
              )
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%')
                   OR hc.description ILIKE CONCAT('%', :search, '%'))
            ORDER BY hc.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(hc) FROM HistoricalContext hc
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NULL
              AND (
                hc.createdBy.uid = :teacherId
                OR hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
              )
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%')
                   OR hc.description ILIKE CONCAT('%', :search, '%'))
            """)
    Page<HistoricalContext> findVisibleToTeacher(
            @Param("schoolId") UUID schoolId,
            @Param("teacherId") UUID teacherId,
            @Param("search") String search,
            Pageable pageable);

    // ── School Admin queries ───────────────────────────────────────────────────

    /**
     * All local contexts in the school (for School Admin review panel).
     */
    @Query(value = """
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NULL
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%'))
              AND (:scope IS NULL OR hc.contentScope = :scope)
            ORDER BY hc.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(hc) FROM HistoricalContext hc
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NULL
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%'))
              AND (:scope IS NULL OR hc.contentScope = :scope)
            """)
    Page<HistoricalContext> findAllBySchool(
            @Param("schoolId") UUID schoolId,
            @Param("search") String search,
            @Param("scope") ContentScope scope,
            Pageable pageable);

    // ── Student query ─────────────────────────────────────────────────────────

    /**
     * Published contexts visible to a student in a school:
     *   - SCHOOL_WIDE published contexts, OR
     *   - CLASS_ONLY published contexts assigned to one of the student's enrolled classrooms (or given classroom).
     */
    @Query(value = """
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.isPublished = true
              AND hc.contentStatus = com.historytalk.entity.enums.ContentStatus.ACTIVE
              AND hc.deletedAt IS NULL
              AND (
                hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
                OR (
                    hc.contentScope = com.historytalk.entity.enums.ContentScope.CLASS_ONLY
                    AND (:hasClasses = true AND hc.contextId IN (
                        SELECT clc.entityId FROM ClassLocalContent clc
                        WHERE clc.entityType = com.historytalk.entity.enums.EntityType.CONTEXT
                          AND clc.classId IN :classIds
                    ))
                )
              )
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%')
                   OR hc.description ILIKE CONCAT('%', :search, '%'))
            ORDER BY hc.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(hc) FROM HistoricalContext hc
            WHERE hc.school.id = :schoolId
              AND hc.isPublished = true
              AND hc.contentStatus = com.historytalk.entity.enums.ContentStatus.ACTIVE
              AND hc.deletedAt IS NULL
              AND (
                hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE
                OR (
                    hc.contentScope = com.historytalk.entity.enums.ContentScope.CLASS_ONLY
                    AND (:hasClasses = true AND hc.contextId IN (
                        SELECT clc.entityId FROM ClassLocalContent clc
                        WHERE clc.entityType = com.historytalk.entity.enums.EntityType.CONTEXT
                          AND clc.classId IN :classIds
                    ))
                )
              )
              AND (:search IS NULL OR :search = ''
                   OR hc.name ILIKE CONCAT('%', :search, '%')
                   OR hc.description ILIKE CONCAT('%', :search, '%'))
            """)
    Page<HistoricalContext> findVisibleToStudent(
            @Param("schoolId") UUID schoolId,
            @Param("hasClasses") boolean hasClasses,
            @Param("classIds") List<UUID> classIds,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.isPublished = true
              AND hc.deletedAt IS NULL
            ORDER BY hc.createdAt DESC
            """)
    List<HistoricalContext> findPublishedBySchool(@Param("schoolId") UUID schoolId);

    // ── Soft-delete helper ────────────────────────────────────────────────────

    @Query("""
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.contextId = :id
              AND hc.school.id = :schoolId
              AND hc.deletedAt IS NULL
            """)
    Optional<HistoricalContext> findByIdAndSchool(
            @Param("id") UUID id,
            @Param("schoolId") UUID schoolId);

    // ── Count for dashboard ───────────────────────────────────────────────────

    @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.school.id = :schoolId AND hc.deletedAt IS NULL")
    long countBySchool(@Param("schoolId") UUID schoolId);

    @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.school.id = :schoolId AND hc.isPublished = true AND hc.deletedAt IS NULL")
    long countPublishedBySchool(@Param("schoolId") UUID schoolId);

    // ── Trash & Restore queries ───────────────────────────────────────────────

    @Query(value = """
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NOT NULL
            ORDER BY hc.deletedAt DESC
            """,
            countQuery = """
            SELECT COUNT(hc) FROM HistoricalContext hc
            WHERE hc.school.id = :schoolId
              AND hc.deletedAt IS NOT NULL
            """)
    Page<HistoricalContext> findTrashBySchool(@Param("schoolId") UUID schoolId, Pageable pageable);

    @Query(value = """
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.school.id = :schoolId
              AND hc.createdBy.uid = :creatorId
              AND hc.deletedAt IS NOT NULL
            ORDER BY hc.deletedAt DESC
            """,
            countQuery = """
            SELECT COUNT(hc) FROM HistoricalContext hc
            WHERE hc.school.id = :schoolId
              AND hc.createdBy.uid = :creatorId
              AND hc.deletedAt IS NOT NULL
            """)
    Page<HistoricalContext> findTrashBySchoolAndCreator(@Param("schoolId") UUID schoolId,
                                                        @Param("creatorId") UUID creatorId,
                                                        Pageable pageable);

    @Query("""
            SELECT hc FROM HistoricalContext hc
            LEFT JOIN FETCH hc.createdBy
            LEFT JOIN FETCH hc.approvedBy
            LEFT JOIN FETCH hc.school
            WHERE hc.contextId = :id
              AND hc.school.id = :schoolId
              AND hc.deletedAt IS NOT NULL
            """)
    Optional<HistoricalContext> findTrashedByIdAndSchool(@Param("id") UUID id, @Param("schoolId") UUID schoolId);
}
