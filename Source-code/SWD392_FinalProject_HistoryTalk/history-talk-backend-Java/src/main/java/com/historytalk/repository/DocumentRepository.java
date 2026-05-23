package com.historytalk.repository;

import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("""
            SELECT d FROM Document d
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
            WHERE d.createdBy.uid = :uid
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findByUploadedByUidOrderByUploadDateDesc(
            @Param("uid") UUID uid,
            @Param("includeDeleted") boolean includeDeleted);

    @Query("""
            SELECT d FROM Document d
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
            WHERE d.entityType = :entityType
              AND (:includeDeleted = true OR d.deletedAt IS NULL)
            ORDER BY d.uploadedAt DESC
            """)
    List<Document> findAllActive(
            @Param("entityType") EntityType entityType,
            @Param("includeDeleted") boolean includeDeleted);
}
