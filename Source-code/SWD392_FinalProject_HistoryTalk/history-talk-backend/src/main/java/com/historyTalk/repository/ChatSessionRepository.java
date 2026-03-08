package com.historyTalk.repository;

import com.historyTalk.entity.chat.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    @Query("""
            SELECT cs FROM ChatSession cs
            JOIN FETCH cs.character c
            JOIN FETCH c.historicalContext hc
            WHERE cs.user.uid = :userId
              AND c.characterId = :characterId
              AND hc.contextId = :contextId
            ORDER BY cs.lastMessageAt DESC NULLS LAST, cs.createDate DESC
            """)
    List<ChatSession> findByUserAndCharacterAndContext(
            @Param("userId") UUID userId,
            @Param("characterId") UUID characterId,
            @Param("contextId") UUID contextId);

    Optional<ChatSession> findBySessionIdAndUserUid(UUID sessionId, UUID userId);
}
