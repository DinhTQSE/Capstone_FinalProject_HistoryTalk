package com.historytalk.entity.historicalContext;

import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.user.User;
import com.historytalk.entity.character.Character;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

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
public class HistoricalContext {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "context_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID contextId;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

//    @Lob
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
    @Column(name = "is_bc", columnDefinition = "boolean not null default false")
    private Boolean isBC = false;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Builder.Default
    @Column(name = "is_published", nullable = false)
    private Boolean isPublished = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", nullable = true)
    private LocalDateTime deletedAt;

    @Builder.Default
    @ManyToMany(mappedBy = "historicalContexts", fetch = FetchType.LAZY)
    private Set<Character> characters = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "historicalContext", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizzes = new ArrayList<>();

    public Boolean getIsDraft() {
        return !Boolean.TRUE.equals(isPublished);
    }

    public void setIsDraft(Boolean isDraft) {
        if (isDraft != null) {
            this.isPublished = !Boolean.TRUE.equals(isDraft);
        }
    }

    public LocalDateTime getCreatedDate() {
        return createdAt;
    }

    public void setCreatedDate(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedDate() {
        return updatedAt;
    }

    public void setUpdatedDate(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
