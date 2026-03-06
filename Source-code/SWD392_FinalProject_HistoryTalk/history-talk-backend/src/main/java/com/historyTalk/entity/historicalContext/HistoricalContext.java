package com.historyTalk.entity.historicalContext;

import com.historyTalk.entity.quiz.Quiz;
import com.historyTalk.entity.staff.Staff;
import com.historyTalk.entity.character.Character;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "historical_context")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalContext {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "context_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID contextId;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Lob
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoricalContextDocument> documents = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Character> characters = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizzes = new ArrayList<>();

}
