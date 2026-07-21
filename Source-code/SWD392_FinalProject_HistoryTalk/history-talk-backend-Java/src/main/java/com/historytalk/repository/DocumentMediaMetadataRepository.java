package com.historytalk.repository;

import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentMediaMetadataRepository extends JpaRepository<DocumentMediaMetadata, UUID> {
    // Legacy method for backward compatibility (optional)
    Optional<DocumentMediaMetadata> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
    
    // Polymorphic query methods
    Optional<DocumentMediaMetadata> findByEntityTypeAndEntityId(EntityType entityType, UUID entityId);
    List<DocumentMediaMetadata> findAllByEntityTypeAndEntityId(EntityType entityType, UUID entityId);
    void deleteByEntityTypeAndEntityId(EntityType entityType, UUID entityId);
}
