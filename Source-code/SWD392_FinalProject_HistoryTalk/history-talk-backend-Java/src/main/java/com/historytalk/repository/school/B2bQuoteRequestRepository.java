package com.historytalk.repository.school;

import com.historytalk.entity.enums.B2bQuoteStatus;
import com.historytalk.entity.school.B2bQuoteRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bQuoteRequestRepository extends JpaRepository<B2bQuoteRequest, UUID> {

    Optional<B2bQuoteRequest> findByQuoteCodeIgnoreCase(String quoteCode);

    boolean existsByQuoteCodeIgnoreCase(String quoteCode);

    @Query("""
            SELECT q FROM B2bQuoteRequest q
            WHERE (:status IS NULL OR q.status = :status)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(q.schoolName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(q.contactEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(q.quoteCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(q.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(q.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<B2bQuoteRequest> searchQuotes(
            @Param("status") B2bQuoteStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
