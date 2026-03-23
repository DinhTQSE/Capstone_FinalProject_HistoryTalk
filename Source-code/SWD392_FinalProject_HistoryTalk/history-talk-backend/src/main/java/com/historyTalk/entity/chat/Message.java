package com.historyTalk.entity.chat;

import com.historyTalk.entity.enums.MessageRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE message SET deleted_at = NOW() WHERE message_id=?")
@Where(clause = "deleted_at IS NULL")
public class Message {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "message_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID messageId;

//    @Lob
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "is_from_ai", nullable = false)
    private Boolean isFromAi;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private MessageRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "suggested_questions", columnDefinition = "TEXT")
    private String suggestedQuestions;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    void ensureDefaults() {
        if (this.isFromAi == null) {
            this.isFromAi = Boolean.FALSE;
        }
        if (this.role == null) {
            this.role = Boolean.TRUE.equals(this.isFromAi) ? MessageRole.ASSISTANT : MessageRole.USER;
        }
    }
}
