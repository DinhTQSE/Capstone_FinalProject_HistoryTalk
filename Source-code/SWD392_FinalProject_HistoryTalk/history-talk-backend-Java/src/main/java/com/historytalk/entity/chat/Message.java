package com.historytalk.entity.chat;


import com.historytalk.entity.enums.ExamVerdict;
import jakarta.persistence.*;
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
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession chatSession;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "suggested_questions", columnDefinition = "TEXT")
    private String suggestedQuestions;

    @Column(name = "quotes", columnDefinition = "TEXT")
    private String quotes;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Column(name = "token")
    private Integer token;

    @Column(name = "message_type", length = 50)
    private String messageType;

    /** The question this message asks, in an exam session. */
    @Column(name = "exam_question", columnDefinition = "TEXT")
    private String examQuestion;

    /** Answer key of {@link #examQuestion}. Never return this to clients. */
    @Column(name = "exam_expected_answer", columnDefinition = "TEXT")
    private String examExpectedAnswer;

    /** Source excerpt of {@link #examQuestion}. Never return this to clients. */
    @Column(name = "exam_source", columnDefinition = "TEXT")
    private String examSource;

    /** Grade of the learner's answer to the previous question. */
    @Enumerated(EnumType.STRING)
    @Column(name = "exam_verdict", length = 20)
    private ExamVerdict examVerdict;

    @PrePersist
    void ensureDefaults() {
        if (this.isFromAi == null) {
            this.isFromAi = Boolean.FALSE;
        }
    }
}
