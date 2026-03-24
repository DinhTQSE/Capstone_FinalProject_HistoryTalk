package com.historyTalk.repository;

import com.historyTalk.entity.historicalContext.HistoricalContextDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HistoricalContextDocumentRepository extends JpaRepository<HistoricalContextDocument, UUID> {

        @Query("""
            SELECT d FROM HistoricalContextDocument d
            WHERE d.historicalContext.contextId = :contextId
            AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadDate DESC
            """)
        List<HistoricalContextDocument> findByHistoricalContextContextIdOrderByUploadDateDesc(@Param("contextId") UUID contextId,
                                                    @Param("includeDeleted") boolean includeDeleted);

        @Query("""
            SELECT d FROM HistoricalContextDocument d
            WHERE d.createdBy.uid = :uid
            AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadDate DESC
            """)
        List<HistoricalContextDocument> findByCreatedByUidOrderByUploadDateDesc(@Param("uid") UUID uid,
                                             @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT hcd FROM HistoricalContextDocument hcd
            WHERE (:search IS NULL OR :search = '' OR
                   hcd.title ILIKE CONCAT('%', :search, '%') OR
                   hcd.content ILIKE CONCAT('%', :search, '%'))
            AND (:includeDeleted = true OR hcd.deletedAt IS NULL)
            ORDER BY hcd.uploadDate DESC
            """)
    List<HistoricalContextDocument> search(@Param("search") String search,
                                            @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT d FROM HistoricalContextDocument d WHERE (:includeDeleted = true OR d.deletedAt IS NULL) ORDER BY d.uploadDate DESC")
    List<HistoricalContextDocument> findAllActive(@Param("includeDeleted") boolean includeDeleted);
}
