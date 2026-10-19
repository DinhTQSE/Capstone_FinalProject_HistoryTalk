package com.historytalk.entity.localcontent;

import com.historytalk.entity.enums.EntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Join entity for class_local_content table.
 * Represents the M2M relationship between a classroom and a local content item
 * (either a local HistoricalContext or a local Character).
 */
@Entity
@Table(name = "class_local_content")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassLocalContent {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** ID of the classroom this content is assigned to. */
    @Column(name = "class_id", columnDefinition = "uuid", nullable = false)
    private UUID classId;

    /** contextId or characterId of the local content. */
    @Column(name = "entity_id", columnDefinition = "uuid", nullable = false)
    private UUID entityId;

    /** CONTEXT or CHARACTER. */
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20, nullable = false)
    private EntityType entityType;

    /** Teacher who assigned this content to the classroom. */
    @Column(name = "assigned_by", columnDefinition = "uuid", nullable = true)
    private UUID assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;
}
