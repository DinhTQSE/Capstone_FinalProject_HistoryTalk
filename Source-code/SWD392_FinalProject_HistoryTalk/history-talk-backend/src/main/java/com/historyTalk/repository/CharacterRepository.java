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
           ORDER BY c.name ASC
           """)
    List<Character> findByContextIdOrderByNameAsc(@Param("contextId") UUID contextId);

    List<Character> findByCreatedByUidOrderByNameAsc(UUID uid);

    @Query(value = """
            SELECT DISTINCT c FROM Character c
            JOIN c.historicalContexts hc
            JOIN FETCH c.createdBy u
            WHERE (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%'))
            AND (:era IS NULL OR hc.era = :era)
            ORDER BY c.name ASC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT c) FROM Character c
            JOIN c.historicalContexts hc
            WHERE (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%'))
            AND (:era IS NULL OR hc.era = :era)
            """)
    Page<Character> findAllWithFilter(@Param("search") String search,
                                      @Param("era") EventEra era,
                                      Pageable pageable);
}
