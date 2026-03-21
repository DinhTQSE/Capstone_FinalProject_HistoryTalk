package com.historyTalk.entity.historicalContext;

import com.historyTalk.entity.enums.EventCategory;
import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.entity.quiz.Quiz;
import com.historyTalk.entity.user.User;
import com.historyTalk.entity.character.Character;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "historical_context")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE historical_context SET deleted_at = NOW() WHERE context_id=?")
@Where(clause = "deleted_at IS NULL")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "era", length = 50)
    private EventEra era;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private EventCategory category;

    @Column(name = "year")
    private Integer year;

    @Column(name = "start_year")
    private Integer startYear;

    @Column(name = "end_year")
    private Integer endYear;

    @Builder.Default
    @Column(name = "before_tcn", columnDefinition = "boolean not null default false")
    private Boolean beforeTCN = false;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistoricalContextDocument> documents = new ArrayList<>();

    @Builder.Default
    @ManyToMany(mappedBy = "historicalContexts", fetch = FetchType.LAZY)
    private Set<Character> characters = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizzes = new ArrayList<>();

}
