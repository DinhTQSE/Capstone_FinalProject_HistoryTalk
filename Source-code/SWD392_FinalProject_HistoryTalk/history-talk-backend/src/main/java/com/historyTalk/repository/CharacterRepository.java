package com.historyTalk.repository;

import com.historyTalk.entity.character.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CharacterRepository extends JpaRepository<Character, UUID> {

    List<Character> findByHistoricalContextContextIdOrderByNameAsc(UUID contextId);

    List<Character> findByStaffStaffIdOrderByNameAsc(UUID staffId);

    @Query("""
            SELECT c FROM Character c
            WHERE (:search IS NULL OR :search = ''
                   OR c.name ILIKE CONCAT('%', :search, '%')
                   OR c.background ILIKE CONCAT('%', :search, '%')
                   OR c.personality ILIKE CONCAT('%', :search, '%'))
            ORDER BY c.name ASC
            """)
    List<Character> findAllWithSearch(@Param("search") String search);
}
