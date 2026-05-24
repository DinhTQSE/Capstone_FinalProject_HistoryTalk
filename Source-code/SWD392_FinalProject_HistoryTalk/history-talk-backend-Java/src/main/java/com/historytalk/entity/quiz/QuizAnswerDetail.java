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
import java.util.UUID;

@Entity
@Table(name = "quiz_answer_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAnswerDetail {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "detail_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID answerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // Lưu ý: Trên sơ đồ ghi là 'seassion_id' (có vẻ bị typo), ở đây dùng 'session_id' cho chuẩn xác
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuizSession quizSession;

    @Column(name = "selected_option", nullable = false)
    private Integer selectedOption;

    @Builder.Default
    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

//    @UpdateTimestamp
//    @Column(name = "updated_at")
//    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

}