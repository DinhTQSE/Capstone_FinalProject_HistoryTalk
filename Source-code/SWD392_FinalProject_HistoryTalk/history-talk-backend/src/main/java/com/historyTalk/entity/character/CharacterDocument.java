package com.historyTalk.entity.character;

import com.historyTalk.entity.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "character_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "doc_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID docId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

//    @Lob
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "upload_date", nullable = false, updatable = false)
    private LocalDateTime uploadDate;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

}
