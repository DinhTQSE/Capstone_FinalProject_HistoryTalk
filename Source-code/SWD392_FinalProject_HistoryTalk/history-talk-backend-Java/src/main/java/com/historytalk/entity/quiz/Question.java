package com.historytalk.entity.quiz;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Question {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "question_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID questionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    //    @Lob
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    //    @Lob
    @Column(name = "options", columnDefinition = "TEXT", nullable = true)
    private String options;

    @Column(name = "correct_answer", nullable = true)
    private Integer correctAnswer;

    //    @Lob
    @Column(name = "explanation", columnDefinition = "TEXT", nullable = true)
    private String explanation;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizAnswerDetail> answerDetails = new ArrayList<>();

}