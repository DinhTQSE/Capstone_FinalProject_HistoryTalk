package com.historytalk.entity.assignment;

import com.historytalk.entity.enums.SubmissionStatus;
import com.historytalk.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment_submission", schema = "historical_schema",
       uniqueConstraints = {@UniqueConstraint(name = "uq_assignment_student", columnNames = {"assignment_id", "student_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentSubmission {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", length = 20, nullable = false)
    private SubmissionStatus status = SubmissionStatus.NOT_STARTED;

    @Column(name = "score", precision = 4, scale = 2)
    private BigDecimal score;

    @Builder.Default
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Builder.Default
    @Column(name = "reading_duration_seconds", nullable = false)
    private Integer readingDurationSeconds = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_session_id")
    private com.historytalk.entity.quiz.QuizSession quizSession;

    @Column(name = "chat_session_id")
    private UUID chatSessionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
