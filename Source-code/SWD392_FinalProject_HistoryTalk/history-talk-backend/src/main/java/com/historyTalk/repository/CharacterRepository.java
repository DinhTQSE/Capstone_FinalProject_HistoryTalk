package com.historyTalk.repository;

import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.enums.EventEra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
                                    WHERE hc.contextId = :contextId
                                           AND (:includeDraft = true OR c.isDraft = false)
                                           AND (:includeDeleted = true OR c.deletedAt IS NULL)
                                    ORDER BY c.name ASC
           """)
              List<Character> findByContextIdOrderByNameAsc(@Param("contextId") UUID contextId,
                                                                                                                                                                               @Param("includeDraft") boolean includeDraft,
                                                                                                                                                                               @Param("includeDeleted") boolean includeDeleted);

    List<Character> findByCreatedByUidOrderByNameAsc(UUID uid);

    @Query(value = """
            SELECT DISTINCT c FROM Character c
              LEFT JOIN c.historicalContexts hc
            JOIN FETCH c.createdBy u
           WHERE (:search IS NULL OR :search = ''
                 OR c.name ILIKE CONCAT('%', :search, '%')
                 OR c.background ILIKE CONCAT('%', :search, '%'))
           AND (:era IS NULL OR hc.era = :era)
           AND (:includeDraft = true OR c.isDraft = false)
           AND (:includeDeleted = true OR c.deletedAt IS NULL)
            ORDER BY c.name ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT c) FROM Character c
            LEFT JOIN c.historicalContexts hc
            WHERE (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%'))
          AND (:era IS NULL OR hc.era = :era)
          AND (:includeDraft = true OR c.isDraft = false)
          AND (:includeDeleted = true OR c.deletedAt IS NULL)
            """)
    Page<Character> findAllWithFilter(@Param("search") String search,
                                  @Param("era") EventEra era,
                                  @Param("includeDraft") boolean includeDraft,
                                  @Param("includeDeleted") boolean includeDeleted,
                                  Pageable pageable);

    @Query(value = """
           SELECT * FROM historical_schema."character" c
           WHERE c.deleted_at IS NOT NULL
           ORDER BY c.name ASC
           """, nativeQuery = true)
    List<Character> findAllDeleted();

    @Query(value = """
           UPDATE historical_schema."character"
           SET deleted_at = NULL
           WHERE character_id = :characterId
           """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int restoreById(@Param("characterId") UUID characterId);
}
