package com.historytalk.entity.school;

import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "token_boost_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenBoostRequest {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_id")
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_uid")
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_uid")
    private User requester;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "requester_role", length = 30, nullable = false)
    private UserRole requesterRole = UserRole.SCHOOL_STUDENT;

    @Column(name = "requested_tokens", nullable = false)
    private Integer requestedTokens;

    @Column(name = "approved_tokens")
    private Integer approvedTokens;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private TokenBoostRequestStatus status = TokenBoostRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_reviewed_by_uid")
    private User teacherReviewedBy;

    @Column(name = "teacher_review_note", length = 500)
    private String teacherReviewNote;

    @Column(name = "teacher_reviewed_at")
    private LocalDateTime teacherReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_reviewed_by_uid")
    private User adminReviewedBy;

    @Column(name = "admin_review_note", length = 500)
    private String adminReviewNote;

    @Column(name = "admin_reviewed_at")
    private LocalDateTime adminReviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_uid")
    private User reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Builder.Default
    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate = LocalDate.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
