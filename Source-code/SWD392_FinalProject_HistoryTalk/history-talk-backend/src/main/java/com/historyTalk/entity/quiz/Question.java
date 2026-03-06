package com.historyTalk.entity.quiz;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

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

    @Lob
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Lob
    @Column(name = "options", columnDefinition = "TEXT", nullable = false)
    private String options;

    @Column(name = "correct_answer", length = 255, nullable = false)
    private String correctAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Builder.Default
    @OneToMany(mappedBy = "question", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizAnswerDetail> answerDetails = new ArrayList<>();

}
