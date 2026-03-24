package com.historyTalk.repository;

import com.historyTalk.entity.chat.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m WHERE m.chatSession.sessionId = :sessionId AND (:includeDeleted = true OR m.deletedAt IS NULL) ORDER BY m.timestamp ASC")
    List<Message> findByChatSessionSessionIdOrderByTimestampAsc(@Param("sessionId") UUID sessionId,
                                                                @Param("includeDeleted") boolean includeDeleted);
}
