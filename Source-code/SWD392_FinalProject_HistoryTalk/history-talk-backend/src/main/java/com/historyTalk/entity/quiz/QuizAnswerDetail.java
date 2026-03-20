package com.historyTalk.entity.quiz;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

import java.util.UUID;

@Entity
@Table(name = "quiz_answer_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE quiz_answer_detail SET deleted_at = NOW() WHERE detail_id=?")
@Where(clause = "deleted_at IS NULL")
public class QuizAnswerDetail {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "detail_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID detailId;

    @Column(name = "selected_option", length = 255, nullable = false)
    private String selectedOption;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private QuizResult quizResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @PrePersist
    void ensureDefaults() {
        if (this.isCorrect == null) {
            this.isCorrect = Boolean.FALSE;
        }
    }
}
