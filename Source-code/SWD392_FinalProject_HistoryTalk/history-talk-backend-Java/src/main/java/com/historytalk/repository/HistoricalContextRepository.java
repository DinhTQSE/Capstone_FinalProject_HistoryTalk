package com.historytalk.repository;

import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.historicalContext.HistoricalContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoricalContextRepository extends JpaRepository<HistoricalContext, UUID> {

       Optional<HistoricalContext> findByNameIgnoreCase(String name);

       boolean existsByNameIgnoreCaseAndContextIdNot(String name, UUID contextId);

       Page<HistoricalContext> findByCreatedByUid(UUID uid, Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE hc.school IS NULL
                       AND (:search IS NULL OR :search = ''
                            OR LOWER(hc.name) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(hc.description) LIKE LOWER(CONCAT('%', :search, '%')))
                     AND (:era IS NULL OR hc.era = :era)
                     AND (:category IS NULL OR hc.category = :category)
                     AND (:includeDraft = true OR hc.isPublished = true)
                     AND (:includeDeleted = true OR hc.deletedAt IS NULL)
                     """)
       Page<HistoricalContext> findAllWithSearch(
                     @Param("search") String search,
                     @Param("era") EventEra era,
                     @Param("category") EventCategory category,
                     @Param("includeDraft") boolean includeDraft,
                     @Param("includeDeleted") boolean includeDeleted,
                     Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE hc.school IS NULL
                       AND (:search IS NULL OR :search = ''
                            OR LOWER(hc.name) LIKE LOWER(CONCAT('%', :search, '%'))
                            OR LOWER(hc.description) LIKE LOWER(CONCAT('%', :search, '%')))
                     AND (:includeDraft = true OR hc.isPublished = true)
                     AND (:includeDeleted = true OR hc.deletedAt IS NULL)
                     ORDER BY hc.createdAt DESC
                     """)
       List<HistoricalContext> findAllSimple(@Param("search") String search,
                                             @Param("includeDraft") boolean includeDraft,
                                             @Param("includeDeleted") boolean includeDeleted);

       @Query("""
               SELECT hc FROM HistoricalContext hc
               WHERE hc.school IS NULL
                 AND hc.deletedAt IS NOT NULL
               ORDER BY hc.createdAt DESC
               """)
       List<HistoricalContext> findAllDeleted();

       @Modifying
       @Query("""
               UPDATE HistoricalContext hc
               SET hc.deletedAt = NULL
               WHERE hc.contextId = :contextId
                 AND hc.deletedAt IS NOT NULL
               """)
       int restoreById(@Param("contextId") UUID contextId);

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.school IS NULL")
       long countCurrent();

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.school IS NULL AND hc.isPublished = true")
       long countPublished();

       @Query("SELECT COUNT(hc) FROM HistoricalContext hc WHERE hc.school IS NULL AND hc.deletedAt IS NULL AND hc.isPublished = true")
       long countActive();
}
