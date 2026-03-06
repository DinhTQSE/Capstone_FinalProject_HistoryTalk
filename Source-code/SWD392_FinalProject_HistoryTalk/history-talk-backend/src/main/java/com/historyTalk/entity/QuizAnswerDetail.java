package com.historyTalk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column(name = "detail_id", length = 50)
    private String detailId;

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
    void ensureId() {
        if (this.detailId == null) {
            this.detailId = UUID.randomUUID().toString();
        }
        if (this.isCorrect == null) {
            this.isCorrect = Boolean.FALSE;
        }
    }
}
