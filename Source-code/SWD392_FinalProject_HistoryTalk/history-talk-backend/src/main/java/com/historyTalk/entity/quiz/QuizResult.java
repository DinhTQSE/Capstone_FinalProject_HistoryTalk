package com.historyTalk.entity.quiz;

import com.historyTalk.entity.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizResult {

    @Id
    @Column(name = "result_id", length = 50)
    private String resultId;

    @Column(name = "score", nullable = false)
    private Integer score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @CreationTimestamp
    @Column(name = "taken_date", nullable = false, updatable = false)
    private LocalDateTime takenDate;

    @Builder.Default
    @OneToMany(mappedBy = "quizResult", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizAnswerDetail> answerDetails = new ArrayList<>();

    @PrePersist
    void ensureId() {
        if (this.resultId == null) {
            this.resultId = UUID.randomUUID().toString();
        }
    }
}
