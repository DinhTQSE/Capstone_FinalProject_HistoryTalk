package com.historyTalk.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @Column(name = "staff_id", length = 50)
    private String staffId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "email", length = 100, nullable = false)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    @Builder.Default
    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<HistoricalContext> historicalContexts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<HistoricalContextDocument> contextDocuments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<Character> characters = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<CharacterDocument> characterDocuments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "staff", fetch = FetchType.LAZY)
    private List<Quiz> quizzes = new ArrayList<>();

    @PrePersist
    void ensureId() {
        if (this.staffId == null) {
            this.staffId = UUID.randomUUID().toString();
        }
    }
}
