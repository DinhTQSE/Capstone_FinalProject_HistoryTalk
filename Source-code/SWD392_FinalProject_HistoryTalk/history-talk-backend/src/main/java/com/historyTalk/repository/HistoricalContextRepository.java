package com.historyTalk.repository;

import com.historyTalk.entity.historicalContext.HistoricalContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

       Page<HistoricalContext> findByStaffStaffId(UUID staffId, Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE (:search IS NULL OR :search = ''
                            OR hc.name ILIKE CONCAT('%', :search, '%')
                            OR hc.description ILIKE CONCAT('%', :search, '%'))
                     """)
       Page<HistoricalContext> findAllWithSearch(@Param("search") String search, Pageable pageable);

       @Query("""
                     SELECT hc FROM HistoricalContext hc
                     WHERE (:search IS NULL OR :search = ''
                            OR hc.name ILIKE CONCAT('%', :search, '%')
                            OR hc.description ILIKE CONCAT('%', :search, '%'))
                     ORDER BY hc.createdDate DESC
                     """)
       List<HistoricalContext> findAllSimple(@Param("search") String search);
}
