package com.historyTalk.entity.quiz;

import com.historyTalk.entity.user.User;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.enums.EventEra;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quiz")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE quiz SET deleted_at = NOW() WHERE quiz_id=?")
@Where(clause = "deleted_at IS NULL")
public class Quiz {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "quiz_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID quizId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

//    @Lob
    @Column(name = "description", columnDefinition = "TEXT", nullable = true)
    private String description;

    @Column(name = "grade", nullable = true)
    private Integer grade;

    @Column(name = "chapter_number", nullable = true)
    private Integer chapterNumber;

    @Column(name = "chapter_title", length = 255, nullable = true)
    private String chapterTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "era", nullable = true)
    private EventEra era;

    @Column(name = "duration_seconds", nullable = true)
    private Integer durationSeconds;

    @Builder.Default
    @Column(name = "play_count", nullable = false)
    private Integer playCount = 0;

    @Builder.Default
    @Column(name = "rating", nullable = false)
    private Double rating = 0.0;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private HistoricalContext historicalContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "quiz", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Question> questions = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "quiz", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<QuizResult> quizResults = new ArrayList<>();

}
