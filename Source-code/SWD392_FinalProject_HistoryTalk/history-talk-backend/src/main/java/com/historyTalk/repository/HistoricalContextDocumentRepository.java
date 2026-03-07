package com.historyTalk.repository;

import com.historyTalk.entity.historicalContext.HistoricalContextDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HistoricalContextDocumentRepository extends JpaRepository<HistoricalContextDocument, UUID> {

    List<HistoricalContextDocument> findAllByOrderByUploadDateDesc();

    List<HistoricalContextDocument> findByHistoricalContextContextIdOrderByUploadDateDesc(UUID contextId);

    List<HistoricalContextDocument> findByCreatedByUidOrderByUploadDateDesc(UUID uid);

    @Query("""
            SELECT hcd FROM HistoricalContextDocument hcd
            WHERE (:search IS NULL OR :search = '' OR
                   hcd.title ILIKE CONCAT('%', :search, '%') OR
                   hcd.content ILIKE CONCAT('%', :search, '%'))
            ORDER BY hcd.uploadDate DESC
            """)
    List<HistoricalContextDocument> search(@Param("search") String search);
}
