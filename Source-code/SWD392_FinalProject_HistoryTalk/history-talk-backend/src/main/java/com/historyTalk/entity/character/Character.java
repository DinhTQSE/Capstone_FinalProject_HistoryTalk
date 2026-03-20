package com.historyTalk.entity.character;

import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "\"character\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "character_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID characterId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "title", length = 150)
    private String title;

    @Lob
    @Column(name = "background", columnDefinition = "TEXT", nullable = false)
    private String background;

    @Column(name = "image", length = 255)
    private String image;

    @Column(name = "personality", length = 500)
    private String personality;

    @Column(name = "lifespan", length = 50)
    private String lifespan;

    @Column(name = "side", length = 100)
    private String side;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "character_historical_context",
            joinColumns = @JoinColumn(name = "character_id"),
            inverseJoinColumns = @JoinColumn(name = "context_id")
    )
    private Set<HistoricalContext> historicalContexts = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Builder.Default
    @OneToMany(mappedBy = "character", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterDocument> documents = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "character", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatSession> chatSessions = new ArrayList<>();

}
