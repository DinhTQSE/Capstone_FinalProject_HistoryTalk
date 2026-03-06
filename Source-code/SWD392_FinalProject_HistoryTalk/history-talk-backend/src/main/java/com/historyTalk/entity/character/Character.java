package com.historyTalk.entity.character;

import com.historyTalk.entity.chat.ChatSession;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.staff.Staff;
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
@Table(name = "\"character\"")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Character {

    @Id
    @Column(name = "character_id", length = 50)
    private String characterId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Lob
    @Column(name = "background", columnDefinition = "TEXT", nullable = false)
    private String background;

    @Column(name = "image", length = 255)
    private String image;

    @Column(name = "personality", length = 500)
    private String personality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_id", nullable = false)
    private HistoricalContext historicalContext;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Builder.Default
    @OneToMany(mappedBy = "character", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CharacterDocument> documents = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "character", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatSession> chatSessions = new ArrayList<>();

    @PrePersist
    void ensureId() {
        if (this.characterId == null) {
            this.characterId = UUID.randomUUID().toString();
        }
    }
}
