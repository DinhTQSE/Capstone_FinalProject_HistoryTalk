package com.historytalk.repository;

import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.entityId = :entityId
              AND d.entityType = :entityType
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findByEntityIdAndEntityTypeOrderByUploadDateDesc(
            @Param("entityId") UUID entityId,
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.entityId IN :entityIds
              AND d.entityType = :entityType
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findByEntityIdInAndEntityTypeOrderByUploadDateDesc(
            @Param("entityIds") Collection<UUID> entityIds,
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.createdBy.uid = :uid
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findByUploadedByUidOrderByUploadDateDesc(
            @Param("uid") UUID uid,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.createdBy.uid = :uid
              AND d.entityType = :entityType
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findByUploadedByUidAndEntityTypeOrderByUploadDateDesc(
            @Param("uid") UUID uid,
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.entityType = :entityType
              AND (:search IS NULL OR :search = ''
                   OR d.title ILIKE CONCAT('%', :search, '%')
                   OR d.content ILIKE CONCAT('%', :search, '%'))
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> search(
            @Param("search") String search,
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
            LEFT JOIN FETCH d.createdBy
            WHERE d.entityType = :entityType
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findAllActive(
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("SELECT COUNT(d) FROM Document d")
    long countCurrent();

    @Query("SELECT COUNT(d) FROM Document d WHERE d.deletedAt IS NULL")
    long countActive();

    List<Document> findByEntityIdAndEntityType(UUID entityId, EntityType entityType);

    /**
     * Live documents with text but no vector chunk, i.e. the AI indexing call failed or the AI service was down.
     * Documents touched after {@code settledBefore} are skipped so in-flight async indexing is not duplicated.
     */
    @Query(value = """
            SELECT d.* FROM document d
            WHERE d.deleted_at IS NULL
              AND d.content IS NOT NULL AND btrim(d.content) <> ''
              AND COALESCE(d.updated_at, d.uploaded_at) < :settledBefore
              AND NOT EXISTS (SELECT 1 FROM vector_chunk vc
                              WHERE vc.doc_id = d.doc_id AND vc.deleted_at IS NULL)
            ORDER BY COALESCE(d.updated_at, d.uploaded_at) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Document> findUnindexedDocuments(
            @Param("settledBefore") java.time.LocalDateTime settledBefore,
            @Param("limit") int limit);
}
