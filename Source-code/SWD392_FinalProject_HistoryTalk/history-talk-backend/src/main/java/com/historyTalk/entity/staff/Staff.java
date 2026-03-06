package com.historyTalk.entity.staff;

import com.historyTalk.entity.Role;
import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.character.CharacterDocument;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.historicalContext.HistoricalContextDocument;
import com.historyTalk.entity.quiz.Quiz;
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
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "staff_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID staffId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "email", length = 100, nullable = false, unique = true)
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

}
