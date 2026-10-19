package com.historytalk.repository;

import com.historytalk.entity.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for class_local_content join table.
 * Manages M2M relationships between classrooms and local content (context/character).
 */
@Repository
public interface ClassLocalContentRepository extends JpaRepository<com.historytalk.entity.localcontent.ClassLocalContent, UUID> {

    List<com.historytalk.entity.localcontent.ClassLocalContent> findByEntityIdAndEntityType(UUID entityId, EntityType entityType);

    List<com.historytalk.entity.localcontent.ClassLocalContent> findByClassIdIn(List<UUID> classIds);

    @Modifying
    @Query("DELETE FROM ClassLocalContent c WHERE c.entityId = :entityId AND c.entityType = :entityType")
    void deleteByEntityIdAndEntityType(@Param("entityId") UUID entityId, @Param("entityType") EntityType entityType);

    @Query("SELECT c.classId FROM ClassLocalContent c WHERE c.entityId = :entityId AND c.entityType = :entityType")
    List<UUID> findClassIdsByEntityIdAndEntityType(@Param("entityId") UUID entityId, @Param("entityType") EntityType entityType);
}
