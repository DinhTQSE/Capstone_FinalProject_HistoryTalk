package com.historyTalk.entity.character;

import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.user.User;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private HistoricalContext historicalContext;

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
