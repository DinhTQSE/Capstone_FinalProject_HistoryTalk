package com.historyTalk.entity.quiz;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

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

    @PrePersist
    void ensureDefaults() {
        if (this.isCorrect == null) {
            this.isCorrect = Boolean.FALSE;
        }
    }
}
