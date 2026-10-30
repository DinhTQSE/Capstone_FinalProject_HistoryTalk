package com.historytalk.repository;

import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.EventEra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    @Query("""
            SELECT DISTINCT c FROM Character c
            JOIN c.historicalContexts hc
            LEFT JOIN FETCH c.createdBy
            LEFT JOIN FETCH c.school
            WHERE hc.contextId = :contextId
              AND (:includeDraft = true OR c.isPublished = true)
              AND (:includeDeleted = true OR c.deletedAt IS NULL)
            ORDER BY c.name ASC
           """)
    List<Character> findByContextIdOrderByNameAsc(@Param("contextId") UUID contextId,
                                                 @Param("includeDraft") boolean includeDraft,
                                                 @Param("includeDeleted") boolean includeDeleted);

    List<Character> findByCreatedByUidOrderByNameAsc(UUID uid);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndCharacterIdNot(String name, UUID characterId);

    @Query(value = """
            SELECT DISTINCT c FROM Character c
              LEFT JOIN c.historicalContexts hc
            JOIN FETCH c.createdBy u
           WHERE c.school IS NULL
             AND (:search IS NULL OR :search = ''
                 OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                 OR LOWER(c.background) LIKE LOWER(CONCAT('%', :search, '%')))
           AND (:era IS NULL OR hc.era = :era)
           AND (:includeDraft = true OR c.isPublished = true)
           AND (:includeDeleted = true OR c.deletedAt IS NULL)
            ORDER BY c.name ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT c) FROM Character c
            LEFT JOIN c.historicalContexts hc
            WHERE c.school IS NULL
              AND (:search IS NULL OR :search = ''
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.background) LIKE LOWER(CONCAT('%', :search, '%')))
          AND (:era IS NULL OR hc.era = :era)
          AND (:includeDraft = true OR c.isPublished = true)
          AND (:includeDeleted = true OR c.deletedAt IS NULL)
            """)
    Page<Character> findAllWithFilter(@Param("search") String search,
                                      @Param("era") EventEra era,
                                      @Param("includeDraft") boolean includeDraft,
                                      @Param("includeDeleted") boolean includeDeleted,
                                      Pageable pageable);

    @Query("""
           SELECT c FROM Character c
           LEFT JOIN FETCH c.createdBy
           WHERE c.school IS NULL AND c.deletedAt IS NOT NULL
           ORDER BY c.name ASC
           """)
    List<Character> findAllDeleted();

    @Modifying
    @Query("""
           UPDATE Character c
           SET c.deletedAt = NULL
           WHERE c.characterId = :characterId
             AND c.deletedAt IS NOT NULL
           """)
    int restoreById(@Param("characterId") UUID characterId);

    @Query("SELECT COUNT(c) FROM Character c WHERE c.school IS NULL")
    long countCurrent();

    @Query("SELECT COUNT(c) FROM Character c WHERE c.school IS NULL AND c.isPublished = true")
    long countPublished();

    @Query("SELECT COUNT(c) FROM Character c WHERE c.school IS NULL AND c.deletedAt IS NULL AND c.isPublished = true")
    long countActive();

    @Query(value = """
            SELECT c, hc FROM Character c
            JOIN c.historicalContexts hc
            WHERE c.deletedAt IS NULL AND hc.deletedAt IS NULL
              AND c.isPublished = true AND hc.isPublished = true
              AND (:search IS NULL OR :search = ''
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(hc.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:era IS NULL OR hc.era = :era)
              AND (
                  (:schoolId IS NULL AND c.school IS NULL AND hc.school IS NULL)
                  OR (:schoolId IS NOT NULL
                      AND (c.school IS NULL
                           OR (c.school.id = :schoolId AND (c.contentScope IS NULL OR c.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE)))
                      AND (hc.school IS NULL
                           OR (hc.school.id = :schoolId AND (hc.contentScope IS NULL OR hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE))))
              )
            ORDER BY hc.createdAt DESC, c.name ASC
            """,
            countQuery = """
            SELECT COUNT(c) FROM Character c
            JOIN c.historicalContexts hc
            WHERE c.deletedAt IS NULL AND hc.deletedAt IS NULL
              AND c.isPublished = true AND hc.isPublished = true
              AND (:search IS NULL OR :search = ''
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(hc.name) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:era IS NULL OR hc.era = :era)
              AND (
                  (:schoolId IS NULL AND c.school IS NULL AND hc.school IS NULL)
                  OR (:schoolId IS NOT NULL
                      AND (c.school IS NULL
                           OR (c.school.id = :schoolId AND (c.contentScope IS NULL OR c.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE)))
                      AND (hc.school IS NULL
                           OR (hc.school.id = :schoolId AND (hc.contentScope IS NULL OR hc.contentScope = com.historytalk.entity.enums.ContentScope.SCHOOL_WIDE))))
              )
            """)
    Page<Object[]> findScenarios(@Param("search") String search,
                                 @Param("era") EventEra era,
                                 @Param("schoolId") UUID schoolId,
                                 Pageable pageable);
}
