package com.historytalk.entity.document;

import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "doc_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID docId;

    @Column(name = "entity_id", columnDefinition = "uuid", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 50, nullable = false)
    private EntityType entityType;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50, nullable = false)
    @Builder.Default
    private DocumentType documentType = DocumentType.TEXT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public LocalDateTime getUploadDate() {
        return uploadedAt;
    }

    public void setUploadDate(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedAt;
    }

    public void setUpdatedDate(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
